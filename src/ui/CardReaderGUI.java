package ui;

import io.bus.DeviceManager;
import io.bus.DeviceLink;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CardReaderGUI extends Application {
    private DeviceManager dm;
    private DeviceLink reader;

    private Image imgIdle0, imgIdle1, imgAccepted, imgDeclined;
    private ImageView cardView;
    private Label status;

    private Timeline idleFlash;
    private boolean flip;

    @Override
    public void start(Stage stage) throws Exception {
        var entries = List.of(
                new DeviceManager.Entry("cardreader-ctrl", "127.0.0.1", 5221, "cardr-ctrl", "cardreader")
        );
        dm = new DeviceManager(entries);
        reader = dm.link("cardreader-ctrl");

        imgIdle0    = new Image(getClass().getResourceAsStream("/images/cardReader/CR-0.png"));
        imgIdle1    = new Image(getClass().getResourceAsStream("/images/cardReader/CR-1.png"));
        imgAccepted = new Image(getClass().getResourceAsStream("/images/cardReader/CR-2.png"));
        imgDeclined = new Image(getClass().getResourceAsStream("/images/cardReader/CR-3.png"));

        cardView = new ImageView(imgIdle0);
        cardView.setPreserveRatio(true);
        cardView.setFitWidth(260);
        cardView.setOnMouseClicked(e -> cardRead());

        status = new Label("Ready. Tap to send a random digit (0–9).");

        VBox root = new VBox(12, cardView, status);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER);

        stage.setTitle("Card Reader");
        stage.setScene(new Scene(root, 320, 380));
        stage.setOnCloseRequest(e -> {
            try { if (dm != null) dm.close(); } catch (Exception ignore) {}
            Platform.exit();
        });
        stage.show();

        startIdle();
    }

    private void startIdle() {
        if (idleFlash != null) idleFlash.stop();
        flip = false;
        idleFlash = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            flip = !flip;
            cardView.setImage(flip ? imgIdle1 : imgIdle0);
        }));
        idleFlash.setCycleCount(Timeline.INDEFINITE);
        idleFlash.play();
    }

    private void stopIdle() {
        if (idleFlash != null) idleFlash.stop();
    }

    private void cardRead() {
        Image current = cardView.getImage();
        if (current != imgIdle0 && current != imgIdle1) return;

        int d = ThreadLocalRandom.current().nextInt(10);
        String ok;
        try {
            ok = reader.request("CARDREADER|DEVCTL|MAIN|" + d, java.time.Duration.ofSeconds(1));
        } catch (Exception e) {
            ok = "NO REPLY";
        }

        ScaleTransition st = new ScaleTransition(Duration.millis(150), cardView);
        st.setFromX(1.0); st.setFromY(1.0);
        st.setToX(0.9);   st.setToY(0.9);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();

        stopIdle();
        boolean accepted = (d % 2 == 0);
        cardView.setImage(accepted ? imgAccepted : imgDeclined);
        status.setText("Queued tap: " + d + " (server replied: " + ok + ")" + (accepted ? "  → authorized" : "  → declined"));

        PauseTransition back = new PauseTransition(Duration.millis(1200));
        back.setOnFinished(e -> {
            cardView.setImage(imgIdle0);
            status.setText("Ready. Tap to send a random digit (0–9).");
            startIdle();
        });
        back.play();
    }

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


