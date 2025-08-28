package devices;

import io.bus.DeviceLink;
import java.io.IOException;
import java.time.Duration;

public final class BinaryDevice {
    private final DeviceLink link;
    public BinaryDevice(DeviceLink link) { this.link = link; }

    public void set(boolean on) throws IOException {
        expectOk(link.request("SET " + (on ? "1" : "0"), Duration.ofMillis(500)));
    }

    public boolean get() throws IOException {
        String r = link.request("GET", Duration.ofMillis(500));
        if (!r.startsWith("OK ")) throw new IOException("Binary get error: " + r);
        return r.endsWith(" 1") || r.equals("OK 1");
    }

    private static void expectOk(String resp) throws IOException {
        if (!resp.startsWith("OK")) throw new IOException("Binary error: " + resp);
    }
}

