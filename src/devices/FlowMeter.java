package devices;

import io.bus.*;
import java.time.*;
import java.util.concurrent.*;

/*Flow Meter Communicator
* Sends -> Flow Rates -> Main
* Receives -> Fuel Start/Pause/Resume/Stop from Main
*          -> Receive Reset Signals from Main
*          -> Receive Price for selected Fuel from Main
* */


/* CONTROLLER USAGE
* FlowMeter fm = new FlowMeter(DeviceLink)
* fm.setPricePerGal (x.xxf) // Must be Float
* fm.start()
*
* While (CONTROLLER RUNNING)
* fm.loop();
* FlowMeter.State state = fm.,getState
*   -> USE to update ui
*
* */


public final class FlowMeter{
    //Inner State class for recording
    public static final class State {
        public final boolean running;
        public final boolean paused;
        public final double gallons;
        public final double price;
        public final double currGPM;
        public final Duration elapsed;

        State(boolean running, boolean paused, double gallons, double price,
              double currGPM,Duration elapsed) {
            this.running = running;
            this.paused = paused;
            this.gallons = gallons;
            this.price = price;
            this.currGPM = currGPM;
            this.elapsed = elapsed;
        }

        @Override
        public String toString() {
            long s = elapsed.toSeconds();
            return String.format("time=%ds  gallons=%.3f  price=$%.2f  rate=%.3f gpm  Run/Stop:Paused=>[%s:%s]",
                    s, gallons, price, currGPM,
                    running ? "RUN" : "STOP",
                    paused ? "/PAUSE" : "");
        }
    }

    //IO
    private final DeviceLink link;
    //States, Config
    private boolean running = false;
    private boolean paused = false;
    private float pricePerGal = 0.0f;
    //Accumulators
    private double runningGals = 0.0;
    private double runningPrice = 0.0;
    private double currGPM = 8.5;
    //Timing Control
    private long startNan = 0L;
    private long lastNan = 0L;
    //test helper
    private boolean useDeviceRate = true;

    //Constructor
    public FlowMeter(DeviceLink link) {
        this.link = link;
    }

    //Setters
    //Price Setter, 0.1 if not set
    public void setPricePerGal(float dollars) {this.pricePerGal = Math.max(0.1f, dollars);}
    public void setCurrGPM(double rate) {
        this.useDeviceRate = false;
        this.currGPM = Math.abs(rate); //Assume all pos
    }
    public void useDeviceRate () {this.useDeviceRate = true;}

    public void Start() {
        if (running) return;
        running = true;
        paused = false;
        runningGals = 0.0;
        runningPrice = 0.0;

        startNan = System.nanoTime();
        lastNan = startNan;
        //Send Starting Message
        try {send("Meter:start"); } catch (Exception ignored) {}
        if (useDeviceRate) {currGPM = tryReadRate(currGPM);}
    }

    public void resume() {
        if (!running || !paused) return;
        paused = false;
        lastNan = System.nanoTime();
        try {send("METER:RESUME"); } catch (Exception ignored) {}
    }

    public void pause() {
        if (!running || paused) return;
        paused = true;
        try {send("METER:PAUSE"); } catch (Exception ignored) {}
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {send("METER:STOP"); } catch (Exception ignored) {}
    }

    //controller should call this, should get time difference between calls
    //Computes Price and other info
    public void loop(){
        final long now = System.nanoTime();
        final long prev = lastNan == 0L ? now : lastNan;
        lastNan = now;

        //Look For Next Rate when Stopped
        if(!running||paused){
            if (useDeviceRate) currGPM = tryReadRate(currGPM);
            return;
        }

        //refresh rate
        if (useDeviceRate) currGPM = tryReadRate(currGPM);

        //Use Loop Mechanism
        final double deltaSec = (now-prev)/1_000_000_000.0;
        //ensure time change is not neg
        if (deltaSec > 0){
            final double deltaGal = currGPM*(deltaSec/60);
            runningGals += deltaGal;
            runningPrice += deltaGal*pricePerGal;
        }
    }

    public State getState() {
        //Timing
        final long ref = startNan == 0L ? System.nanoTime() : startNan;
        final long end = (running ? System.nanoTime() : lastNan);
        final Duration elapsed = (end >= ref) ? Duration.ofNanos(end - ref) : Duration.ZERO;
        return new  State(running, paused, runningGals, runningPrice, currGPM,  elapsed);
    }

    // Communication Helpers
    private double tryReadRate(double ignore) {
        try {
            final String reply = send("METER:RATE?");
            final String s = reply.contains(":") ?
                    reply.substring(reply.indexOf(':') + 1) : reply;
            return Math.abs(Double.parseDouble(s.trim()));
        } catch (Exception e){
            return ignore;
        }
    }

    private String send(String cmd) throws Exception {
        if (link == null) { return "";}
        return link.request(cmd,Duration.ofSeconds(7));
    }

}