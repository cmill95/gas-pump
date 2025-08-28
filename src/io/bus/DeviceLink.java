package io.bus;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class DeviceLink implements AutoCloseable {
    private static final String PROTO_VERSION = "v1";
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final String deviceId;

    public DeviceLink(String host, int port, String expectedDeviceId) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), /*connectTimeoutMs*/ 3000);
        this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        // Handshake (ensures "sockets match")
        writeLine("HELLO main " + PROTO_VERSION);
        String hello = readLine(Duration.ofMillis(3000));
        if (hello == null || !hello.startsWith("HELLO ")) {
            throw new IOException("Bad handshake: " + hello);
        }
        String[] parts = hello.split("\\s+");
        if (parts.length < 3) throw new IOException("Malformed HELLO: " + hello);
        this.deviceId = parts[1];
        String serverVer = parts[2];
        if (!expectedDeviceId.equals(this.deviceId)) {
            throw new IOException("Device id mismatch: expected " + expectedDeviceId + " got " + this.deviceId);
        }
        if (!PROTO_VERSION.equals(serverVer)) {
            throw new IOException("Protocol version mismatch: expected " + PROTO_VERSION + " got " + serverVer);
        }
    }

    public synchronized String request(String line, Duration timeout) throws IOException {
        writeLine(line);
        String resp = readLine(timeout);
        if (resp == null) throw new EOFException("Device closed connection");
        return resp;
    }

    public synchronized void send(String line) throws IOException { writeLine(line); }

    private void writeLine(String line) throws IOException {
        out.write(line);
        out.write('\n');
        out.flush();
    }

    private String readLine(Duration timeout) throws IOException {
        int prev = socket.getSoTimeout();
        socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
        try { return in.readLine(); }
        finally { socket.setSoTimeout(prev); }
    }

    public boolean isOpen() { return socket.isConnected() && !socket.isClosed(); }

    @Override public void close() throws IOException { socket.close(); }
}

