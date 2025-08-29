/**
 * This class creates the primary hub or "board" which drives the functionality
 * of the Gas Pump project
 *
 *
 */
import io.bus.DeviceManager;
import io.bus.DeviceManager.Entry;
import devices.Screen;
import devices.BinaryDevice;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        // Device table — different port per device
        List<Entry> table = List.of(
                new Entry("ui-screen",   "127.0.0.1", 5001, "screen-01", "screen"),
                new Entry("main-valve",  "127.0.0.1", 5101, "valve-01",  "binary"),
                new Entry("magnet",      "127.0.0.1", 5301, "mag-01",    "binary")
                // add: flow-meter, NFC, etc.
        );

        try (DeviceManager dm = new DeviceManager(table)) {
            Screen ui = dm.screen("ui-screen");
            BinaryDevice valve = dm.binary("main-valve");

            ui.clear();
            ui.printLine("Welcome");
            valve.set(true);      // OPEN
            Thread.sleep(250);
            valve.set(false);     // CLOSE
        }
    }
}
