package Screen;

import javafx.scene.control.Button;
import javafx.scene.text.Font;


/**
 * This class defines the Buttons which appear in the Gas Pump GUI.
 */
public class DefaultButton extends Button {

    private int number;
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