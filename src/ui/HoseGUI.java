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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class HoseGUI extends Application {
    private static String extractQuoted(String s) {
        if (s == null) return "";
        int q1 = s.indexOf('"'); if (q1 < 0) return "";
        int q2 = s.indexOf('"', q1 + 1); if (q2 < 0) return "";
        return s.substring(q1 + 1, q2);
    }

    private volatile boolean suppressPoll = false;
    private boolean armed = false;

    private DeviceManager dm;
    private DeviceLink hoseCtrl;

    private boolean attached = false;
    private ImageView image;
    private Label status;

    private javafx.animation.Timeline fuelTicker;
    private int fillIndex = 0;

    private final List<Image> connectedImages = new ArrayList<>();
    private final List<Image> disconnectedImages = new ArrayList<>();
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

        loadImages();

        image = new ImageView(disconnectedImages.get(0));
        image.setFitWidth(240);
        image.setPreserveRatio(true);
        image.setCursor(Cursor.HAND);

        status = new Label("Hose: DETACHED");
        status.setStyle("-fx-font-size: 16px;");

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
            hoseCtrl.request("HOSECTRL|SET|MAIN|" + (next ? "1" : "0"), Duration.ofSeconds(1));
        } catch (Exception ignored) {}

        if (next && !armed) initializeNewTank();

        attached = next;
        status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
        changeImage(fillIndex);

        if (armed && attached && !tankFull) startFuelTickerIfNeeded();
        else stopFuelTicker();

        new Thread(() -> {
            try {
                Thread.sleep(250);
                String r = hoseCtrl.request("HOSECTRL|GET|MAIN|None", Duration.ofSeconds(1));
                String payload = extractQuoted(r);
                boolean sAttached = parseAttached(payload);
                boolean sArmed    = parseArmed(payload);

                attached = sAttached;
                armed    = sArmed;

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
        try {
            hoseCtrl.request("HOSECTRL|SET|MAIN|" + (isAttached ? "1" : "0"), Duration.ofSeconds(1));
        } catch (Exception ex) {
            Platform.runLater(() -> status.setText("ERROR sending HOSECTRL SET: " + ex.getMessage()));
        }
    }

    private void resetForNewSession() {
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

                String r = hoseCtrl.request("HOSECTRL|GET|MAIN|None", Duration.ofSeconds(1));
                String payload = extractQuoted(r);

                boolean sAttached = parseAttached(payload);
                boolean sArmed    = parseArmed(payload);

                boolean attachChanged = (sAttached != attached);
                boolean armedChanged  = (sArmed != armed);

                attached = sAttached;
                armed    = sArmed;

                if (armedChanged && !armed) resetForNewSession();
                if (attachChanged && attached && !armed) initializeNewTank();

                Platform.runLater(() -> {
                    status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
                    changeImage(fillIndex);
                });

                if (armed && attached && !tankFull) startFuelTickerIfNeeded();
                else stopFuelTicker();

                Thread.sleep(200);
            }
        } catch (Exception ignored) {}
    }

    private void startFuelTickerIfNeeded() {
        if (fuelTicker != null) {
            if (armed && attached && !tankFull) fuelTicker.play();
            return;
        }
        fuelTicker = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                    if (!armed || !attached || tankFull) { fuelTicker.pause(); return; }
                    if (fillIndex < 10) { fillIndex += 1; changeImage(fillIndex); }
                    if (fillIndex >= 10) {
                        tankFull = true;
                        try { hoseCtrl.request("HOSECTRL|FULL|MAIN|1", Duration.ofSeconds(1)); } catch (Exception ignored) {}
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
            String dn = "GN-D-" + i + ".png";
            Image imgD = new Image(Objects.requireNonNull(HoseGUI.class.getResource("/images/hose/" + dn)).toExternalForm());
            disconnectedImages.add(imgD);
        }
        for (int i = 0; i <= 10; i++) {
            String cn = "GN-C-" + i + ".png";
            Image imgC = new Image(Objects.requireNonNull(HoseGUI.class.getResource("/images/hose/" + cn)).toExternalForm());
            connectedImages.add(imgC);
        }
    }

    private void initializeNewTank() {
        Random rand = new Random();
        tankFull = false;
        tankSize = 10 + rand.nextDouble() * 20;
        currentTankFill = tankSize * (rand.nextDouble() * rand.nextDouble() * rand.nextDouble());
        double percentFull = currentTankFill / tankSize;
        fillIndex = Math.max(0, Math.min(10, (int) Math.floor(percentFull * 11)));
        changeImage(fillIndex);
        try {
            hoseCtrl.request("HOSECTRL|SETCAP|MAIN|" + String.format(java.util.Locale.US, "%.3f", tankSize), Duration.ofSeconds(1));
            hoseCtrl.request("HOSECTRL|SETCUR|MAIN|" + String.format(java.util.Locale.US, "%.3f", currentTankFill), Duration.ofSeconds(1));
        } catch (Exception ignored) {}
    }

    private void changeImage(int number) {
        if (tankFull) return;
        int idx = Math.max(0, Math.min(10, number));
        fillIndex = idx;
        image.setImage((attached ? connectedImages : disconnectedImages).get(idx));
    }

    private static boolean parseArmed(String payload) {
        int i = payload.indexOf("ARMED:");
        return i >= 0 && i + 6 < payload.length() && payload.charAt(i + 6) == '1';
    }
    private static boolean parseAttached(String payload) {
        int i = payload.indexOf("STATE:");
        return i >= 0 && i + 6 < payload.length() && payload.charAt(i + 6) == '1';
    }

    public static void main(String[] args) { launch(args); }
}

