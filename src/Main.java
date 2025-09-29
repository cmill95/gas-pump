import io.bus.DeviceManager;
import io.bus.DeviceLink;

import java.io.IOException;
import java.util.List;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

    private static final class ScreenController {

        ScreenController(DeviceLink screen) {
            this.screen = screen;
        }
        private final DeviceLink screen;
        private String last;

        synchronized boolean show(String payload) throws IOException {
            if (Objects.equals(last, payload)) return false;
            screen.request("SCREEN|DISPLAY|MAIN|\"" + payload + "\"", java.time.Duration.ofSeconds(1));
            System.out.println("[screen] state -> " + payload);
            last = payload;
            return true;
        }

        synchronized void showWelcome() throws IOException { show("WELCOME"); }
    }

    // -----------------------------------------------------------------------

    private static final class AuthTimeouts {
        private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        private final ScreenController sc;
        private final Runnable resetSession;
        private ScheduledFuture<?> pending;
        private long seq = 0;

        AuthTimeouts(ScreenController sc, Runnable resetSession) {
            this.sc = sc;
            this.resetSession = resetSession;
        }

        synchronized void start(long delayMs, String label) {
            cancel("rearm:" + label);
            long id = ++seq;
            long firesAt = System.currentTimeMillis() + delayMs;
            System.out.println(String.format("⏲ start #%d %s delay=%dms firesAt=%tT", id, label, delayMs, firesAt));
            pending = ses.schedule(() -> {
                try {
                    System.out.println(String.format("⏲ fire  #%d %s", id, label));
                    resetSession.run();
                    sc.showWelcome();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    clear();
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }

        synchronized void cancel(String reason) {
            if (pending != null) {
                System.out.println("⏲ cancel " + reason);
                pending.cancel(false);
                pending = null;
            }
        }

        private synchronized void clear() { pending = null; }
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

            ScreenController sc = new ScreenController(screen);
            AuthTimeouts timeouts = new AuthTimeouts(sc, () -> {
            });

            while (true) {

                System.out.println("[main] STATUS READY -> Screen");
                String allow = screen.request("SCREEN|READY|MAIN|None", Duration.ofSeconds(1));
                System.out.println("[main] Screen replied: " + allow);
                sc.showWelcome();

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

                    sc.show("GRADE_MENU:" + menuCsv);
                    timeouts.start(INACTIVITY_MS, "post-auth idle");

                    final long deadline = System.currentTimeMillis() + INACTIVITY_MS;
                    String sel = null;
                    boolean timedOut = false;
                    while (true) {
                        sel = screen.request("SCREEN|CHECK|MAIN|None", java.time.Duration.ofSeconds(1));
                        if (sel != null && sel.startsWith("MAIN|EVENT|SCREEN|\"GRADE_SELECTED:")) break;
                        if (System.currentTimeMillis() > deadline) { timedOut = true; break; }
                        Thread.sleep(200);
                    }
                    if (timedOut) {
                        timeouts.cancel("manual-timeout");
                        sc.showWelcome();
                        continue;
                    }
                    timeouts.cancel("activity:grade_selected");

                    String payload = extractQuoted(sel);
                    String fuel    = afterColon(payload).trim();

                    sc.show("FUEL_SELECTED:" + fuel);

                    Thread.sleep(CONFIRM_MS);

                    sc.showWelcome();
                } else {

                    sc.show("AUTH_NO");
                    System.out.println("[main] Declined — dwell " + (DECLINE_DWELL_MS / 1000.0) + "s, then reset.");
                    Thread.sleep(DECLINE_DWELL_MS);
                }
            }
        }
    }
}