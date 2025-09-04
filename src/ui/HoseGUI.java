package ui;

import devices.BinaryDevice;
import io.bus.DeviceLink;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.Duration;

/**
 * Hose tester: lets you open/close the main valve and (optionally) talk to a flow meter.
 * - Works now with only the Valve simulator running.
 * - If a Flow device (flow-01 @ 5401) is not running yet, flow controls are disabled.
 */
public class HoseGUI extends Application {

    // Ports/IDs match
    private static final String HOST = "127.0.0.1";
    private static final int    VALVE_PORT = 5101;   // SimBinaryDevice (valve-01)
    private static final String VALVE_ID   = "valve-01";

    private static final int    FLOW_PORT  = 5401;   // SimFlowDevice (flow-01), IF present later
    private static final String FLOW_ID    = "flow-01";

    private static final double TICKS_PER_GALLON = 500.0; // demo calibration

    private BinaryDevice valve;          // required (we control it)
    private DeviceLink   flowLink = null; // optional (we try to connect; may be null)

    @Override
    public void start(Stage stage) throws Exception {
        Label status = new Label("Starting...");
        Label reading = new Label("Flow: n/a");

        // ----- Connect to valve (required) -----
        try {
            DeviceLink valveLink = new DeviceLink(HOST, VALVE_PORT, VALVE_ID);
            valve = new BinaryDevice(valveLink);
            status.setText("Valve connected.");
        } catch (IOException ex) {
            status.setText("ERROR: Could not connect to valve @ " + HOST + ":" + VALVE_PORT + " (" + ex.getMessage() + ")");
        }

        // ----- Try to connect to flow meter (optional) -----
        try {
            flowLink = new DeviceLink(HOST, FLOW_PORT, FLOW_ID);
            status.setText(status.getText() + "  Flow connected.");
        } catch (IOException ex) {
            flowLink = null; // not available yet
            status.setText(status.getText() + "  (Flow device not connected; controls disabled.)");
        }

        // ----- UI controls -----
        ToggleButton toggleValve = new ToggleButton("Valve: CLOSED");
        toggleValve.setDisable(valve == null);
        toggleValve.setOnAction(e -> {
            if (valve == null) return;
            try {
                boolean open = toggleValve.isSelected();
                valve.set(open);
                toggleValve.setText(open ? "Valve: OPEN" : "Valve: CLOSED");
                status.setText("Valve " + (open ? "OPEN" : "CLOSED"));
            } catch (Exception ex) {
                status.setText("Valve ERR: " + ex.getMessage());
                toggleValve.setSelected(false);
                toggleValve.setText("Valve: CLOSED");
            }
        });

        Button flowReset = new Button("Flow: Reset");
        Button flowTick  = new Button("Flow: +100 ticks");
        Button flowRead  = new Button("Flow: Read");
        flowReset.setDisable(flowLink == null);
        flowTick.setDisable(flowLink == null);
        flowRead.setDisable(flowLink == null);

        flowReset.setOnAction(e -> {
            try { expectOk(flowLink.request("RESET", Duration.ofMillis(500)));
                reading.setText("Flow: 0 ticks (0.000 gal)");
                status.setText("Flow reset OK");
            } catch (Exception ex) { status.setText("Flow ERR: " + ex.getMessage()); }
        });

        flowTick.setOnAction(e -> {
            try { expectOk(flowLink.request("TICK 100", Duration.ofMillis(500)));
                status.setText("Added 100 ticks");
            } catch (Exception ex) { status.setText("Flow ERR: " + ex.getMessage()); }
        });

        flowRead.setOnAction(e -> {
            try {
                String r = flowLink.request("GET", Duration.ofMillis(500));
                long ticks = parseOkLong(r);
                double gallons = ticks / TICKS_PER_GALLON;
                reading.setText(String.format("Flow: %d ticks (%.3f gal)", ticks, gallons));
                status.setText("Flow read OK");
            } catch (Exception ex) { status.setText("Flow ERR: " + ex.getMessage()); }
        });

        VBox root = new VBox(10,
                new Label("Hose Tester"),
                toggleValve,
                new HBox(10, flowReset, flowTick, flowRead),
                reading,
                status
        );
        root.setPadding(new Insets(12));

        stage.setTitle("Hose Tester");
        stage.setScene(new Scene(root, 460, 220));
        stage.show();
    }

    private static void expectOk(String resp) throws IOException {
        if (resp == null || !resp.startsWith("OK"))
            throw new IOException("Bad response: " + resp);
    }
    private static long parseOkLong(String r) throws IOException {
        if (r == null || !r.startsWith("OK"))
            throw new IOException("Bad response: " + r);
        String[] parts = r.split("\\s+");
        return Long.parseLong(parts[parts.length - 1]);
    }

    public static void main(String[] args) { launch(args); }
}
