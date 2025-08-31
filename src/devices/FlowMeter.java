package devices;

import io.bus.*;
import java.io.IOException;
import java.sql.Time;
import java.util.Base64;
import java.time.*;

/*Flow Meter Communicator
* Sends -> Flow Rates -> Main
* Receives -> Fuel Start/Pause/Resume/Stop from Main
*          -> Receive Reset Signals from Main
*          -> Receive Price for selected Fuel from Main
* */
public final class FlowMeter {
    //Fields and Constructors
    private final DeviceLink link;
    private String status;
    public FlowMeter(DeviceLink link,  String status) {
        this.link = link;
        this.status = status;
    }

    //Hypothetical Values, Need to update when Main sends
    public float price = 2.79F;
    private float runningTotal = 0.0F;
    private float runningAmount = 0.0F;


    /**
     * Use a Timer to Calculate amounts of Gallons
     * Use runningAmount to Calculate runningTotal
     */
    private void start() throws IOException {

    }

    /**
     *
     */
    private void stop() {}

    /**
     *
     */
    private void reset (){}

    /**
     * Get status from Main about the pump
     */
    public void getPumpStatus () {
        //Info From Main
    }

    /**
     * @return MESSAGE in API Format back to main
     */
    public String sendInfo (){
        return "";
    }

    //Setter, Resetters and Getters
    public void setPrice (float price){this.price = price;}
    public float getRunningAmount() {return runningAmount;}
    public float getRunningTotal() {return runningTotal;}
    public void resetRunningTotal() {this.runningTotal = 0.0F;}
    public void resetRunningAmount() {this.runningAmount = 0.0F;}
}