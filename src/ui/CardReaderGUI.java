package ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import io.bus.DeviceManager;
import io.bus.DeviceLink;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CardReaderGUI extends Application {
    private DeviceManager dm;
    private DeviceLink reader;

    @Override
    public void start(Stage stage) throws Exception {
        // connect only to the control server
        var entries = List.of(
                new DeviceManager.Entry("cardreader-ctrl", "127.0.0.1", 5221, "cardr-ctrl", "cardreader")
        );
        dm = new DeviceManager(entries);
        reader = dm.link("cardreader-ctrl");

        Label status = new Label("Tap to send a random single digit (0-9)");
        Button tap = new Button("Tap Card");

        tap.setOnAction(ev -> {
            int d = ThreadLocalRandom.current().nextInt(10);
            String ok;
            try {
                ok = reader.request("CARDREADER|DEVCTL|TAP|" + d, Duration.ofSeconds(1));
            } catch (Exception e) {
                ok = "NO REPLY";
            }
            status.setText("Queued tap: " + d + " (server replied: " + ok + ")");
        });

        VBox root = new VBox(10, status, tap);
        root.setPadding(new Insets(12));
        stage.setTitle("Card Reader GUI");
        stage.setScene(new Scene(root, 320, 140));
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

