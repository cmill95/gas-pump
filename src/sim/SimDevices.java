package sim;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class SimDevices {

    static volatile String screenState = "WELCOME";
    static volatile String pendingGrade = null;
    static volatile String pendingTap = null;
    public static void main(String[] args) throws Exception {
        int screenPort      = 5001;
        int screenCtrlPort  = 5021;
        int cardReaderPort  = 5201;
        int cardReaderCtl   = 5221;
        int cardServerPort  = 5301;
        int stationPort = 5401;

        chooseAvailable3();

        System.out.println("[sim] Started. Screen @" + screenPort +
                ", Screen-CTRL @" + screenCtrlPort +
                ", CardReader @" + cardReaderPort +
                ", CardServer @" + cardServerPort +
                ", CardReader-CTRL @" + cardReaderCtl +
                ", StationServer @" + stationPort);

        new Thread(() -> new ScreenServer("screen-01", screenPort).serve()).start();
        new Thread(() -> new ScreenControlServer("screen-ctrl", screenCtrlPort).serve()).start();
        new Thread(() -> new CardReaderServer("cardr-01", cardReaderPort).serve()).start();
        new Thread(() -> new CardReaderControlServer("cardr-ctrl", cardReaderCtl).serve()).start();
        new Thread(() -> new CardServer("cards-01", cardServerPort).serve()).start();
        new Thread(() -> new StationServer("station-01", stationPort).serve()).start();
    }

    // ─────────────── Base server ───────────────
    static abstract class SimServer {
        private final String deviceId;
        private final int port;

        SimServer(String deviceId, int port) { this.deviceId = deviceId; this.port = port; }

        void serve() {
            try (ServerSocket ss = new ServerSocket(port);
                 Socket s = ss.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {

                // Simple handshake
                in.readLine();
                out.write("HELLO " + deviceId + " v1\n");
                out.flush();

                String line;
                while ((line = in.readLine()) != null) {
                    String reply = handle(line.trim());
                    out.write(reply + "\n");
                    out.flush();
                }
            } catch (Exception e) {
                System.out.println("[sim] " + deviceName() + " stopped.");
            }
        }

        abstract String deviceName();
        abstract String handle(String line);
    }

    // ─────────────── Screen ───────────────
    static final class ScreenServer extends SimServer {
        ScreenServer(String id, int port) { super(id, port); }
        @Override String deviceName() { return "SCREEN"; }
        @Override String handle(String line) {
            if (line.equals("SCREEN|READY|MAIN|None")) {
                return "MAIN|REPLY|SCREEN|\"ALLOWPAYMENT\"";
            }
            if (line.equals("SCREEN|DISPLAY|MAIN|\"WELCOME\"")) {
                screenState = "WELCOME";
                pendingGrade = null;      // 🔧 FIX: drop any stale selection
                System.out.println("[sim] SCREEN state -> " + screenState + " (pendingGrade cleared)");
                return "MAIN|REPLY|SCREEN|\"OK\"";
            }
            if (line.startsWith("SCREEN|DISPLAY|MAIN|")) {
                int q1 = line.indexOf('"');
                int q2 = line.lastIndexOf('"');
                String state = (q1 >= 0 && q2 > q1) ? line.substring(q1 + 1, q2) : "";
                screenState = state;
                System.out.println("[sim] SCREEN state -> " + screenState);
                return "MAIN|REPLY|SCREEN|\"OK\"";
            }
            if (line.equals("SCREEN|CHECK|MAIN|None")) {
                if (pendingGrade != null) {
                    String g = pendingGrade; pendingGrade = null;
                    String ev = "MAIN|EVENT|SCREEN|\"GRADE_SELECTED:" + g + "\"";
                    System.out.println("[sim] SCREEN -> " + ev);
                    return ev;
                }
                return "MAIN|REPLY|SCREEN|\"NONE\"";
            }
            return "MAIN|REPLY|SCREEN|\"OK\"";
        }
    }

    // ─────────────── Screen Control (GUI) ───────────────
    static final class ScreenControlServer extends SimServer {
        ScreenControlServer(String id, int port) { super(id, port); }
        @Override String deviceName() { return "SCREEN-CTRL"; }
        @Override String handle(String line) {

            if (line.equals("SCREEN|GETSTATE|MAIN|None")) {
                return "MAIN|REPLY|SCREEN|\"STATE:" + screenState + "\"";
            }
            if (line.startsWith("SCREEN|DEVCTL|MAIN|")) {
                String fuel = line.substring("SCREEN|DEVCTL|MAIN|".length()).trim();
                pendingGrade = fuel;
                System.out.println("[sim] SCREEN-CTRL queued selection: " + fuel);
                return "MAIN|REPLY|SCREEN|\"OK\"";
            }
            return "MAIN|REPLY|SCREEN|\"OK\"";
        }
    }

    // ─────────────── CardReader ───────────────

    static final class CardReaderServer extends SimServer {
        CardReaderServer(String id, int port) { super(id, port); }
        @Override String deviceName() { return "CARDREADER"; }
        @Override String handle(String line) {
            if (line.equals("CARDREADER|CHECK|MAIN|None")) {
                if (pendingTap != null) {
                    String cc = pendingTap;
                    pendingTap = null;
                    return "MAIN|EVENT|CARDREADER|\"CARDTAP:" + cc + "\"";
                }
                return "MAIN|REPLY|CARDREADER|\"NONE\"";
            }
            return "MAIN|REPLY|CARDREADER|\"OK\"";
        }
    }

    // ─────────────── CardReader Control (GUI) ───────────────
    static final class CardReaderControlServer extends SimServer {
        private final Random rnd = new Random();
        CardReaderControlServer(String id, int port) { super(id, port); }
        @Override String deviceName() { return "CARDREADER-CTRL"; }
        @Override String handle(String line) {
            if (line.startsWith("CARDREADER|DEVCTL|MAIN|")) {
                String[] parts = line.split("\\|", 4);
                String cc = (parts.length == 4 && !parts[3].isBlank()) ? parts[3] : Integer.toString(rnd.nextInt(10));
                pendingTap = cc;
                System.out.println("[sim] CardReader-CTRL queued tap: " + cc);
                return "MAIN|REPLY|CARDREADER|\"OK\"";
            }
            return "MAIN|REPLY|CARDREADER|\"OK\"";
        }
    }

    // ─────────────── CardServer ───────────────
    static final class CardServer extends SimServer {
        CardServer(String id, int port) { super(id, port); }
        @Override String deviceName() { return "CARDSERVER"; }
        @Override String handle(String line) {
            if (line.startsWith("CARDSERVER|AUTH|MAIN|")) {
                String cc = line.substring("CARDSERVER|AUTH|MAIN|".length());
                boolean ok = Integer.parseInt(cc) % 2 == 0;
                return ok ? "MAIN|REPLY|CARDSERVER|\"AUTH:YES\"" : "MAIN|REPLY|CARDSERVER|\"AUTH:NO\"";
            }
            return "MAIN|REPLY|CARDSERVER|\"OK\"";
        }
    }

    static final class Fuel {
        final String name; final double price;
        Fuel(String n, double p) { name = n; price = p; }
    }

    static final Fuel[] ALL_FUELS = new Fuel[] {
            new Fuel("Regular", 3.49),
            new Fuel("Plus",    3.79),
            new Fuel("Premium", 4.09),
            new Fuel("Diesel",  3.99),
            new Fuel("E85",     2.89)
    };

    static volatile Fuel[] AVAILABLE3;

    // --- StationServer (provides available fuels and prices) ---
    static final class StationServer extends SimServer {
        private final java.util.Random rnd = new java.util.Random();

        StationServer(String id, int port) { super(id, port); }

        @Override String deviceName() { return "STATIONSERVER"; }

        @Override String handle(String line) {
            if (line.equals("STATIONSERVER|LIST|MAIN|None")) {
                StringBuilder sb = new StringBuilder("MAIN|REPLY|STATIONSERVER|\"LIST:");
                for (int i = 0; i < AVAILABLE3.length; i++) {
                    Fuel f = AVAILABLE3[i];
                    sb.append(f.name).append("=").append(String.format(java.util.Locale.US, "%.2f", f.price));
                    if (i < AVAILABLE3.length - 1) sb.append(",");
                }
                sb.append("\"");
                return sb.toString();
            }

            if (line.startsWith("STATIONSERVER|GETPRICE|MAIN|")) {
                String fuelName = line.substring("STATIONSERVER|GETPRICE|MAIN|".length()).trim();
                for (Fuel f : ALL_FUELS) {
                    if (f.name.equalsIgnoreCase(fuelName)) {
                        return "MAIN|REPLY|STATIONSERVER|\"PRICE:" +
                                String.format(java.util.Locale.US, "%.2f", f.price) + "\"";
                    }
                }
                return "MAIN|REPLY|STATIONSERVER|\"ERR:NO_SUCH_FUEL\"";
            }

            if (line.equals("STATIONSERVER|LISTREROLL|MAIN|None")) {
                chooseAvailable3();
                return "MAIN|REPLY|STATIONSERVER|\"OK\"";
            }

            if (line.equals("SCREEN|DISPLAY|MAIN|\"FUEL_SELECTED\"")) {
                System.out.println("[sim] SCREEN <= FUEL_SELECTED");
                return "MAIN|REPLY|SCREEN|\"OK\"";
            }

            return "MAIN|REPLY|STATIONSERVER|\"OK\"";
        }
    }

    static void chooseAvailable3() {
        java.util.List<Fuel> pool = new java.util.ArrayList<>(java.util.Arrays.asList(ALL_FUELS));
        java.util.Collections.shuffle(pool);
        AVAILABLE3 = new Fuel[] { pool.get(0), pool.get(1), pool.get(2) };
    }
}

    // --- Binary device: supports GET and SET 0/1 ---
    //static final class SimHoseDevice extends SimServer {
        //private boolean state = false;

        //SimHoseDevice(String deviceId, int port) { super(deviceId, port); }

        //@Override
        //protected String handle(String line) {
            //if (line.equals("GET")) {
                //return "OK " + (state ? "1" : "0");
            //}
            //if (line.startsWith("SET ")) {
                //String v = line.substring(4).trim();
                //if ("1".equals(v)) { state = true;  System.out.println(tag() + "SET 1"); return "OK"; }
                //if ("0".equals(v)) { state = false; System.out.println(tag() + "SET 0"); return "OK"; }
                //return "ERR 400 bad-value";
            //}
            //System.out.println(tag() + "unknown cmd: " + line);
            //return "ERR 400 unknown-command";
        //}
    //}

    //static final class SimFlowMeter extends SimDeviceBase {

    //}
