package ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;


public final class PumpGUI extends Application {

    // ---- CONFIG ----
    private static final boolean SAME_PROCESS = true;
    private static final long    PERIOD_MS     = 350;

    // Device-poll mode config (only used if SAME_PROCESS = false)
    private static final String  HOSE_ENTRY_NAME = "hose-sensor";
    private static final String  HOST = "127.0.0.1";
    private static final int     PORT = 5101;
    private static final String  DEVICE_ID = "hose-01";
    private static final String  KIND = "binary";

    // ---- images ----
    private static final String BASE = "/images/pump/";
    private static final String IMG_OFF = "P-0.png";
    private static final String IMG_UPPER = "P-1-1-0.png";
    private static final String IMG_LOWER = "P-1-1-1.png";

    // ---- shared bus for same-process wiring (HoseGUI updates this) ----
    public static final class Bus {
        private static final BooleanProperty hoseAttached = new SimpleBooleanProperty(false);
        public static BooleanProperty hoseAttachedProperty() { return hoseAttached; }
        public static boolean isHoseAttached() { return hoseAttached.get(); }
        public static void setHoseAttached(boolean v) { hoseAttached.set(v); }
    }

    private Image imgOff, imgUpper, imgLower;
    private ImageView view;
    private boolean showUpper = true;

    // device-poll state (reflection to avoid compile-time dependency)
    private Object deviceManager;
    private Object hoseDevice;

    private Timeline loop;

    @Override
    public void start(Stage stage) {
        // load images
        imgOff   = load(IMG_OFF);
        imgUpper = load(IMG_UPPER);
        imgLower = load(IMG_LOWER);

        view = new ImageView(imgUpper);
        view.setPreserveRatio(true);
        view.setFitWidth(360);

        var root = new BorderPane(view);
        BorderPane.setAlignment(view, Pos.CENTER);

        stage.setTitle("Pump");
        stage.setScene(new Scene(root, 420, 320));
        stage.show();

        if (!SAME_PROCESS) {
            initDevicePoll();
        } else {
            // immediate refresh when HoseGUI flips the bus
            Bus.hoseAttachedProperty().addListener((obs, oldV, newV) -> refreshNow());
        }

        // timer loop
        loop = new Timeline(new KeyFrame(Duration.millis(PERIOD_MS), e -> tick()));
        loop.setCycleCount(Animation.INDEFINITE);
        loop.play();

        refreshNow(); // initial paint
    }

    private void tick() {
        boolean attached = getAttached();
        if (!attached) {
            if (view.getImage() != imgUpper) view.setImage(imgLower);
            if (view.getImage() != imgLower) view.setImage(imgUpper);

            return;
        }
        // toggle upper/lower while attached
        showUpper = !showUpper;
        view.setImage(showUpper ? imgUpper : imgLower);
    }

    private void refreshNow() {
        if (!getAttached()) {
            view.setImage(imgUpper);
        } else {
            view.setImage(showUpper ? imgUpper : imgLower);
        }
    }

    private boolean getAttached() {
        if (SAME_PROCESS) return Bus.isHoseAttached();
        return pollHose(); // device-poll mode
    }

    // ---------- device-poll mode ----------
    private void initDevicePoll() {
        try {
            Class<?> dmClass = Class.forName("io.bus.DeviceManager");
            Class<?> entryClass = Class.forName("io.bus.DeviceManager$Entry");

            var entryCtor = entryClass.getConstructor(String.class, String.class, int.class, String.class, String.class);
            Object entry = entryCtor.newInstance(HOSE_ENTRY_NAME, HOST, PORT, DEVICE_ID, KIND);

            var list = java.util.List.of(entry);
            var dmCtor = dmClass.getConstructor(java.util.List.class);
            deviceManager = dmCtor.newInstance(list);

            // call dm.binary(String) -> BinaryDevice
            var binaryMethod = dmClass.getMethod("binary", String.class);
            hoseDevice = binaryMethod.invoke(deviceManager, HOSE_ENTRY_NAME);

            System.out.println("[PumpGUI] Device-poll initialized");
        } catch (Throwable t) {
            System.err.println("[PumpGUI] Device-poll init FAILED: " + t);
            deviceManager = null;
            hoseDevice = null;
        }
    }

    private boolean pollHose() {
        if (hoseDevice == null) return false;
        try {
            // BinaryDevice#get() -> boolean
            var m = hoseDevice.getClass().getMethod("get");
            Object v = m.invoke(hoseDevice);
            return (v instanceof Boolean b) ? b : false;
        } catch (Throwable t) {
            System.err.println("[PumpGUI] pollHose failed: " + t);
            return false;
        }
    }

    // ---------- image loading ----------
    private Image load(String filename) {
        var url = PumpGUI.class.getResource(BASE + filename);
        if (url != null) {
            return new Image(url.toExternalForm(), false);
        }
        // dev fallback if classpath fails
        var p = java.nio.file.Paths.get("resources/images/pump/" + filename);
        if (java.nio.file.Files.exists(p)) {
            return new Image(p.toUri().toString(), false);
        }
        System.err.println("[PumpGUI] Image NOT FOUND: " + filename + "  cwd=" + System.getProperty("user.dir"));
        return new javafx.scene.image.WritableImage(360, 240); // obvious blank
    }

    @Override
    public void stop() {
        if (loop != null) loop.stop();
        // close DeviceManager if we opened it
        if (deviceManager != null) {
            try {
                var m = deviceManager.getClass().getMethod("close");
                m.invoke(deviceManager);
            } catch (Throwable ignored) {}
        }
    }

    public static void main(String[] args) { launch(args); }
}

