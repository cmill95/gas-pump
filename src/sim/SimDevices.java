package sim;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class SimDevices {

    public static void main(String[] args) throws Exception {
        int screenPort = 5001;
        int hosePort = 5101;
        int flowMeterPort = 5201;

        // Allow overriding ports via args if needed
        if (args.length >= 1) screenPort = Integer.parseInt(args[0]);
        if (args.length >= 2) hosePort = Integer.parseInt(args[1]);
        if (args.length >= 2) flowMeterPort = Integer.parseInt(args[2]);

        SimScreenDevice screen = new SimScreenDevice("screen-01", screenPort);
        SimHoseDevice valve  = new SimHoseDevice("hose-01", hosePort);
        //SimFlowMeter flowmeter = new SimFlowMeter("meter-01", flowMeterPort);

        Thread t1 = new Thread(screen, "SimScreenDevice");
        Thread t2 = new Thread(valve,  "SimHoseDevice");
        t1.start();
        t2.start();

        System.out.println("[sim] Started. Screen @" + screenPort + " deviceId=screen-01, Hose @" + hosePort + " deviceId=hose-01");
        System.out.println("[sim] Press Ctrl+C to stop.");

        t1.join();
        t2.join();
    }

    // --- Base class with common handshake/loop ---
    static abstract class SimDeviceBase implements Runnable {
        private final String deviceId;
        private final int port;

        SimDeviceBase(String deviceId, int port) {
            this.deviceId = deviceId;
            this.port = port;
        }

        @Override
        public void run() {
            try (ServerSocket server = new ServerSocket(port)) {
                while (true) {
                    try (Socket s = server.accept()) {
                        s.setTcpNoDelay(true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

                        // Expect: HELLO main v1
                        String hello = in.readLine();
                        if (hello == null || !hello.startsWith("HELLO ")) {
                            System.out.println(tag() + "bad client hello: " + hello);
                            continue;
                        }
                        out.write("HELLO " + deviceId + " v1\n");
                        out.flush();
                        System.out.println(tag() + "handshake OK with client: " + s.getRemoteSocketAddress());

                        String line;
                        while ((line = in.readLine()) != null) {
                            String resp = handle(line);
                            out.write(resp);
                            out.write('\n');
                            out.flush();
                        }
                        System.out.println(tag() + "client disconnected");
                    } catch (IOException e) {
                        System.out.println(tag() + "client error: " + e);
                    }
                }
            } catch (IOException e) {
                System.err.println(tag() + "fatal server error: " + e);
            }
        }

        protected abstract String handle(String line);

        protected String tag() { return "[" + getClass().getSimpleName() + "] "; }
    }

    // --- Screen device: supports SCREEN CLEAR and SCREEN PRINT <base64> ---
    static final class SimScreenDevice extends SimDeviceBase {
        SimScreenDevice(String deviceId, int port) { super(deviceId, port); }

        @Override
        protected String handle(String line) {
            if (line.equals("SCREEN CLEAR")) {
                System.out.println(tag() + "CLEAR");
                return "OK";
            }
            if (line.startsWith("SCREEN PRINT ")) {
                String b64 = line.substring("SCREEN PRINT ".length());
                try {
                    String text = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
                    System.out.println(tag() + "PRINT: \"" + text + "\"");
                    return "OK";
                } catch (IllegalArgumentException e) {
                    return "ERR 400 bad-base64";
                }
            }
            System.out.println(tag() + "unknown cmd: " + line);
            return "ERR 400 unknown-command";
        }
    }

    // --- Binary device: supports GET and SET 0/1 ---
    static final class SimHoseDevice extends SimDeviceBase {
        private boolean state = false;

        SimHoseDevice(String deviceId, int port) { super(deviceId, port); }

        @Override
        protected String handle(String line) {
            if (line.equals("GET")) {
                return "OK " + (state ? "1" : "0");
            }
            if (line.startsWith("SET ")) {
                String v = line.substring(4).trim();
                if ("1".equals(v)) { state = true;  System.out.println(tag() + "SET 1"); return "OK"; }
                if ("0".equals(v)) { state = false; System.out.println(tag() + "SET 0"); return "OK"; }
                return "ERR 400 bad-value";
            }
            System.out.println(tag() + "unknown cmd: " + line);
            return "ERR 400 unknown-command";
        }
    }

    //static final class SimFlowMeter extends SimDeviceBase {

    }
