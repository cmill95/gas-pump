package ui;

import devices.BinaryDevice;
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
import java.util.List;
import java.util.Objects;

public class HoseGUI extends Application {

    private static final String HOST = "127.0.0.1";
    private static final int    HOSE_PORT = 5101;
    private static final String HOSE_ID = "hose-01";
    private static final String HOSE_NAME = "hose-sensor";

    private DeviceManager dm;
    private BinaryDevice hose;

    private boolean attached = false; // start DETACHED
    private ImageView image;
    private Label status;

    @Override
    public void start(Stage stage) throws Exception {

        var entries = List.of(
                new DeviceManager.Entry(HOSE_NAME, HOST, HOSE_PORT, HOSE_ID, "binary")
        );
        dm = new DeviceManager(entries);
        hose = dm.binary(HOSE_NAME);

        Image imgDetached = new Image(
                Objects.requireNonNull(HoseGUI.class.getResource("/images/hose_detached.png"))
                        .toExternalForm());

        Image imgAttached = new Image(
                Objects.requireNonNull(HoseGUI.class.getResource("/images/hose_attached.png"))
                        .toExternalForm());

        image = new ImageView(imgDetached);
        image.setFitWidth(240);
        image.setPreserveRatio(true);
        image.setCursor(Cursor.HAND);

        status = new Label("Hose: DETACHED");
        status.setStyle("-fx-font-size: 16px;");

        image.setOnMouseClicked(e -> toggle(imgDetached, imgAttached));

        // Push initial state to sim
        setSimState(attached);

        // Optional: keep UI synced if something else toggles the sim
        Thread poll = new Thread(this::pollLoop, "hose-poll");
        poll.setDaemon(true);
        poll.start();

        VBox root = new VBox(12, image, status);
        root.setPadding(new Insets(12));
        stage.setTitle("Hose Simulator");
        stage.setScene(new Scene(root, 280, 320));
        stage.show();
    }

    private void toggle(Image imgDetached, Image imgAttached) {
        attached = !attached;
        setSimState(attached);
        image.setImage(attached ? imgAttached : imgDetached);
        status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
    }

    private void setSimState(boolean isAttached) {
        try {
            hose.set(isAttached); // sends SET 1/0 over DeviceLink → SimDevices
        } catch (Exception ex) {
            status.setText("ERROR sending state: " + ex.getMessage());
        }
    }

    private void pollLoop() {
        try {
            while (true) {
                boolean s = hose.get(); // GET → OK 0|1
                if (s != attached) {
                    attached = s;
                    Platform.runLater(() -> {
                        status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
                    });
                }
                Thread.sleep(200); // 5 Hz
            }
        } catch (Exception ignored) {
            // exit on error/close
        }
    }

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
    }

    public static void main(String[] args) { launch(args); }
}
