package ui;

import devices.BinaryDevice;
import io.bus.DeviceManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class HoseGUI extends Application {

    private static final boolean OFFLINE_MODE = true;

    private static final String HOST = "127.0.0.1";
    private static final int    HOSE_PORT = 5101;
    private static final String HOSE_ID = "hose-01";
    private static final String HOSE_NAME = "hose-sensor";

    private DeviceManager dm;
    private BinaryDevice hose;

    private boolean attached = false; // start DETACHED
    private ImageView image;
    private Label status;

    private List<Image> connectedImages;
    private List<Image> disconnectedImages;
    private boolean firstTimeTankConnection;
    private boolean tankFull;
    private double tankSize;
    private double currentTankFill;

    @Override
    public void start(Stage stage) throws Exception {

        //var entries = List.of(
        //        new DeviceManager.Entry(HOSE_NAME, HOST, HOSE_PORT, HOSE_ID, "binary")
        //);
        //dm = new DeviceManager(entries);
        //hose = dm.binary(HOSE_NAME);
        
        // When Hose is initially run, it is the first connection by default
        firstTimeTankConnection = true;


        // Load all images
        connectedImages = new ArrayList<>();
        disconnectedImages = new ArrayList<>();
        loadImages();

        for (int i = 0; i <= 10; i++) {
            Image d = new Image(Objects.requireNonNull(
                    HoseGUI.class.getResource("/images/hose/GN-D-" + i + ".png")
            ).toExternalForm());
            disconnectedImages.add(d);
        }
        for (int i = 0; i <= 10; i++) {
            Image c = new Image(Objects.requireNonNull(
                    HoseGUI.class.getResource("/images/hose/GN-C-" + i + ".png")
            ).toExternalForm());
            connectedImages.add(c);
        }

        image = new ImageView(disconnectedImages.get(0));
        image.setFitWidth(240);
        image.setPreserveRatio(true);
        image.setCursor(Cursor.HAND);

        status = new Label("Hose: DETACHED");
        status.setStyle("-fx-font-size: 16px;");

        image.setOnMouseClicked(e -> toggle());

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

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
    }


    // Old toggle() function
//    private void toggle(Image imgDetached, Image imgAttached) {
//        attached = !attached;
//        setSimState(attached);
//        image.setImage(attached ? imgAttached : imgDetached);
//        status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");
//    }


    // New toggle() function
    private void toggle() {
        
        // Checks if this is the first connection to this car
        if (!attached && firstTimeTankConnection) {
            initializeNewTank();
        }

        // Gets the index of the image in one of the two arrays
        int index = attached ? connectedImages.indexOf(
                image.getImage()) : disconnectedImages.indexOf(image.getImage());

        // Switches state, image and text
        attached = !attached;
        setSimState(attached);
        image.setImage(attached ? connectedImages.get(index) : disconnectedImages.get(index));
        status.setText(attached ? "Hose: ATTACHED" : "Hose: DETACHED");

        incrementLoop();
    }

    private void setSimState(boolean isAttached) {
        if (OFFLINE_MODE) return;
        try {
            hose.set(isAttached); // sends SET 1/0 over DeviceLink → SimDevices
        } catch (Exception ex) {
            status.setText("ERROR sending state: " + ex.getMessage());
        }
    }

    private void pollLoop() {
        if (OFFLINE_MODE) return;
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

    private void incrementLoop() {
        // Create the timeline and keep a reference so we can stop it
        Timeline timeline = new Timeline();

        KeyFrame keyFrame = new KeyFrame(Duration.millis(200), e -> {
            if (!attached || tankFull) {
                timeline.stop(); // now we can stop it directly
                return;
            }

            // increment
            currentTankFill += tankSize * 0.01;
            if (currentTankFill >= tankSize) {
                tankFull = true;
            }

            // calculate percentage
            double percentFull = currentTankFill / tankSize;

            // update the UI
            changeImage((int) Math.floor(percentFull * 11));
        });

        timeline.getKeyFrames().add(keyFrame);
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }



    // This method loads the 22 images used for the gass nozzle
    private void loadImages() {

        for (int i=0; i<=10; i++) {
            String imageName = "GN-D-" + i + ".png";
            Image image = new Image(
                Objects.requireNonNull(HoseGUI.class.getResource("/images/hose/" + imageName))
                        .toExternalForm());
            disconnectedImages.add(image);
        }

        for (int i=0; i<=10; i++) {
            String imageName = "GN-C-" + i + ".png";
            Image image = new Image(
                    Objects.requireNonNull(HoseGUI.class.getResource("/images/hose/" + imageName))
                            .toExternalForm());

            connectedImages.add(image);
        }
    }


    // A connection to a new car has been made, tank needs to be "sensed"
    private void initializeNewTank() {

        // It is now false that this tank is new, this variable must be reset before a new connection
        // can be made
        firstTimeTankConnection = false;

        Random rand = new Random();
        tankFull = false;
        tankSize = 10 + rand.nextDouble() * 20;
        currentTankFill = tankSize * (rand.nextDouble() * rand.nextDouble() * rand.nextDouble());

        double percentFull = currentTankFill / tankSize;
        changeImage((int) Math.floor(percentFull * 11));
    }

    // Loads new image 0-10 based on the number sent and status of attached variable
    private void changeImage(int number) {

        if (tankFull) {
            return;
        }

        if (!attached) {
            image.setImage(disconnectedImages.get(number));
        }
        else {
            image.setImage(connectedImages.get(number));
        }
    }

    public static void main(String[] args) { launch(args); }
}
