package ui;

import io.bus.DeviceLink;
import io.bus.DeviceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class HoseGUI extends Application {

    // ---- helpers to mirror Main's parsing ----------------------------------
    private static String extractQuoted(String s) {
        if (s == null) return "";
        int q1 = s.indexOf('"'); if (q1 < 0) return "";
        int q2 = s.indexOf('"', q1 + 1); if (q2 < 0) return "";
        return s.substring(q1 + 1, q2);
    }
    private static String afterColon(String s) {
        if (s == null) return "";
        int i = s.indexOf(':');
        return (i >= 0 && i + 1 < s.length()) ? s.substring(i + 1) : "";
    }

    // ---- simple shared flag (optional: read by Main if needed) -------------
    private static volatile boolean ATTACHED_FLAG = false;
    private static void publishAttached(boolean v) { ATTACHED_FLAG = v; }

    private volatile boolean suppressPoll = false;
    private boolean armed = false;

    // ---- bus/link -----------------------------------------------------------
    private DeviceManager dm;
    private DeviceLink hoseCtrl;

    // ---- GUI state ----------------------------------------------------------
    private boolean attached = false;
    private ImageView image;
    private Label status;

    private javafx.animation.Timeline fuelTicker;
    private int fillIndex = 0;

    private List<Image> connectedImages;
    private List<Image> disconnectedImages;
    private boolean firstTimeTankConnection;
    private boolean tankFull;
    private double tankSize;
    private double currentTankFill;

    @Override
    public void start(Stage stage) throws Exception {

        var entries = List.of(
                new DeviceManager.Entry("hose-ctrl", "127.0.0.1", 5121, "hose-ctrl", "hosectrl")
        );
        dm = new DeviceManager(entries);
        hoseCtrl = dm.link("hose-ctrl");

        firstTimeTankConnection = true;

        connectedImages = new ArrayList<>();
        disconnectedImages = new ArrayList<>();
        loadImages();

        image = new ImageView(disconnectedImages.get(0));
        image.setFitWidth(240);
        image.setPreserveRatio(true);
        image.setCursor(Cursor.HAND);

        status = new Label("Hose: DETACHED");
        status.setStyle("-fx-font-size: 16px;");

        publishAttached(attached);

        image.setOnMouseClicked(e -> toggle());

        setSimState(attached);

        Thread poll = new Thread(this::pollLoop, "hose-poll");
        poll.setDaemon(true);
        poll.start();

        VBox root = new VBox(12, image, status);
        root.setPadding(new Insets(12));
        stage.setTitle("Hose Simulator");
        stage.setScene(new Scene(root, 280, 320));
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
    }

    private void toggle() {
        boolean next = !attached;
        suppressPoll = true;

        try {
            hoseCtrl.request("HOSECTRL|SET|MAIN|" + (next ? "1" : "0"), java.time.Duration.ofSeconds(1));
        } catch (Exception ignored) {}

        if (next && !armed) {
            initializeNewTank();
        }

        attached = next;
        publishAttached(attached);
        status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
        changeImage(fillIndex);

        if (armed && attached && !tankFull) {
            startFuelTickerIfNeeded();
        } else {
            stopFuelTicker();
        }

        new Thread(() -> {
            try {
                Thread.sleep(250);
                String r = hoseCtrl.request("HOSECTRL|GET|MAIN|None", java.time.Duration.ofSeconds(1));
                String payload = extractQuoted(r);
                boolean sAttached = parseAttached(payload);
                boolean sArmed    = parseArmed(payload);

                attached = sAttached;
                armed    = sArmed;
                publishAttached(attached);

                Platform.runLater(() -> {
                    status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
                    changeImage(fillIndex);
                });
            } catch (Exception ignored) {
            } finally {
                suppressPoll = false;
            }
        }, "hose-debounce").start();
    }

    private void setSimState(boolean isAttached) {
        publishAttached(isAttached);
        try {
            String v = isAttached ? "1" : "0";
            hoseCtrl.request("HOSECTRL|SET|MAIN|" + v, java.time.Duration.ofSeconds(1));
        } catch (Exception ex) {
            Platform.runLater(() -> status.setText("ERROR sending HOSECTRL SET: " + ex.getMessage()));
        }
    }

    private void resetForNewSession() {
        firstTimeTankConnection = true;
        tankFull = false;
        currentTankFill = 0;
        tankSize = 0;
        fillIndex = 0;
        changeImage(fillIndex);
        stopFuelTicker();
    }

    private void pollLoop() {
        try {
            while (true) {
                if (suppressPoll) { Thread.sleep(50); continue; }

                String r = hoseCtrl.request("HOSECTRL|GET|MAIN|None", java.time.Duration.ofSeconds(1));
                String payload = extractQuoted(r);

                boolean sAttached = parseAttached(payload);
                boolean sArmed    = parseArmed(payload);

                boolean attachChanged = (sAttached != attached);
                boolean armedChanged  = (sArmed != armed);

                attached = sAttached;
                armed    = sArmed;
                publishAttached(attached);

                if (armedChanged && !armed) {
                    resetForNewSession();
                }

                if (attachChanged && attached && !armed) {
                    initializeNewTank();
                }

                Platform.runLater(() -> {
                    status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
                    changeImage(fillIndex);
                });

                if (armed && attached && !tankFull) {
                    startFuelTickerIfNeeded();
                } else {
                    stopFuelTicker();
                }

                Thread.sleep(200);
            }
        } catch (Exception ignored) {
        }
    }

    private void startFuelTickerIfNeeded() {
        if (fuelTicker != null) {

            if (armed && attached && !tankFull) fuelTicker.play();
            return;
        }
        fuelTicker = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {

                    if (!armed || !attached || tankFull) {
                        fuelTicker.pause();
                        return;
                    }
                    if (fillIndex < 10) {
                        fillIndex += 1;
                        changeImage(fillIndex);

                    }
                    if (fillIndex >= 10) {
                        tankFull = true;
                        try {
                            hoseCtrl.request("HOSECTRL|FULL|MAIN|1", java.time.Duration.ofSeconds(1));
                        } catch (Exception ignored) {}
                        fuelTicker.pause();
                    }
                })
        );
        fuelTicker.setCycleCount(javafx.animation.Animation.INDEFINITE);
        if (armed && attached && !tankFull) fuelTicker.play();
    }

    private void stopFuelTicker() {
        if (fuelTicker != null) fuelTicker.pause();
    }

    private void loadImages() {
        for (int i = 0; i <= 10; i++) {
            String imageName = "GN-D-" + i + ".png";
            Image img = new Image(Objects.requireNonNull(
                    HoseGUI.class.getResource("/images/hose/" + imageName)).toExternalForm());
            disconnectedImages.add(img);
        }
        for (int i = 0; i <= 10; i++) {
            String imageName = "GN-C-" + i + ".png";
            Image img = new Image(Objects.requireNonNull(
                    HoseGUI.class.getResource("/images/hose/" + imageName)).toExternalForm());
            connectedImages.add(img);
        }
    }

    private void initializeNewTank() {
        firstTimeTankConnection = false;
        Random rand = new Random();
        tankFull = false;
        tankSize = 10 + rand.nextDouble() * 20;
        currentTankFill = tankSize * (rand.nextDouble() * rand.nextDouble() * rand.nextDouble());
        double percentFull = currentTankFill / tankSize;
        fillIndex = Math.max(0, Math.min(10, (int) Math.floor(percentFull * 11)));
        changeImage(fillIndex);
    }

    private void changeImage(int number) {
        if (tankFull) return;
        int idx = Math.max(0, Math.min(10, number));
        fillIndex = idx;
        if (!attached) {
            image.setImage(disconnectedImages.get(idx));
        } else {
            image.setImage(connectedImages.get(idx));
        }
    }

    private static boolean parseArmed(String payload) {
        int i = payload.indexOf("ARMED:");
        return i >= 0 && i+6 < payload.length() && payload.charAt(i+6) == '1';
    }
    private static boolean parseAttached(String payload) {
        int i = payload.indexOf("STATE:");
        return i >= 0 && i+6 < payload.length() && payload.charAt(i+6) == '1';
    }

    public static void main(String[] args) { launch(args); }
}

