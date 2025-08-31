package ui;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import devices.Screen;
import io.bus.DeviceLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenTesterApp extends Application {
    private Screen screen;

    private double SCENE_WIDTH = 500.0;
    private double SCENE_HEIGHT = 500.0;

    private Scene defaultScene;
    private Scene fillScene;

    @Override
    public void start(Stage stage) throws Exception {
        // Connect directly to SimDevices
//        DeviceLink link = new DeviceLink("127.0.0.1", 5001, "screen-01");
//        screen = new Screen(link);

        // TODO: Add multiple fonts
        Font defaultFont = new Font("Verdana", 50);

        createDefaultScene(defaultFont);
        // TODO: Create and Add Fill Scene

        //TODO: Add ability for resizing

        stage.setResizable(true);



        stage.setScene(defaultScene);
        stage.show();
    }


    private void send(String msg) {

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



        defaultScene = new Scene(vBox);
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

        button.defaultButtonPressed();
        System.out.println("Button " + button.getNumber() + " Was Pressed");
    }

    /**
     * This method creates the Fill scene which displays the status of the tank being filled to the GUI.
     * NOTE: Functionality must be added
     *
     * @param defaultFont the default font is passed to this function
     */
    private void createFillScene(Font defaultFont) {

        //TODO: create Fill scene
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

            Random rand = new Random();

            if (rand.nextDouble() >= 0.5) {
                getChildren().addAll(leftButton,  combinedLabel, rightButton);
            }
            else {
                getChildren().addAll(leftButton,  leftLabel, rightLabel, rightButton);

            }

//            // In the beginning, the buttons and combined labels are added to the row
//            getChildren().addAll(leftButton,  combinedLabel, rightButton);
//
//            // - VS. the two separated labels
//            getChildren().addAll(leftButton,  leftLabel, rightLabel, rightButton);
        }



        private void createLabels () {

            // This assumes each of the two buttons are squares which have side length of
            // the row's height
            double labelWidth = width - (2 * height);
            Font labelFont = new Font(font.getName(), 10);

            leftLabel = new DefaultLabel(labelWidth/2, height, labelFont, "left");
            rightLabel = new DefaultLabel(labelWidth/2, height, labelFont, "right");
            combinedLabel = new DefaultLabel(labelWidth, height, labelFont, "center");
        }
    }

    static class DefaultButton extends Button {

        private final int number;
        private double size;
        private Font font;

        // This might be unneeded
        private int timesPressed = 0;

        public DefaultButton(int number, double size, Font font) {

            this.number = number;
//        this.size = size;
            this.font = font;

            setPrefSize(size, size);

            setFont(font);
            setText(String.valueOf(number));
//        setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        }

        public int getNumber() {
            return number;
        }

        public void defaultButtonPressed() {
            timesPressed++;
        }
    }

    static class DefaultLabel extends Label {

        private int width;
        private int height;
        private Font font;
        private String alignment;


        public DefaultLabel (double width, double height, Font font, String alignment) {

            this.width = (int) width;
            this.height = (int) height;
            this.font = font;

            setPrefSize(width, height);

            setFont(font);
            setText("Hello World!");
            setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
            setBorder(Border.stroke(Color.GRAY));


            switch (alignment) {
                case "left":
                    setAlignment(Pos.CENTER_LEFT);
                    break;
                case "right":
                    setAlignment(Pos.CENTER_RIGHT);
                    break;
                case "center":
                    setAlignment(Pos.CENTER);
                    break;
                default:
                    System.out.println("Unknown Label Position");
                    throw new RuntimeException();
            }
        }
    }
}