package devices;

import io.bus.DeviceLink;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

public final class Screen {
    private final DeviceLink link;
    public Screen(DeviceLink link) { this.link = link; }

    public void clear() throws IOException {
        expectOk(link.request("SCREEN CLEAR", Duration.ofMillis(500)));
    }

    public void printLine(String text) throws IOException {
        String b64 = Base64.getEncoder().encodeToString(text.getBytes());
        expectOk(link.request("SCREEN PRINT " + b64, Duration.ofMillis(800)));
    }

    private static void expectOk(String resp) throws IOException {
        if (!resp.startsWith("OK")) throw new IOException("Screen error: " + resp);
    }
}

