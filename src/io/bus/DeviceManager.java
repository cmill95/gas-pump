package io.bus;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class DeviceManager implements AutoCloseable {
    public static final class Entry {
        public final String name, host, deviceId, kind; // kind = "screen" | "binary" | etc.
        public final int port;
        public Entry(String name, String host, int port, String deviceId, String kind)
        {
            this.name = name; this.host = host; this.port = port; this.deviceId = deviceId; this.kind = kind;
        }
    }

    private final Map<String, DeviceLink> links = new HashMap<>();
    private final Map<String, Entry> entries;

    public DeviceManager(List<Entry> entries)
    {
        this.entries = entries.stream().collect(Collectors.toMap(e -> e.name, e -> e));
    }

    private DeviceLink linkFor(String name) throws IOException
    {
        return links.computeIfAbsent(name, n -> {
            Entry e = entries.get(n);
            if (e == null) throw new IllegalArgumentException("Unknown device: " + n);
            try { return new DeviceLink(e.host, e.port, e.deviceId); }
            catch (IOException ex) { throw new RuntimeException(ex); }
        });
    }

    public io.bus.DeviceLink link(String name) throws IOException
    {
        return linkFor(name);
    }

    @Override public void close() throws IOException {
        IOException first = null;
        for (DeviceLink l : links.values()) {
            try { l.close(); } catch (IOException ex) { if (first == null) first = ex; }
        }
        if (first != null) throw first;
    }
}
