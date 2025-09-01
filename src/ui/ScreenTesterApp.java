package ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import devices.Screen;
import io.bus.DeviceLink;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenTesterApp extends Application {
    private Screen screen;

    private double SCENE_WIDTH;
    private double SCENE_HEIGHT;

    private int mode;
    private Scene defaultScene;
    private Scene fillScene;

    private List<Row> defaultSceneRows;

    @Override
    public void start(Stage stage) throws Exception {


        // Connect directly to SimDevices
        // - (commented out for testing purposes)
        DeviceLink link = new DeviceLink("127.0.0.1", 5001, "screen-01");
        screen = new Screen(link);

        // Get the monitor size to set scene size
        Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        SCENE_HEIGHT = screenBounds.getHeight() * 0.75;
        SCENE_WIDTH = SCENE_HEIGHT;

        // TODO: Add multiple fonts
        Font defaultFont = new Font("Verdana", SCENE_HEIGHT/10);

        // Mode starts at 0, meaning the Default scene is loaded, a mode = 1 means Fill scene is loaded
        mode = 0;
        createDefaultScene(defaultFont);
        createFillScene(defaultFont);

        //TODO: Add ability for resizing
        stage.setResizable(false);


        // Force closes App. when GUI closed
        stage.setOnCloseRequest(e -> {
            Platform.exit();   // shuts down JavaFX runtime
            System.exit(0);    // ensures JVM ends
        });



        stage.getIcons().add(new Image("file:resources/icon.png"));
        stage.setTitle("Screen");
        stage.setScene(defaultScene);
        stage.show();



        // TODO: Add a simple starting screen example which sets the rows
        // TODO: Add a class which redraws the Screen (may not need)


//        // Basic Welcome screen displaying functionality, should be removed eventually
//        StringBuilder strBuilder = new StringBuilder();
//        strBuilder.append("SCREEN|DEFAULT|0|BUTTON|LEFT|OFF");
//        strBuilder.append("_");
//        strBuilder.append("SCREEN|DEFAULT|0|BUTTON|RIGHT|OFF");
//        strBuilder.append("_");
//        strBuilder.append("SCREEN|DEFAULT|0|LABEL|COMBINED|WELCOME!|2|0");
//        strBuilder.append("_");
//        strBuilder.append("SCREEN|DEFAULT|4|BUTTON|LEFT|ON");
//        strBuilder.append("_");
//        strBuilder.append("SCREEN|DEFAULT|4|BUTTON|RIGHT|ON");
//        strBuilder.append("_");
//        strBuilder.append("SCREEN|DEFAULT|4|LABEL|LEFT|YES|1|0");
//        strBuilder.append("_");
//        strBuilder.append("SCREEN|DEFAULT|4|LABEL|RIGHT|NO|1|0");
//
//
//        receive(strBuilder.toString());
    }

    /**
     * This method is called by Screen to update ScreenTesterApp. It relays messages from Main which update Screen
     * @param msg
     */
    public void receive(String msg) {

        if (!msg.isEmpty()) {

            String[] messages = msg.split("_");

            for (String message : messages) {

                String[] parts = message.split("\\|");

                // Mode is Default and message pertains to Default
                if (parts[1].equals("DEFAULT") && mode == 0) {

                    // Trims the message and sends it to the correct row
                    List<String> infoForRows = new ArrayList<>(Arrays.asList(parts).subList(3, parts.length));
                    defaultSceneRows.get(Integer.parseInt(parts[2])).receiveMessage(infoForRows);
                }

                // Mode is Fill and message pertains to Fill
                else if (parts[1].equals("FILL") && mode == 1) {

                    // TODO: Handle messages when in FILL scene
                }

                // Change of scene from Default to Fill
                else if (parts[1].equals("FILL") && mode == 0) {

                    // Default->Fill
                    mode = 1;

                    // TODO: Handle Scene change

                }

                // Change of scene from Fill to Default
                else if (parts[1].equals("DEFAULT") && mode == 1) {

                    // Fill->Default
                    mode = 0;

                    // TODO: Handle Scene change

                }

                // Message has unrecognized Screen mode
                else {

                    throw new RuntimeException("Unrecognized Screen Mode: Not DEFAULT/FILL");

                }

            }
        }

        else {
            throw new RuntimeException("Message to Screen is Empty");
        }
    }

    private void send(String msg) {

        // TODO: Message needs to be sent out to Screen and DeviceLink
        // - EX) "MAIN|BUTTON|Button#"

        

    }

    /**
     * This method creates the default scene displayed on the GUI. It also creates the
     * buttons and labels displayed on the GUI
     * <p>
     * NOTE: The order of the Buttons and the ability for Labels to be split into left, right and combined
     * must be addressed
     *
     * @param defaultFont a default font must be passed to this method which is disseminated to the Buttons
     *                    and the labels
     */
    private void createDefaultScene(Font defaultFont) {

        defaultScene = null;

        // This VBox serves as the primary container for the Default Scene
        VBox vBox = new VBox();
        vBox.setPrefSize(SCENE_WIDTH, SCENE_HEIGHT);
        vBox.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

        // Used along with SCENE_WIDTH to size the rows/buttons
        double rowHeight = SCENE_HEIGHT / 5;

        // Creates the 10 buttons, creates the 5 rows, passes buttons -> rows
        List<Row> rows = new ArrayList<>();
        int counter = 0;
        for (int i = 0; i < 5; i++) {

            List<DefaultButton> buttons = new ArrayList<>();
            for (int j = 0; j < 2; j++) {

                // This assumes buttons are squares
                DefaultButton button = new DefaultButton(counter, rowHeight, defaultFont);
                button.setOnMouseClicked(e -> {
                    defaultButonPressed(button);
                });
                buttons.add(button);
                counter++;
            }
            rows.add(new Row(i, rowHeight, SCENE_WIDTH, defaultFont, buttons));
        }

        for (Row row : rows) {
            vBox.getChildren().addAll(row);
        }


        defaultSceneRows = rows;
        defaultScene = new Scene(vBox);
    }

    /**
     * This method creates the Fill scene which displays the status of the tank being filled to the GUI.
     * NOTE: Functionality must be added
     *
     * @param defaultFont the default font is passed to this function
     */
    private void createFillScene(Font defaultFont) {


    }

    /**
     * This method is called by the Button event handlers. It is designed to receive, interpret, and pass
     * button press information to the entire system.
     * <p>
     * NOTE: This method currently only prints a message corresponding to which button was pressed, the
     * functionality of this info being passed to the socket API must be added
     *
     * @param button the button that was pressed is passed to this method from the event handler
     */
    private void defaultButonPressed(DefaultButton button) {

        if (button.getActive()) {
            button.defaultButtonPressed();
            send("MAIN|BUTTON|" + button.number);
        }
    }


    // Here, the local classes used by ScreenTesterApp are defined


    static class Row extends HBox {

        private int number;
        private DefaultButton leftButton;
        private DefaultButton rightButton;
        private DefaultLabel leftLabel;
        private DefaultLabel rightLabel;
        private DefaultLabel combinedLabel;

        private double height;
        private double width;
        private Font font;


        public Row (int number, double height, double width, Font font, List<DefaultButton> buttons) {

            // Sanity check
            if (buttons.size() != 2) {
                throw new RuntimeException();
            }


            // Firmly establish the size of each row
            setPrefSize(width, height);
            setMinSize(width, height);
            setMaxSize(width, height);


            this.number = number;
            this.height = height;
            this.width = width;
            this.font = font;
            this.leftButton = buttons.getFirst();
            this.rightButton = buttons.getLast();


            // This method call creates and assigns the three labels
            createLabels();

            // Starts with combined label added
            getChildren().addAll(leftButton,  combinedLabel, rightButton);
        }


        /**
         * This method used to update the individual rows of Screen. It is called by ScreenTesterApp when
         * a row needs to be updated.
         *
         * @param parts a part of the message sent to ScreenTesterApp
         */
        public void receiveMessage(List<String> parts) {

            if (parts.getFirst().equals("BUTTON")) {

                switch (parts.get(1)) {

                    case "LEFT":
                        if (parts.get(2).equals("ON")) {
                            leftButton.setActive(true);
                        }

                        else if (parts.get(2).equals("OFF")) {
                            leftButton.setActive(false);
                        }
                        else {
                            throw new RuntimeException("Unknown Button State: Not ON/OFF");
                        }

                        break;
                    case "RIGHT":
                        if (parts.get(2).equals("ON")) {
                            rightButton.setActive(true);
                        }

                        else if (parts.get(2).equals("OFF")) {
                            rightButton.setActive(false);
                        }
                        else {
                            throw new RuntimeException("Unknown Button State: Not ON/OFF");
                        }
                        break;
                    default:
                        throw new RuntimeException("Unknown Button: Not LEFT/RIGHT");
                }
            }

            else if (parts.getFirst().equals("LABEL")) {

                switch (parts.get(1)) {

                    case "LEFT":
                        leftLabel.setLabelText(parts.get(2),
                                Integer.parseInt(parts.get(3)), Integer.parseInt(parts.get(4)));
                        getChildren().clear();
                        getChildren().addAll(leftButton, leftLabel, rightLabel, rightButton);
                        break;
                    case "RIGHT":
                        rightLabel.setLabelText(parts.get(2),
                                Integer.parseInt(parts.get(3)), Integer.parseInt(parts.get(4)));
                        getChildren().clear();
                        getChildren().addAll(leftButton, leftLabel, rightLabel, rightButton);
                        break;
                    case "COMBINED":
                        combinedLabel.setLabelText(parts.get(2),
                                Integer.parseInt(parts.get(3)), Integer.parseInt(parts.get(4)));
                        getChildren().clear();
                        getChildren().addAll(leftButton, combinedLabel, rightButton);
                        break;
                    default:
                        throw new RuntimeException("Unknown Button: Not LEFT/RIGHT/COMBINED");
                }
            }

            else {
                throw new RuntimeException("Unknown Screen Part: Not BUTTON/LABEL");
            }
        }

        // This method is used to erase previous info from the Row before accepting new info
        public void clear() {

            leftButton.setActive(false);
            rightButton.setActive(false);
            leftLabel.clear();
            rightLabel.clear();
            combinedLabel.clear();
            getChildren().clear();
            getChildren().addAll(leftButton,  combinedLabel, rightButton);
        }

        // Creates the three Labels which will be used by Row
        private void createLabels () {

            // This assumes each of the two buttons are squares which have side length of
            // the row's height
            double labelWidth = width - (2 * height);
            Font labelFont = new Font(font.getName(), height/10);

            leftLabel = new DefaultLabel(labelWidth/2, height, "left");
            rightLabel = new DefaultLabel(labelWidth/2, height, "right");
            combinedLabel = new DefaultLabel(labelWidth, height, "combined");
        }
    }

    static class DefaultButton extends Button {

        private final int number;
        private double size;
        private Font font;
        private boolean active;

        // This might be unneeded
        private int timesPressed = 0;

        public DefaultButton(int number, double size, Font font) {

            this.number = number;
            this.font = font;
            active = false;

            setPrefSize(size, size);

            setFont(font);
        }


        public int getNumber() {
            return number;
        }

        public boolean getActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;

            if (active) {
                setText(String.valueOf(number));
            }
            else {
                setText("");
            }
        }

        public void defaultButtonPressed() {
            timesPressed++;
        }

    }

    static class DefaultLabel extends Label {

        private int width;
        private int height;
        private String alignment;

        private final List<Integer> sizes = new ArrayList<>();
        private final List<String> fontNames = new ArrayList<>();


        public DefaultLabel (double width, double height, String alignment) {

            this.width = (int) width;
            this.height = (int) height;

            setPrefSize(width, height);
            setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
            setBorder(Border.stroke(Color.GRAY));


            // Here, the sizes and fonts are set, all the fonts are currently Verdana
            sizes.add((int) height/10);
            sizes.add((int) height/5);
            sizes.add((int) height/2);
            fontNames.add("Verdana");
            fontNames.add("Verdana");
            fontNames.add("Verdana");


            switch (alignment) {
                case "left":
                    setAlignment(Pos.CENTER_LEFT);
                    break;
                case "right":
                    setAlignment(Pos.CENTER_RIGHT);
                    break;
                case "combined":
                    setAlignment(Pos.CENTER);
                    break;
                default:
                    throw new RuntimeException("Unknow Label: Not left/right/combined");
            }
        }


        /**
         * This method is used to update the Label's text.
         *
         * @param msg String to be displayed
         * @param size small, medium, large
         * @param font font1, font2, font3
         */
        public void setLabelText(String msg, int size, int font) {

            // TODO: Add handling for a message being too large for label

            setFont(new Font(fontNames.get(font), sizes.get(size)));
            setText(msg);
        }

        public void setDefaultText() {
            setLabelText("Hello World", 0, 0);
        }

        // Resets a label
        public void clear() {
            setText("");
        }
    }
}