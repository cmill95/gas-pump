import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;

public class Display extends Application {

    private static final double SCENE_WIDTH = 500.0;
    private static final double SCENE_HEIGHT = 500.0;

    private Scene defaultScene;
    private Scene fillScene;

    @Override
    public void start(Stage stage) throws Exception {

        Font defaultFont = new Font("Verdana", 50);

        createDefaultScene(defaultFont);

        stage.setScene(defaultScene);
        stage.show();
    }

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

    private void defaultButonPressed (DefaultButton button) {

        button.defaultButtonPressed();
        System.out.println("Button " + button.getNumber() + " Was Pressed");
    }
}
