package ui;

import devices.FlowMeter;
import io.bus.DeviceLink;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.swing.text.Position;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;

public class FlowMeterGUI extends Application {

    private double SCENE_HEIGHT;
    private double SCENE_WIDTH;
    private boolean flowing;
    private double volume;
    private Scene scene;
    private StackPane pane;
    private List<ImageView> images = new ArrayList<>();

    private final double screenMultiplier = 0.5;

    @Override
    public void start(Stage stage) throws Exception {

//        DeviceLink link = new DeviceLink("127.0.0.1", 5401, "flow-meter");
//        FlowMeter fm = new FlowMeter(link);

//        flowing = fm.getState().running;
//        volume = fm.getState().gallons;

        flowing = true;
        volume = 10.17;


        // Get the monitor size to set scene size
        Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        System.out.println(screenBounds.getHeight());
        System.out.println(screenBounds.getWidth());

        Text text = new Text();
        text.setText(volume + " gallons");

        SCENE_WIDTH = screenBounds.getHeight() * screenMultiplier;
        SCENE_HEIGHT = SCENE_WIDTH;


        // Filler rect doesn't work with StackPane

//        Rectangle rect = new Rectangle(SCENE_WIDTH,SCENE_HEIGHT/4);
//        rect.setOpacity(0);

        pane = new StackPane();
        pane.setPrefSize(SCENE_WIDTH, SCENE_HEIGHT);
        pane.setMaxSize(SCENE_WIDTH, SCENE_HEIGHT);


        double size = SCENE_HEIGHT;
        images.add(new ImageView(new Image("file:resources/images/FM-1-1.png")));
        images.add(new ImageView(new Image("file:resources/images/FM-1-2.png")));
        images.add(new ImageView(new Image("file:resources/images/FM-0-1.png")));

        for (ImageView image : images) {
            image.setFitWidth(size);
            image.setFitHeight(size);
        }

        pane.getChildren().addAll(images.get(2), text);
        pane.setPrefSize(SCENE_WIDTH, SCENE_HEIGHT);
        pane.setMaxSize(SCENE_WIDTH, SCENE_HEIGHT);
        pane.setAlignment(Pos.TOP_CENTER);

        scene = new Scene(pane);


        stage.getIcons().add(new Image("file:resources/icon.png"));
        stage.setTitle("FlowMeter");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();

        // Toggle between two images every 1 second while 'flowing' is true
        final IntegerProperty idx = new SimpleIntegerProperty(1);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(.1), e -> {
                    if (flowing && idx.get() == 0) {
                        idx.set(1); // flip 0↔1
                        pane.getChildren().setAll(images.get(idx.get()));
                    }
                    else if (flowing && idx.get() == 1) {

                        idx.set(0); // flip 0↔1
                        pane.getChildren().setAll(images.get(idx.get()));
                    }
                })
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        stage.show();
    }
}
