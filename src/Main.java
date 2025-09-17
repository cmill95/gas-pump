import io.bus.DeviceManager;
import io.bus.DeviceLink;
import java.util.List;
import java.time.Duration;

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

            final int INACTIVITY_MS   = 30_000; // 30 seconds
            final int DECLINE_DWELL_MS = 4_000; // 4 seconds

            while (true) {
                // Re-handshake each attempt so Screen is "allowing payment" again
                System.out.println("[main] STATUS READY -> Screen");
                String allow = screen.request("SCREEN|STATUS|READY|None", Duration.ofSeconds(1));
                System.out.println("[main] Screen replied: " + allow);

                // Show welcome screen (code-word state)
                screen.request("SCREEN|DISPLAY|MAIN|\"WELCOME\"", Duration.ofSeconds(1));

                // Wait for a tap
                System.out.println("[main] Waiting for CARDTAP...");
                String tap;
                while (true) {
                    tap = cardReader.request("CARDREADER|CHECK|EVENT|None", Duration.ofSeconds(1));
                    if (tap.startsWith("MAIN|EVENT|CARDREADER|\"CARDTAP:")) break;
                    Thread.sleep(250);
                }

                // Parse CC digit
                int q1 = tap.indexOf('"');
                int q2 = tap.lastIndexOf('"');
                String content = tap.substring(q1 + 1, q2);              // e.g., CARDTAP:4
                String cc = content.substring(content.indexOf(':') + 1); // "4"
                System.out.println("[main] Tap received with CC = " + cc);

                // Auth (even => YES; odd => NO)
                String auth = cardServer.request("CARDSERVER|AUTH|START|" + cc, Duration.ofSeconds(1));
                System.out.println("[main] " + auth);

                if (auth.contains("AUTH:YES")) {
                    screen.request("SCREEN|DISPLAY|MAIN|\"AUTH_OK\"", Duration.ofSeconds(1));

                    // Inactivity timeout: revert to WELCOME, then loop back
                    System.out.println("[main] Authorized. Waiting " + (INACTIVITY_MS / 1000) + "s...");
                    Thread.sleep(INACTIVITY_MS);
                    System.out.println("[main] Inactivity timeout -> WELCOME");
                    screen.request("SCREEN|DISPLAY|MAIN|\"WELCOME\"", Duration.ofSeconds(1));

                } else {
                    // Show Card Declined, let user see it, then loop back for another attempt
                    screen.request("SCREEN|DISPLAY|MAIN|\"AUTH_NO\"", Duration.ofSeconds(1));
                    System.out.println("[main] Declined — showing message for " + (DECLINE_DWELL_MS / 1000.0) + "s...");
                    Thread.sleep(DECLINE_DWELL_MS);
                    // loop continues: next iteration re-sends READY and WELCOME
                }
            }
        }
    }
}