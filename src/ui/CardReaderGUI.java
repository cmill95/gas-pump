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

import javafx.util.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javafx.animation.ScaleTransition;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

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

        Image cardImage = new Image(getClass().getResourceAsStream("/images/hose_detached.png"));
        ImageView cardView = new ImageView(cardImage);
        cardView.setFitWidth(120);
        cardView.setFitHeight(120);
        cardView.setPreserveRatio(true);

        Button tap = new Button();
        tap.setGraphic(cardView);

        tap.setStyle("-fx-background-color: transparent;"); // invisible background


        tap.setOnAction(ev -> {
            int d = ThreadLocalRandom.current().nextInt(10);
            String ok;
            try {
                ok = reader.request("CARDREADER|DEVCTL|TAP|" + d, java.time.Duration.ofSeconds(1));
            } catch (Exception e) {
                ok = "NO REPLY";
            }
            status.setText("Queued tap: " + d + " (server replied: " + ok + ")");
            ScaleTransition st = new ScaleTransition(Duration.millis(150), cardView);
            st.setFromX(1.0);
            st.setFromY(1.0);
            st.setToX(0.9);
            st.setToY(0.9);
            st.setAutoReverse(true);
            st.setCycleCount(2);
            st.play();
        });

        VBox root = new VBox(15, status, tap);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        root.setStyle("-fx-background-color: #2c3e50;"); // dark blue-gray
        status.setStyle("-fx-text-fill: white; -fx-font-size: 14px;"); // make label visible

        Scene scene = new Scene(root, 400, 300);
        stage.setTitle("Card Reader GUI");
        stage.setScene(scene);
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

