package ui;

import javafx.application.Application;
import javafx.stage.Stage;

import devices.Screen;
import io.bus.DeviceLink;

public class ScreenTesterApp extends Application {
    private Screen screen;

    @Override
    public void start(Stage stage) throws Exception {
        // Connect directly to SimDevices
        DeviceLink link = new DeviceLink("127.0.0.1", 5001, "screen-01");
        screen = new Screen(link);

    }

    private void send(String msg) {

    }
    public static void main(String[] args) { launch(args); }
}
