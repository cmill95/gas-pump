package ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.time.Duration;
import io.bus.DeviceLink;
import io.bus.DeviceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Polished, stand‑alone Flow‑Meter GUI demo.
 * <p>
 * — Loads existing sprite frames in {@code resources/images/flowMeter/}.
 * — Shows a crisp gallon counter in a digital‑style font.
 * — Modern card‑style design (rounded corners, drop shadow, soft background).</p>
 */

public class FlowMeterGUI extends Application {



    private static final String RES_PATH = "/images/flowMeter/";
    private static final double SCREEN_MULTIPLIER = 0.30; // 30 % of monitor height

    // Internal mutable state

    private final List<ImageView> frames = new ArrayList<>();
    private final IntegerProperty frameIdx = new SimpleIntegerProperty(0);

    private boolean flowing = false;
    private double gallons = 0.0;

    private Text readout;
    private StackPane spritePane;

    private DeviceManager dm;
    private DeviceLink flowMeterCtrl;

    // JavaFX lifecycle

    @Override
    public void start(Stage stage) throws Exception {
        // Device connections
        var entries = List.of(
                new DeviceManager.Entry("flowmeter-ctrl", "127.0.0.1", 5621, "flowmeter-ctrl", "flowmeter")
        );
        dm = new DeviceManager(entries);
        flowMeterCtrl = dm.link("flowmeter-ctrl");

        // Load resources
        frames.add(loadFrame("FM-1-1.png"));
        frames.add(loadFrame("FM-1-2.png"));
        frames.add(loadFrame("FM-0-1.png")); // idle frame


        // Compute dimensions & scale images
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double size = screen.getHeight() * SCREEN_MULTIPLIER;
        frames.forEach(v -> {
            v.setPreserveRatio(true);
            v.setFitWidth(size);
        });


        // Build scene graph
        readout = new Text();
        readout.setFont(Font.font("Consolas", size * 0.23));
        readout.setFill(Color.web("#222"));
        updateReadout();

        spritePane = new StackPane(frames.get(2));
        spritePane.setPadding(new Insets(10));
        spritePane.setBackground(new Background(new BackgroundFill(Color.WHITE, new CornerRadii(12), Insets.EMPTY)));
        spritePane.setBorder(new Border(new BorderStroke(Color.web("#bbbbbb"), BorderStrokeStyle.SOLID,
                new CornerRadii(12), new BorderWidths(2))));
        spritePane.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.25)));

        VBox root = new VBox(18, readout, spritePane);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(25));
        root.setBackground(new Background(new BackgroundFill(Color.web("#f3f6fb"), CornerRadii.EMPTY, Insets.EMPTY)));

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Flow‑Meter");
        stage.setResizable(false);
        stage.sizeToScene();


        // Sprite animation
        Timeline anim = new Timeline(new KeyFrame(javafx.util.Duration.seconds(0.12), e -> toggleFrame()));
        anim.setCycleCount(Animation.INDEFINITE);
        anim.play();


        // DEMO‑ONLY logic
        //simulateFlow(); // <— Remove when wiring to real device

        stage.show();
        startPolling();

    }


    // Helper methods ----------------------------------------------------
    private ImageView loadFrame(String fileName) {
        return new ImageView(new Image(getClass().getResourceAsStream(RES_PATH + fileName)));
    }

    private void toggleFrame() {
        if (!flowing) return;
        frameIdx.set(frameIdx.get() ^ 1); // XOR swap 0 ⇄ 1
        spritePane.getChildren().set(0, frames.get(frameIdx.get()));
    }

    private void updateReadout() {
        readout.setText(String.format("%.2f gallons", gallons));
    }

    // Temporary stand‑alone simulation
    private void simulateFlow() {
        flowing = true;
        Timeline t = new Timeline(new KeyFrame(javafx.util.Duration.seconds(0.1), e -> {
            gallons += 0.005; // +0.005 gal every 100 ms → 3 GPM demo
            updateReadout();
        }));
        t.setCycleCount(Animation.INDEFINITE);
        t.play();
    }

    private void startPolling() {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    String r = flowMeterCtrl.request("FLOWCTRL|GETSTATE|MAIN|None", Duration.ofSeconds(1));
                    String payload = extractQuoted(r);
                    boolean flowingNow = payload.contains("STATE:1");
                    double gallonsNow = parseGallons(payload);
                    Platform.runLater(() -> updateFromDevice(gallonsNow, flowingNow));
                    Thread.sleep(300);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "flowmeter-poll");
        t.setDaemon(true);
        t.start();
    }

    private double parseGallons(String payload) {
        try {
            int i = payload.indexOf("GALLONS:");
            if (i >= 0) return Double.parseDouble(payload.substring(i + 8).trim());
        } catch (Exception ignored) {}
        return 0.0;
    }


    private static String extractQuoted(String s) {
        if (s == null) return "";
        int q1 = s.indexOf('"'); if (q1 < 0) return "";
        int q2 = s.indexOf('"', q1 + 1); if (q2 < 0) return "";
        return s.substring(q1 + 1, q2);
    }

    private void startFlowUpdates() {
        flowing = true;
        Timeline t = new Timeline(new KeyFrame(javafx.util.Duration.seconds(0.1), e -> {
            gallons += 0.005;
            updateReadout();
        }));
        t.setCycleCount(Animation.INDEFINITE);
        t.play();
    }

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
    }

    /**
     * call this whenever the real {@code FlowMeter} state changes.
     *
     * @param gallons current dispensed volume
     * @param flowing {@code true} if the pump is actively dispensing fuel
     */

    public void updateFromDevice(double gallons, boolean flowing) {
        this.gallons = gallons;
        this.flowing = flowing;
        updateReadout();
    }

    public static void main(String[] args) {
        launch(args);
    }
}