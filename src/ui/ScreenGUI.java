package ui;

import io.bus.DeviceLink;
import io.bus.DeviceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ScreenGUI extends Application {
    private DeviceManager dm;
    private DeviceLink screenCtrl;

    private double SCENE_WIDTH;
    private double SCENE_HEIGHT;

    private List<Row> defaultSceneRows;

    @Override
    public void start(Stage stage) throws Exception {
        // Connect only to Screen control port (NOT the main device port)
        var entries = java.util.List.of(
                new DeviceManager.Entry("screen-ctrl", "127.0.0.1", 5021, "screen-ctrl", "screen")
        );
        dm = new DeviceManager(entries);
        screenCtrl = dm.link("screen-ctrl");

        // Size the scene relative to monitor height
        Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        SCENE_HEIGHT = screenBounds.getHeight() * 0.75;
        SCENE_WIDTH  = SCENE_HEIGHT; // square

        Font defaultFont = new Font("Verdana", SCENE_HEIGHT / 10.0);

        // Build the default scene (5 rows, buttons + labels)
        Scene defaultScene = createDefaultScene(defaultFont);

        // Stage cosmetics
        stage.setResizable(false);
        stage.setTitle("Screen");
        stage.setScene(defaultScene);
        stage.show();

        // Start polling the control port and render code-word states
        startPolling();
    }

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
        Platform.exit();
        System.exit(0);
    }

    // ------------------------------------------------------------------------
    // Poll Screen-CTRL for state and render it using the fancy layout
    // ------------------------------------------------------------------------
    private void startPolling() {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    // Expect: MAIN|REPLY|SCREEN|"STATE:WELCOME" (or AUTH_OK / AUTH_NO)
                    String reply = screenCtrl.request("SCREEN|GET|STATE|None", Duration.ofMillis(500));
                    int q1 = reply.indexOf('"');
                    int q2 = reply.lastIndexOf('"');
                    String payload = (q1 >= 0 && q2 > q1) ? reply.substring(q1 + 1, q2) : "";
                    String state   = payload.startsWith("STATE:") ? payload.substring(6) : payload;

                    Platform.runLater(() -> applyState(state));
                    Thread.sleep(300);
                }
            } catch (Exception ignored) {
            }
        }, "screen-poll");
        t.setDaemon(true);
        t.start();
    }

    // Map state code-words to text shown on the “combined” labels
    private void applyState(String state) {
        // Clear all rows first
        for (Row r : defaultSceneRows) r.clear();

        switch (state) {
            case "WELCOME":
                // Row 0: “Welcome!”
                defaultSceneRows.get(0).showCombined("Welcome!", /*size*/2, /*font*/0);
                // Row 1: “Please tap card.”
                defaultSceneRows.get(1).showCombined("Please tap card.", 1, 0);
                // Buttons are “unused”; leave them inactive/blank
                break;

            case "AUTH_OK":
                defaultSceneRows.get(0).showCombined("Authorization Approved", 2, 0);
                defaultSceneRows.get(1).showCombined("Select fuel type...",    1, 0);
                break;

            case "AUTH_NO":
                defaultSceneRows.get(0).showCombined("Card Declined", 2, 0);
                defaultSceneRows.get(1).showCombined("Please tap card", 1, 0);
                break;

            default:
                // Unknown state: just print it centered on row 0
                defaultSceneRows.get(0).showCombined(state, 2, 0);
                break;
        }
        // Ensure layout shows combined label between the two side buttons
        for (Row r : defaultSceneRows) r.ensureCombinedLayout();
    }

    // ------------------------------------------------------------------------
    // 5 rows, two side buttons per row, central "combined" label area, black background, Verdana fonts.
    // ------------------------------------------------------------------------
    private Scene createDefaultScene(Font defaultFont) {
        VBox root = new VBox();
        root.setPrefSize(SCENE_WIDTH, SCENE_HEIGHT);
        root.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

        double rowHeight = SCENE_HEIGHT / 5.0;
        defaultSceneRows = new ArrayList<>();

        int btnNumber = 0;
        for (int i = 0; i < 5; i++) {
            List<DefaultButton> buttons = new ArrayList<>(2);
            for (int j = 0; j < 2; j++) {
                DefaultButton b = new DefaultButton(btnNumber++, rowHeight, defaultFont);
                b.setOnMouseClicked(e -> {
                    // Buttons are visually present but “unused”; do nothing.
                    // If we want them active later:
                    // if (b.active) { /* send something */ }
                });
                buttons.add(b);
            }
            Row row = new Row(i, rowHeight, SCENE_WIDTH, defaultFont, buttons);
            defaultSceneRows.add(row);
            root.getChildren().add(row);
        }

        return new Scene(root);
    }

    // =====================================================================================
    // Below are the cosmetic classes
    // =====================================================================================

    static class Row extends HBox {
        private final int number;
        private final DefaultButton leftButton;
        private final DefaultButton rightButton;
        private final DefaultLabel leftLabel;
        private final DefaultLabel rightLabel;
        private final DefaultLabel combinedLabel;

        private final double height;
        private final double width;

        public Row (int number, double height, double width, Font font, List<DefaultButton> buttons) {
            if (buttons.size() != 2) throw new RuntimeException("Row needs 2 buttons");
            this.number = number;
            this.height = height;
            this.width  = width;

            setPrefSize(width, height);
            setMinSize(width, height);
            setMaxSize(width, height);

            this.leftButton  = buttons.get(0);
            this.rightButton = buttons.get(1);

            // Create labels panel
            double labelWidth = width - (2 * height); // buttons are squares of side=height
            this.leftLabel     = new DefaultLabel(labelWidth/2, height, "left");
            this.rightLabel    = new DefaultLabel(labelWidth/2, height, "right");
            this.combinedLabel = new DefaultLabel(labelWidth,   height, "combined");

            // Start with combined label layout (buttons on sides)
            getChildren().addAll(leftButton, combinedLabel, rightButton);
        }

        public void clear() {
            leftButton.setActive(false);
            rightButton.setActive(false);
            leftLabel.clear();
            rightLabel.clear();
            combinedLabel.clear();
            ensureCombinedLayout();
        }

        // Show center “combined” message with size/font indexes (0..2)
        public void showCombined(String msg, int sizeIndex, int fontIndex) {
            combinedLabel.setLabelText(msg, sizeIndex, fontIndex);
        }

        // Make sure the children reflect the combined layout look
        public void ensureCombinedLayout() {
            getChildren().setAll(leftButton, combinedLabel, rightButton);
        }
    }

    static class DefaultButton extends Button {
        private final int number;
        private final Font font;
        private boolean active;

        public DefaultButton(int number, double size, Font font) {
            this.number = number;
            this.font   = font;
            this.active = false;

            setPrefSize(size, size);
            setFont(font);
            setText(""); // inactive by default
        }

        public void setActive(boolean active) {
            this.active = active;
            setText(active ? String.valueOf(number) : "");
        }
    }

    static class DefaultLabel extends Label {
        private final List<Integer> sizes = new ArrayList<>();
        private final List<String>  fontNames = new ArrayList<>();

        public DefaultLabel (double width, double height, String alignment) {
            setPrefSize(width, height);
            setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
            setBorder(Border.stroke(Color.GRAY));

            sizes.add((int)(height/10)); // small
            sizes.add((int)(height/5));  // medium
            sizes.add((int)(height/2));  // large

            fontNames.add("Verdana");
            fontNames.add("Verdana");
            fontNames.add("Verdana");

            switch (alignment) {
                case "left":     setAlignment(Pos.CENTER_LEFT);  break;
                case "right":    setAlignment(Pos.CENTER_RIGHT); break;
                case "combined": setAlignment(Pos.CENTER);       break;
                default: throw new RuntimeException("Unknown label alignment");
            }
        }

        public void setLabelText(String msg, int sizeIndex, int fontIndex) {
            setFont(new Font(fontNames.get(Math.max(0, Math.min(fontIndex, 2))),
                    sizes.get(Math.max(0, Math.min(sizeIndex, 2)))));
            setText(msg);
        }

        public void clear() {
            setText("");
        }
    }
}
