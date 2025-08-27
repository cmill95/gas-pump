package Screen;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;


/**
 * This class drives the Screen component of the Gas Pump project using JavaFX. It displays
 * a GUI that represents the interface a customer would operate at the gas station.
 */

public class Display extends Application {


    private static final double SCENE_WIDTH = 500.0;
    private static final double SCENE_HEIGHT = 500.0;

    private Scene defaultScene;
    private Scene fillScene;

    @Override
    public void start(Stage stage) throws Exception {


        // TODO: allow for alternate fonts and sizes
        Font defaultFont = new Font("Verdana", 50);

        createDefaultScene(defaultFont);

        stage.setScene(defaultScene);
        stage.show();
    }


    /**
     * This method creates the default scene displayed on the GUI. It also creates the
     * buttons and labels displayed on the GUI
     *
     * NOTE: The order of the Buttons and the ability for Labels to be split into left, right and combined
     * must be addressed
     *
     * @param defaultFont a default font must be passed to this method which is disseminated to the Buttons
     *                    and the labels
     */
    private void createDefaultScene (Font defaultFont) {

        double buttonHeight = SCENE_HEIGHT/5;
        double buttonWidth = SCENE_WIDTH/5;
        double labelHeight = SCENE_HEIGHT/5;
        double labelWidth = SCENE_WIDTH - (buttonWidth * 2);


        GridPane gridPane = new GridPane(5,5);
        gridPane.setPrefSize(SCENE_WIDTH,SCENE_HEIGHT);
        gridPane.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

        List<DefaultButton> buttons = new ArrayList<>();
        for (int i=0; i < 10; i++) {

            // This assumes buttons are squares
            DefaultButton button = new DefaultButton(i, buttonWidth, defaultFont);
            button.setOnMouseClicked(e -> {
                defaultButonPressed(button);
            });
            buttons.add(button);
        }

        List<DefaultLabel> labels = new ArrayList<>();
        for (int i=0; i < 5; i++) {
            labels.add(new DefaultLabel(i, labelWidth, labelHeight, defaultFont));
        }

        // This setup is crude and relies on 15 elements on the Default Scene
        int counter = 0;
        for (int i=0; i < 3; i++) {
            for (int j=0; j < 5; j++) {

                if (counter < 5) {
                    gridPane.add(buttons.get(counter), i, j);
                }

                else if (counter < 10) {
                    gridPane.add(labels.get(counter-5), i, j);
                }

                else {

                    gridPane.add(buttons.get(counter-5), i, j);
                }
                counter ++;
            }
        }
        defaultScene = new Scene(gridPane);
    }

    /**
     * This method is called by the Button event handlers. It is designed to receive, interpret, and pass
     * button press information to the entire system.
     *
     * NOTE: This method currently only prints a message corresponding to which button was pressed, the
     * functionality of this info being passed to the socket API must be added
     *
     * @param button the button that was pressed is passed to this method from the event handler
     */
    private void defaultButonPressed (DefaultButton button) {

        button.defaultButtonPressed();
        System.out.println("Button " + button.getNumber() + " Was Pressed");
    }

    /**
     * This method creates the Fill scene which displays the status of the tank being filled to the GUI.
     *
     * NOTE: Functionality must be added
     *
     * @param defaultFont the default font is passed to this function
     */
    private void createFillScene (Font defaultFont) {

        //TODO: create Fill scene
    }
}
