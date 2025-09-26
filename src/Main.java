import io.bus.DeviceManager;
import io.bus.DeviceLink;
import java.util.List;
import java.time.Duration;

public class Main {

    // ---- Safe helpers ------------------------------------------------------
    private static String extractQuoted(String s) {
        if (s == null) return "";
        int q1 = s.indexOf('"');
        if (q1 < 0) return "";
        int q2 = s.indexOf('"', q1 + 1);
        if (q2 < 0) return "";
        return s.substring(q1 + 1, q2);
    }

    private static String afterColon(String s) {
        if (s == null) return "";
        int i = s.indexOf(':');
        return (i >= 0 && i + 1 < s.length()) ? s.substring(i + 1) : "";
    }
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        var entries = List.of(
                new DeviceManager.Entry("screen",        "127.0.0.1", 5001, "screen-01",  "screen"),
                new DeviceManager.Entry("cardreader",    "127.0.0.1", 5201, "cardr-01",   "cardreader"),
                new DeviceManager.Entry("cardserver",    "127.0.0.1", 5301, "cards-01",   "cardserver"),
                new DeviceManager.Entry("stationserver", "127.0.0.1", 5401, "station-01", "stationserver")
        );

        final int INACTIVITY_MS     = 30_000;
        final int DECLINE_DWELL_MS  = 2_000;
        final int CONFIRM_MS    = 10_000;

        try (DeviceManager dm = new DeviceManager(entries)) {
            DeviceLink screen   = dm.link("screen");
            DeviceLink reader   = dm.link("cardreader");
            DeviceLink cardSrv  = dm.link("cardserver");
            DeviceLink station  = dm.link("stationserver");

            while (true) {

                System.out.println("[main] STATUS READY -> Screen");
                String allow = screen.request("SCREEN|READY|MAIN|None", Duration.ofSeconds(1));
                System.out.println("[main] Screen replied: " + allow);
                screen.request("SCREEN|DISPLAY|MAIN|\"WELCOME\"", Duration.ofSeconds(1));

                System.out.println("[main] Waiting for CARD_TAP...");
                String tap;
                while (true) {
                    tap = reader.request("CARDREADER|CHECK|MAIN|None", Duration.ofSeconds(1));
                    if (tap != null && tap.startsWith("MAIN|EVENT|CARDREADER|\"CARDTAP:")) break;
                    Thread.sleep(250);
                }
                System.out.println("[main] Tap raw: " + tap);

                String tapPayload = extractQuoted(tap);
                String cc         = afterColon(tapPayload).trim();
                System.out.println("[main] CC digit = " + cc);

                String auth = cardSrv.request("CARDSERVER|AUTH|MAIN|" + cc, Duration.ofSeconds(1));
                System.out.println("[main] " + auth);

                if (auth != null && auth.contains("AUTH:YES")) {

                    String list = station.request("STATIONSERVER|LIST|MAIN|None", java.time.Duration.ofSeconds(1));
                    String listPayload = extractQuoted(list);
                    String menuCsv     = afterColon(listPayload);

                    screen.request("SCREEN|DISPLAY|MAIN|\"GRADE_MENU:" + menuCsv + "\"", java.time.Duration.ofSeconds(1));

                    final long deadline = System.currentTimeMillis() + 30_000;
                    String sel;
                    while (true) {
                        sel = screen.request("SCREEN|CHECK|MAIN|None", java.time.Duration.ofSeconds(1));
                        if (sel != null && sel.startsWith("MAIN|EVENT|SCREEN|\"GRADE_SELECTED:")) break;
                        if (System.currentTimeMillis() > deadline) {

                            screen.request("SCREEN|DISPLAY|MAIN|\"WELCOME\"", java.time.Duration.ofSeconds(1));
                            continue;
                        }
                        Thread.sleep(200);
                    }

                    String payload = extractQuoted(sel);
                    String fuel    = afterColon(payload).trim();

                    screen.request("SCREEN|DISPLAY|MAIN|\"FUEL_SELECTED:" + fuel + "\"", java.time.Duration.ofSeconds(1));

                    Thread.sleep(CONFIRM_MS);

                    screen.request("SCREEN|DISPLAY|MAIN|\"WELCOME\"", java.time.Duration.ofSeconds(1));
                } else {

                    screen.request("SCREEN|DISPLAY|MAIN|\"AUTH_NO\"", Duration.ofSeconds(1));
                    System.out.println("[main] Declined — dwell " + (DECLINE_DWELL_MS / 1000.0) + "s, then reset.");
                    Thread.sleep(DECLINE_DWELL_MS);
                }
            }
        }
    }
}