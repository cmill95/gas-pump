package devices;

import io.bus.*;
import java.io.IOException;
import java.sql.Time;
import java.util.Base64;
import java.time.*;
import java.util.Random;
import java.util.concurrent.*;

/*Flow Meter Communicator
* Sends -> Flow Rates -> Main
* Receives -> Fuel Start/Pause/Resume/Stop from Main
*          -> Receive Reset Signals from Main
*          -> Receive Price for selected Fuel from Main
* */
public final class FlowMeter implements AutoCloseable {

    //Time Configurations
    private static final Duration TIMEOUT = Duration.ofSeconds(30);  //Timeout
    private static final long TICK = 100L; // 10 Hz

    //IO Connection
    private final DeviceLink link;

    //Sim and Counting Fields
    private final ScheduledExecutorService exec; //Single Thread Executor for Tick Cadence in Background
    private ScheduledFuture<?> ticker; //Handles getting info from thread without blocking
    private final Random rng = new Random(); //For Randomizing Flow Rates, for testing

    private volatile boolean running = false;
    private volatile boolean paused = false;

    private volatile float pricePerGallon = 2.79f; //Hypothetical Price, Will be set by main Later
    private volatile float runningGallons = 0.0f;
    private volatile float runningPrice = 0.0f;
    private volatile float currentRateGPM = 0.0f; //Random Gallons-per-Minutes within reason

    private Instant startedAt = null;
    private Instant lastTickAt = null;

    //Constructor
    public FlowMeter(DeviceLink link) {
        this.link = link;

        //implement thread interface in constructor
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FM_Ticker");
            t.setDaemon(true); //set Daemon Thread for Running and Background
            return t;
        });
    }

    //Methods For FlowMeter Controls
    public synchronized void start() throws IOException {
        if (running) return; //Double Start Guard

        ensureOpen();  //Checks DeviceLink is usable
        requestQuietly("FLOW_START"); // Command, sim may ignore

        running = true;
        paused = false;
        startedAt = Instant.now();
        lastTickAt = startedAt;
        currentRateGPM = nextSimRateGPM(); //Seed an init GPM rate

        //Schedules periodic task
        ticker = exec.scheduleAtFixedRate(this::safeTick, 0L,
                TICK, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() throws IOException {
        if (!running) return; //Double Stop Guard

        tickOnce(); //Cover missed time between flag switch
        requestQuietly("FLOW_STOP"); // Command, sim may ignore

        //Stop Couniting and Ticker
        running = false;
        paused = false;
        if (ticker != null) {
            ticker.cancel(false);
            ticker = null;
        }
    }

    public synchronized void pause() throws IOException {
        if (!running || paused ) return;
        requestQuietly ("FLOW_PAUSE");
        paused = true;
    }

    public synchronized void resume() throws IOException {
        if (!running || !paused) return;
        requestQuietly ("FLOW_RESUME");
        paused = false;
        lastTickAt = Instant.now();
    }

    @Override
    public synchronized void close() throws IOException {
        stop();
        exec.shutdown(); //Stop executors
    }

    //Methods for internals of contols
    private void safeTick() {
        try {tickOnce();} catch (Throwable t) {}
    }

    private void tickOnce() {
        if (!running || paused) return;

        //Compute Elapsed Time and Update lastTick
        final Instant now = Instant.now();
        final Instant last = (lastTickAt == null ?  now : lastTickAt);
        final double dtSec = Math.max(0.0, (now.toEpochMilli() - last.toEpochMilli()) /  1000.0);
            //use Epoch so dtSec is never negative, safe guard
        lastTickAt = now;

        //Change GPM and Calculate
        currentRateGPM = clamp((float) (currentRateGPM + (rng.nextFloat() * 0.15)), 5.5F, 10.5F);
        final float gallonsAdded = (float) ((currentRateGPM /60.0) * dtSec);

        runningGallons += gallonsAdded;
        recalcPrice();
    }

    private void recalcPrice() {runningPrice = runningGallons * pricePerGallon;}

    private float nextSimRateGPM() {
        return (float) (6.0 + rng.nextFloat() * 4.0);
    }

    //Keeps Random Values within reasonable range
    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void ensureOpen() throws IOException {
        if (!link.isOpen()) throw new IOException("Link not Open");
        requestQuietly("PING");
    }

    //Method for Requesting from Device Link
    private void requestQuietly(String line) {
        try {
            link.request(line, TIMEOUT);
        } catch (Throwable ignored) {
            //Sim should implement
        }
    }

    //Setter Getters and Send Info Methods
    public synchronized String sendInfo() {
        long elapsed = startedAt == null ? 0 : Duration.between(startedAt,Instant.now()).getSeconds();
        return String.format(
                "OK FLOW running=%d paused=%d GPM=%.2f gallons=%.3f price=%.2f deltaSec=%d",
                running ? 0:1, paused ? 0:1, currentRateGPM, runningGallons, pricePerGallon, elapsed
        );
    }

    public synchronized void setPrice(float price) {this.pricePerGallon = price;}
    public synchronized float getRunningAmount() { return (float) runningGallons; }
    public synchronized float getRunningTotal() { return (float) runningPrice; }
    public synchronized void resetRunningTotal() { this.runningPrice = 0.0F; }
    public synchronized void resetRunningAmount() { this.runningGallons = 0.0F; }
    public synchronized boolean isRunning() { return running; }
    public synchronized boolean isPaused() { return paused; }

}