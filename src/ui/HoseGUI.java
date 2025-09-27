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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

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

    private List<Image> connectedImages;
    private List<Image> disconnectedImages;
    private boolean firstTimeTankConnection;
    private boolean tankFull;
    private double tankSize;
    private double currentTankFill;

    @Override
    public void start(Stage stage) throws Exception {

        var entries = List.of(
                new DeviceManager.Entry(HOSE_NAME, HOST, HOSE_PORT, HOSE_ID, "binary")
        );
        dm = new DeviceManager(entries);
        hose = dm.binary(HOSE_NAME);
        
        // When Hose is initially run, it is the first connection by default
        firstTimeTankConnection = true;


        // Load all images
        connectedImages = new ArrayList<>();
        disconnectedImages = new ArrayList<>();
        loadImages();
        
        
//        Image imgDetached = new Image(
//                Objects.requireNonNull(HoseGUI.class.getResource("/images/hose_detached.png"))
//                        .toExternalForm());
//
//        Image imgAttached = new Image(
//                Objects.requireNonNull(HoseGUI.class.getResource("/images/hose_attached.png"))
//                        .toExternalForm());

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

    // This method loads the 22 images used for the gass nozzle
    private void loadImages() {

        for (int i=0; i<=10; i++) {
            String imageName = "GN-D-" + i;
            Image image = new Image(
                Objects.requireNonNull(HoseGUI.class.getResource("/images/fuelNozzle/" + imageName))
                        .toExternalForm());
            disconnectedImages.add(image);
        }

        for (int i=0; i<=10; i++) {
            String imageName = "GN-C-" + i;
            Image image = new Image(
                    Objects.requireNonNull(HoseGUI.class.getResource("/images/fuelNozzle/" + imageName))
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
        loadNewImage((int) Math.floor(percentFull * 11));
    }

    // Loads new image 0-10 based on the number sent and status of attached variable
    private void loadNewImage(int number) {

        if (!attached) {
            image.setImage(disconnectedImages.get(number));
        }
        else {
            image.setImage(connectedImages.get(number));
        }
    }



    public static void main(String[] args) { launch(args); }
}
