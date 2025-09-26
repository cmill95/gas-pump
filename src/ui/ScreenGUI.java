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

        var entries = java.util.List.of(
                new DeviceManager.Entry("screen-ctrl", "127.0.0.1", 5021, "screen-ctrl", "screen")
        );
        dm = new DeviceManager(entries);
        screenCtrl = dm.link("screen-ctrl");

        Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        SCENE_HEIGHT = screenBounds.getHeight() * 0.75;
        SCENE_WIDTH  = SCENE_HEIGHT;

        Font defaultFont = new Font("Verdana", SCENE_HEIGHT / 10.0);

        Scene defaultScene = createDefaultScene(defaultFont);

        stage.setResizable(false);
        stage.setTitle("Screen");
        stage.setScene(defaultScene);
        stage.show();

        startPolling();
    }

    @Override
    public void stop() throws Exception {
        if (dm != null) dm.close();
        Platform.exit();
        System.exit(0);
    }

    private void resetAllButtons() {
        for (Row r : defaultSceneRows) {
            r.leftButton.setDisable(true);
            r.rightButton.setDisable(true);

            r.leftButton.setActive(false);
            r.rightButton.setActive(false);

            r.leftButton.setText("");
            r.rightButton.setText("");

            r.leftButton.setOnAction(null);
            r.rightButton.setOnAction(null);

            r.leftButton.setStyle(null);
            r.rightButton.setStyle(null);

            r.leftButton.setBorder(null);
            r.rightButton.setBorder(null);
        }
    }

    private void startPolling() {
        Thread t = new Thread(() -> {
            try {
                while (true) {

                    String reply = screenCtrl.request("SCREEN|GETSTATE|MAIN|None", Duration.ofMillis(500));
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

    private void applyState(String state) {

        for (Row r : defaultSceneRows) r.clear();

        if (state.startsWith("GRADE_MENU:")) {
            String csv = state.substring("GRADE_MENU:".length());
            String[] items = csv.split(",");

            for (int i = 0; i < items.length && i < defaultSceneRows.size(); i++) {
                String[] kv = items[i].split("=");
                if (kv.length == 2) {
                    String fuel  = kv[0].trim();
                    String price = kv[1].trim();
                    String line  = fuel + " — $" + price;

                    Row row = defaultSceneRows.get(i);
                    row.showCombined(line, 1, 0);

                    var btn = row.rightButton;
                    btn.setDisable(false);

                    btn.setActive(false);
                    btn.setText("");

                    btn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");

                    btn.setBorder(new Border(new BorderStroke(
                            javafx.scene.paint.Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(3))));

                    btn.setOnMouseEntered(e -> btn.setBorder(new Border(new BorderStroke(
                            javafx.scene.paint.Color.YELLOW, BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(3)))));
                    btn.setOnMouseExited(e -> btn.setBorder(new Border(new BorderStroke(
                            javafx.scene.paint.Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(3)))));

                    btn.setOnAction(ev -> {
                        try {
                            screenCtrl.request("SCREEN|DEVCTL|MAIN|" + fuel, java.time.Duration.ofSeconds(1));
                        } catch (Exception ignore) {}
                    });
                }
            }

            int nextRow = Math.min(items.length, defaultSceneRows.size() - 1);
            defaultSceneRows.get(nextRow).showCombined("Press the right-side button to select", 0, 0);

            for (Row r : defaultSceneRows) r.ensureCombinedLayout();
            return;
        }

        resetAllButtons();

        if (state.startsWith("FUEL_SELECTED:")) {
            String fuel = state.substring("FUEL_SELECTED:".length()).trim();

            int topRow = Math.min(1, defaultSceneRows.size() - 1);
            int botRow = Math.min(2, defaultSceneRows.size() - 1);

            defaultSceneRows.get(topRow).showCombined("Fuel selected: " + fuel, 1, 0);
            defaultSceneRows.get(botRow).showCombined("Please attach hose to begin fueling", 1, 0);

            for (Row r : defaultSceneRows) r.ensureCombinedLayout();
            return;
        }

        if (state.equals("FUEL_SELECTED")) {
            int topRow = Math.min(1, defaultSceneRows.size() - 1);
            int botRow = Math.min(2, defaultSceneRows.size() - 1);
            defaultSceneRows.get(topRow).showCombined("Fuel selected", 1, 0);
            defaultSceneRows.get(botRow).showCombined("Please attach hose to begin fueling", 1, 0);
            for (Row r : defaultSceneRows) r.ensureCombinedLayout();
            return;
        }

        switch (state) {
            case "WELCOME":
                defaultSceneRows.get(0).showCombined("Welcome!", 2, 0);
                defaultSceneRows.get(1).showCombined("Please tap card.", 1, 0);
                for (Row r : defaultSceneRows) r.ensureCombinedLayout();
                return;

            case "AUTH_OK":
                defaultSceneRows.get(0).showCombined("Authorization Approved", 2, 0);
                defaultSceneRows.get(1).showCombined("Select fuel type...", 1, 0);
                for (Row r : defaultSceneRows) r.ensureCombinedLayout();
                return;

            case "AUTH_NO":
                defaultSceneRows.get(0).showCombined("Declined...", 2, 0);
                defaultSceneRows.get(1).showCombined("Please try again.", 1, 0);
                for (Row r : defaultSceneRows) r.ensureCombinedLayout();
                return;
        }

        defaultSceneRows.get(0).showCombined(state, 1, 0);
        for (Row r : defaultSceneRows) r.ensureCombinedLayout();
    }

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

                });
                buttons.add(b);
            }
            Row row = new Row(i, rowHeight, SCENE_WIDTH, defaultFont, buttons);
            defaultSceneRows.add(row);
            root.getChildren().add(row);
        }

        return new Scene(root);
    }

    static class Row extends HBox {
        private final int number;
        private final DefaultButton leftButton;
        public final DefaultButton rightButton;
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

            double labelWidth = width - (2 * height);
            this.leftLabel     = new DefaultLabel(labelWidth/2, height, "left");
            this.rightLabel    = new DefaultLabel(labelWidth/2, height, "right");
            this.combinedLabel = new DefaultLabel(labelWidth,   height, "combined");

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

        public void showCombined(String msg, int sizeIndex, int fontIndex) {
            combinedLabel.setLabelText(msg, sizeIndex, fontIndex);
        }

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
            setText("");
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
