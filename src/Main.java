/**
 * This class creates the primary hub or "board" which drives the functionality
 * of the Gas Pump project
 *
 *
 */
import devices.FlowMeter;
import io.bus.DeviceManager;
import io.bus.DeviceManager.Entry;
import devices.Screen;
import devices.BinaryDevice;

import java.util.List;
import java.time.Duration;

// Main.java
import io.bus.DeviceManager;
import io.bus.DeviceLink;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        var entries = List.of(
                new DeviceManager.Entry("screen",     "127.0.0.1", 5001, "screen-01", "screen"),
                new DeviceManager.Entry("cardreader", "127.0.0.1", 5201, "cardr-01",  "cardreader"),
                new DeviceManager.Entry("cardserver", "127.0.0.1", 5301, "cards-01",  "cardserver")
        );

        try (DeviceManager dm = new DeviceManager(entries)) {
            DeviceLink screen     = dm.link("screen");
            DeviceLink cardReader = dm.link("cardreader");
            DeviceLink cardServer = dm.link("cardserver");

            // 1) Power-up → Screen READY → ALLOWPAYMENT
            System.out.println("[main] STATUS READY -> Screen");
            String allow = screen.request("SCREEN|STATUS|READY|None", Duration.ofSeconds(1));
            System.out.println("[main] Screen replied: " + allow);

            // 2) Draw welcome
            screen.request("SCREEN|DISPLAY|ROW1|\"Welcome\"", Duration.ofSeconds(1));
            screen.request("SCREEN|DISPLAY|ROW2|\"Please tap card\"", Duration.ofSeconds(1));

            // 3) Wait for tap (loop until CARDTAP appears)
            System.out.println("[main] Waiting for CARDTAP...");
            String tap;
            while (true) {
                tap = cardReader.request("CARDREADER|CHECK|EVENT|None", Duration.ofSeconds(1));
                if (tap.startsWith("MAIN|EVENT|CARDREADER|\"CARDTAP:")) break;
                Thread.sleep(250); // short pause before polling again
            }

            // 4) Parse the tap event
            int firstQuote = tap.indexOf('"');
            int lastQuote  = tap.lastIndexOf('"');
            String content = tap.substring(firstQuote + 1, lastQuote); // e.g., CARDTAP:4
            String cc      = content.split(":")[1];                   // e.g., "4"
            System.out.println("[main] Tap received with CC = " + cc);

            // 5) Ask CardServer (even=authYES)
            String auth = cardServer.request("CARDSERVER|AUTH|START|" + cc, Duration.ofSeconds(1));
            System.out.println("[main] " + auth);

            if (auth.contains("AUTH:YES")) {
                screen.request("SCREEN|DISPLAY|ROW1|\"Authorization Approved\"", Duration.ofSeconds(1));
                screen.request("SCREEN|DISPLAY|ROW2|\"Select fuel type...\"", Duration.ofSeconds(1));
            } else {
                screen.request("SCREEN|DISPLAY|ROW1|\"Card Declined\"", Duration.ofSeconds(1));
                screen.request("SCREEN|DISPLAY|ROW2|\"Please tap card\"", Duration.ofSeconds(1));
            }
        }
    }
}
