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

import java.io.InputStream;
import java.lang.reflect.Method;

/**
 * Minimal animation logic:
 *   not attached -> P-0.png
 *   attached     -> toggle P-1-0-0.png and P-1-0-1.png
 *
 * Attempts to read "attached" from existing code (unchanged):
 *   - ui.HoseGUI.isAttached() or getAttached()
 *   - sim.SimDevices.isHoseAttached() or getHoseAttached()
 *
 * If none found, press 'A' to toggle locally for testing.
 */
public class PumpGUI extends Application {

    // --- image names (under resources /images/pump/) ---
    private static final String BASE = "/images/pump/";
    private static final String IMG_OFF   = "P-0.png";
    private static final String IMG_A0    = "P-1-0-0.png";
    private static final String IMG_A1    = "P-1-0-1.png";

    // --- UI ---
    private ImageView view;
    private Image imgOff, imgA0, imgA1;

    // --- animation ---
    private Timeline loop;
    private boolean flip = false;

    // --- attached state (observable) ---
    private final BooleanProperty attached = new SimpleBooleanProperty(false);

    // --- reflection hooks (none of your other files need to change) ---
    private Method mIsAttached;   // static boolean isAttached() / getAttached() / isHoseAttached()/getHoseAttached()
    private Class<?> providerClass;

    @Override
    public void start(Stage stage) {
        System.out.println("[PumpGUI] Launching");

        // Load images
        imgOff = load(BASE + IMG_OFF);
        imgA0  = load(BASE + IMG_A0);
        imgA1  = load(BASE + IMG_A1);
        if (imgOff == null) warnMissing(IMG_OFF);
        if (imgA0  == null) warnMissing(IMG_A0);
        if (imgA1  == null) warnMissing(IMG_A1);

        view = new ImageView(imgOff);
        view.setPreserveRatio(true);
        view.setFitHeight(420);

        BorderPane root = new BorderPane(view);
        BorderPane.setAlignment(view, Pos.CENTER);

        Scene scene = new Scene(root, 520, 480);
        stage.setTitle("PumpGUI");
        stage.setScene(scene);
        stage.show();

        // Try to wire to your existing code (unchanged)
        wireAttachmentGetter();

        // Poll attached flag & animate
        loop = new Timeline(new KeyFrame(Duration.millis(180), e -> tick()));
        loop.setCycleCount(Animation.INDEFINITE);
        loop.play();

        // Test toggle if we couldn't find a provider
        scene.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case A -> {
                    if (mIsAttached == null) {
                        attached.set(!attached.get());
                        System.out.println("[PumpGUI] (TEST) attached=" + attached.get());
                        // force refresh now instead of waiting for next tick
                        tick();
                    }
                }
                default -> {}
            }
        });

        
        tick();
    }

    private void tick() {
        if (mIsAttached != null) {
            try {
                Object v = mIsAttached.invoke(null);
                if (v instanceof Boolean b) {
                    attached.set(b);
                } else {
                    attached.set(false);
                }
            } catch (Throwable t) {
                System.out.println("[PumpGUI] Provider error; disabling: " + t);
                mIsAttached = null;
            }
        }

        // Render based on attached
        if (!attached.get()) {
            // not attached -> P-0.png
            if (imgOff != null) view.setImage(imgOff);
        } else {
            // attached -> toggle between two frames
            flip = !flip;
            if (flip) {
                if (imgA0 != null) view.setImage(imgA0);
            } else {
                if (imgA1 != null) view.setImage(imgA1);
            }
        }
    }

    private void wireAttachmentGetter() {
        // Try ui.HoseGUI first
        providerClass = tryClass("ui.HoseGUI");
        if (providerClass != null) {
            mIsAttached = tryAnyStaticBoolean(providerClass, "isAttached", "getAttached");
        }

        // Try sim.SimDevices next if not found
        if (mIsAttached == null) {
            providerClass = tryClass("sim.SimDevices");
            if (providerClass != null) {
                mIsAttached = tryAnyStaticBoolean(providerClass, "isHoseAttached", "getHoseAttached", "isAttached", "getAttached");
            }
        }

        if (mIsAttached != null) {
            System.out.println("[PumpGUI] Attached provider wired: " +
                    providerClass.getName() + "." + mIsAttached.getName() + "()");
        } else {
            System.out.println("[PumpGUI] No attached provider found. Press 'A' to toggle for testing.");
        }
    }

    private static Class<?> tryClass(String name) {
        try { return Class.forName(name); } catch (Throwable ignored) {}
        return null;
    }

    private static Method tryAnyStaticBoolean(Class<?> cls, String... names) {
        for (String n : names) {
            try {
                Method m = cls.getMethod(n);
                if ((m.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0 &&
                        (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    return m;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Image load(String path) {
        try (InputStream in = PumpGUI.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return new Image(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static void warnMissing(String name) {
        System.out.println("[PumpGUI] WARN: image not found: " + name + " (check resources " + BASE + ")");
    }

    @Override
    public void stop() {
        if (loop != null) loop.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
