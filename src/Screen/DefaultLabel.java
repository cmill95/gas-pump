package Screen;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;


/**
 * This class creates the labels which are displayed in the Gas Pump GUI.
 */
public class DefaultLabel extends Label {

    private int number;
    private int width;
    private int height;
    private Font font;


    public DefaultLabel (int number, double width, double height, Font font) {

        this.number = number;
        this.width = (int) width;
        this.height = (int) height;
        this.font = font;

        setPrefSize(width, height);

        setFont(font);
        setText("Label " + (number + 1));
        setAlignment(Pos.CENTER);
        setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
    }
}