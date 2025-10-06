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
    private static double parseField(String payload, String key) {
        try {
            int i = payload.indexOf(key + ":");
            if (i < 0) return 0.0;
            int start = i + key.length() + 1;
            int end = payload.indexOf(',', start);
            String num = (end < 0) ? payload.substring(start) : payload.substring(start, end);
            return Double.parseDouble(num.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static void abortWithError(ScreenController sc, DeviceLink hose, String code) throws Exception {
        try { hose.request("HOSE|STOP|MAIN|None", Duration.ofSeconds(1)); } catch (Exception ignore) {}
        sc.show("ERROR:" + code);
        Thread.sleep(2000); // dwell on ERROR
        sc.showWelcome();
    }

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

        synchronized void show(String payload) throws IOException {
            if (Objects.equals(last, payload)) return;
            screen.request("SCREEN|DISPLAY|MAIN|\"" + payload + "\"", Duration.ofSeconds(1));
            System.out.println("[screen] state -> " + payload);
            last = payload;
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

        synchronized void start(String label) {
            cancel("rearm:" + label);
            long id = ++seq;
            long firesAt = System.currentTimeMillis() + (long) 30000;
            System.out.printf("-> start #%d %s delay=%dms firesAt=%tT%n", id, label, (long) 30000, firesAt);
            pending = ses.schedule(() -> {
                try {
                    System.out.printf("-> fire  #%d %s%n", id, label);
                    resetSession.run();
                    sc.showWelcome();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    clear();
                }
            }, 30000, TimeUnit.MILLISECONDS);
        }

        synchronized void cancel(String reason) {
            if (pending != null) {
                System.out.println("-> cancel " + reason);
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
                new DeviceManager.Entry("stationserver", "127.0.0.1", 5401, "station-01", "stationserver"),
                new DeviceManager.Entry("hose",          "127.0.0.1", 5101, "hose-01",    "hose"),
                new DeviceManager.Entry("pump", "127.0.0.1", 5501, "pump-01", "pump"),
                new DeviceManager.Entry("flowmeter", "127.0.0.1", 5601, "flowmeter-01", "flowmeter")
        );

        final int INACTIVITY_MS     = 30_000;
        final int DECLINE_DWELL_MS  = 2_000;
        final int DETACH_TIMEOUT_MS = 30_000;
        final int THANK_YOU_DWELL_MS = 5_000;

        try (DeviceManager dm = new DeviceManager(entries)) {
            DeviceLink screen   = dm.link("screen");
            DeviceLink reader   = dm.link("cardreader");
            DeviceLink cardSrv  = dm.link("cardserver");
            DeviceLink station  = dm.link("stationserver");
            DeviceLink hose     = dm.link("hose");
            DeviceLink pump = dm.link("pump");
            DeviceLink flowmeter = dm.link("flowmeter");

            ScreenController sc = new ScreenController(screen);
            AuthTimeouts timeouts = new AuthTimeouts(sc, () -> {
            });

            while (true) {

                System.out.println("[main] initializing Screen...");
                String allow = screen.request("SCREEN|READY|MAIN|None", Duration.ofSeconds(1));
                System.out.println("[screen] replied: " + allow);
                System.out.println("[main] we are ready for payment, show WELCOME screen");
                sc.showWelcome();

                System.out.println("[main] Waiting for CARD_TAP...");
                String tap;
                while (true) {
                    tap = reader.request("CARDREADER|CHECK|MAIN|None", Duration.ofSeconds(1));
                    if (tap.startsWith("MAIN|EVENT|CARDREADER|\"CARDTAP:")) break;
                    Thread.sleep(250);
                }
                System.out.println("[main] Tap raw: " + tap);

                String tapPayload = extractQuoted(tap);
                String cc         = afterColon(tapPayload).trim();
                System.out.println("[main] CC digit = " + cc);

                int ccVal;

                try {
                    ccVal = Integer.parseInt(cc);
                } catch (NumberFormatException ex) {
                    abortWithError(sc, hose, "BAD_TAP");
                    continue;
                }
                //ccVal = -1; // ERROR TESTING
                if (ccVal < 0) {
                    abortWithError(sc, hose, "NEG_TAP");
                    continue;
                }

                String auth = cardSrv.request("CARDSERVER|AUTH|MAIN|" + cc, Duration.ofSeconds(1));
                System.out.println("[main] " + auth);

                if (auth.contains("AUTH:YES")) {

                    String list = station.request("STATIONSERVER|LIST|MAIN|None", Duration.ofSeconds(1));
                    String listPayload = extractQuoted(list);
                    String menuCsv     = afterColon(listPayload);

                    sc.show("GRADE_MENU:" + menuCsv);
                    timeouts.start("post-auth idle");

                    final long deadline = System.currentTimeMillis() + INACTIVITY_MS;
                    String sel;
                    boolean timedOut = false;
                    while (true) {
                        sel = screen.request("SCREEN|CHECK|MAIN|None", java.time.Duration.ofSeconds(1));
                        if (sel.startsWith("MAIN|EVENT|SCREEN|\"GRADE_SELECTED:")) break;
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

                    sc.show("ATTACH_HOSE");
                    timeouts.start("attach-hose idle");

                    while (true) {
                        String r = hose.request("HOSE|GET|MAIN|None", Duration.ofSeconds(1));
                        String state = afterColon(extractQuoted(r)).trim();
                        if ("1".equals(state)) break;
                        sc.show("ATTACH_HOSE");
                        Thread.sleep(200);
                    }

                    timeouts.cancel("activity:hose_attached");

                    hose.request("HOSE|START|MAIN|None", Duration.ofSeconds(1));
                    sc.show("FUELING");

                    String hs = hose.request("HOSE|STATUS|MAIN|None", Duration.ofSeconds(1));
                    String hsp = extractQuoted(hs); // e.g., STATE:1,ARMED:1,FULL:0,CAP:15.000,CUR:3.200

                    double capGal = parseField(hsp, "CAP");
                    double curGal = parseField(hsp, "CUR");
                    double remainingTargetGal = Math.max(0.0, capGal - curGal); // how many gallons *this* session can deliver

                    //capGal = -1; // ERROR TESTING
                    //curGal = -1; // ERROR TESTING
                    //curGal = 10; capGal = 20; // ERROR TESTING

                    if (capGal < 0 || curGal < 0) {
                        abortWithError(sc, hose, "BAD_TANK");
                        continue;
                    }
                    if (curGal > capGal) {
                        abortWithError(sc, hose, "OVERFILL");
                        continue;
                    }

                    String pr = station.request("STATIONSERVER|GETPRICE|MAIN|" + fuel, Duration.ofSeconds(1));
                    double pricePerGal = parseField(extractQuoted(pr), "PRICE");

                    //pricePerGal = -1; // ERROR TESTING
                    //pricePerGal = 0; // ERROR TESTING

                    if (pricePerGal < 0) {
                        abortWithError(sc, hose, "NEG_PRICE");
                        continue;
                    }
                    if (pricePerGal == 0 || pricePerGal < 0.01) {
                        abortWithError(sc, hose, "BAD_PRICE");
                        continue;
                    }

                    int kEst = (capGal > 0.0) ? (int)Math.floor((curGal / capGal) * 11.0) : 0;
                    kEst = Math.max(0, Math.min(10, kEst));
                    int framesRemaining = Math.max(1, 10 - kEst);           // seconds HoseGUI needs (1 frame/sec)
                    double gps = remainingTargetGal / framesRemaining;

                    long detachDeadline = Long.MAX_VALUE;

                    long lastTick = System.currentTimeMillis();
                    double dispensedGal = 0.0;

                    while (true) {
                        String rs = hose.request("HOSE|STATUS|MAIN|None", Duration.ofSeconds(1));
                        String statusPayload = extractQuoted(rs);
                        boolean isAttached = statusPayload.contains("STATE:1");
                        boolean isArmed    = statusPayload.contains("ARMED:1");
                        boolean isFull     = statusPayload.contains("FULL:1");

                        if (!isArmed) {
                            sc.showWelcome();
                            break;
                        }

                        long now = System.currentTimeMillis();
                        double dt = (now - lastTick) / 1000.0;
                        lastTick = now;

                        if (isAttached && !isFull) {

                            dispensedGal += gps * dt;

                            //dispensedGal = -1; // ERROR TESTING

                            if (dispensedGal < 0) {
                                abortWithError(sc, hose, "NEG_GAL");
                                break;
                            }

                            if (dispensedGal >= remainingTargetGal) {
                                dispensedGal = remainingTargetGal;
                                // We hit the computed target; treat as complete.
                                isFull = true;
                            }
                        }

                        double galShown = Math.min(dispensedGal, remainingTargetGal);
                        double usdShown = galShown * pricePerGal;
                        String galsFmt = String.format(java.util.Locale.US, "%.3f", galShown);
                        String usdFmt  = String.format(java.util.Locale.US, "%.2f", usdShown);
                        sc.show("FUELING_NUM:" + galsFmt + "," + usdFmt);

                        try {
                            // S:1 while fueling, S:0 otherwise
                            flowmeter.request(
                                    String.format(java.util.Locale.US,
                                            "FLOWMETER|UPDATE|MAIN|G:%.3f,S:%d", galShown, (isAttached && !isFull) ? 1 : 0),
                                    Duration.ofMillis(500));
                        } catch (Exception ignore) {}

                        if (isFull) {
                            // Compute/Clamp the final numbers you want to show
                            double finalGallons = Math.min(dispensedGal, remainingTargetGal); // or whatever your accumulator is named
                            double finalDollars = finalGallons * pricePerGal;

                            galsFmt = String.format(java.util.Locale.US, "%.3f", finalGallons);
                            usdFmt  = String.format(java.util.Locale.US, "%.2f",  finalDollars);

                            // 1) Show THANK_YOU with the final numbers
                            sc.show("THANK_YOU_NUM:" + galsFmt + "," + usdFmt);

                            // 2) Stop devices
                            hose.request("HOSE|STOP|MAIN|None", Duration.ofSeconds(1));

                            // 3) Linger 5s on the receipt-style screen
                            Thread.sleep(THANK_YOU_DWELL_MS);

                            try {
                                flowmeter.request(
                                        String.format(java.util.Locale.US,
                                                "FLOWMETER|UPDATE|MAIN|G:%.3f,S:0", finalGallons),
                                        Duration.ofMillis(500));
                            } catch (Exception ignore) {}

                            // 4) Back to welcome
                            sc.showWelcome();
                            break;
                        }

                        if (!isAttached) {
                            if (detachDeadline == Long.MAX_VALUE) {
                                detachDeadline = System.currentTimeMillis() + DETACH_TIMEOUT_MS;
                            }
                            if (System.currentTimeMillis() > detachDeadline) {
                                hose.request("HOSE|STOP|MAIN|None", Duration.ofSeconds(1));
                                sc.showWelcome();
                                break;
                            }
                        } else {
                            detachDeadline = Long.MAX_VALUE; // resume window
                        }

                        Thread.sleep(200);
                    }

                } else {
                    sc.show("AUTH_NO");
                    System.out.println("[main] Declined - dwell " + (DECLINE_DWELL_MS / 1000.0) + "s, then reset.");
                    Thread.sleep(DECLINE_DWELL_MS);
                }
            }
        }
    }
}