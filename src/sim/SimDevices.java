// SimDevices.java
package sim;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class SimDevices {

    static volatile String screenState = "WELCOME";
    public static void main(String[] args) throws Exception {
        int screenPort      = 5001;
        int screenCtrlPort  = 5021;
        int cardReaderPort  = 5201;
        int cardReaderCtl   = 5221;
        int cardServerPort  = 5301;

        System.out.println("[sim] Started. Screen @" + screenPort +
                ", Screen-CTRL @" + screenCtrlPort +
                ", CardReader @" + cardReaderPort +
                ", CardServer @" + cardServerPort +
                ", CardReader-CTRL @" + cardReaderCtl);

        new Thread(() -> new ScreenServer("screen-01", screenPort).serve()).start();
        new Thread(() -> new ScreenControlServer("screen-ctrl", screenCtrlPort).serve()).start();
        new Thread(() -> new CardReaderServer("cardr-01", cardReaderPort).serve()).start();
        new Thread(() -> new CardReaderControlServer("cardr-ctrl", cardReaderCtl).serve()).start();
        new Thread(() -> new CardServer("cards-01", cardServerPort).serve()).start();
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
            if (line.equals("SCREEN|STATUS|READY|None")) {
                return "MAIN|REPLY|SCREEN|\"ALLOWPAYMENT\"";
            }
            if (line.startsWith("SCREEN|DISPLAY|MAIN|")) {
                int q1 = line.indexOf('"');
                int q2 = line.lastIndexOf('"');
                String state = (q1 >= 0 && q2 > q1) ? line.substring(q1 + 1, q2) : "";
                screenState = state; // <-- store the code-word state
                System.out.println("[sim] SCREEN state -> " + screenState);
                return "MAIN|REPLY|SCREEN|\"OK\"";
            }
            return "MAIN|REPLY|SCREEN|\"OK\"";
        }
    }

    // ─────────────── Screen Control (GUI) ───────────────
    static final class ScreenControlServer extends SimServer {
        ScreenControlServer(String id, int port) { super(id, port); }
        @Override String deviceName() { return "SCREEN-CTRL"; }
        @Override String handle(String line) {
            // GUI polls current state
            if (line.equals("SCREEN|GET|STATE|None")) {
                return "MAIN|REPLY|SCREEN|\"STATE:" + screenState + "\"";
            }
            return "MAIN|REPLY|SCREEN|\"OK\"";
        }
    }

    // ─────────────── CardReader ───────────────
    static String pendingTap = null;

    static final class CardReaderServer extends SimServer {
        CardReaderServer(String id, int port) { super(id, port); }
        @Override String deviceName() { return "CARDREADER"; }
        @Override String handle(String line) {
            if (line.equals("CARDREADER|CHECK|EVENT|None")) {
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
            if (line.startsWith("CARDREADER|DEVCTL|TAP|")) {
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
            if (line.startsWith("CARDSERVER|AUTH|START|")) {
                String cc = line.substring("CARDSERVER|AUTH|START|".length());
                boolean ok = Integer.parseInt(cc) % 2 == 0;
                return ok ? "MAIN|REPLY|CARDSERVER|\"AUTH:YES\"" : "MAIN|REPLY|CARDSERVER|\"AUTH:NO\"";
            }
            return "MAIN|REPLY|CARDSERVER|\"OK\"";
        }
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
