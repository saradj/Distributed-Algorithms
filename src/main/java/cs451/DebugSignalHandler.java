package cs451;

import sun.misc.Signal;
import sun.misc.SignalHandler;


//Class to handle signals
public class DebugSignalHandler implements SignalHandler {

    //Class Variables
    private Da_proc proc;

    //Constructor of the signal handler
    public DebugSignalHandler(Da_proc proc) {
        this.proc = proc;
    }

    //Listen to a given signal
    public void listenTo(String name) {
        Signal signal = new Signal(name);
        Signal.handle(signal,this);
    }

    //Method that will be invoked when one of the signal it is listening is sent
    public void handle(Signal signal) {
        switch (signal.toString().trim()) {
            case "SIGTERM":
            case "SIGINT":
                proc.stop();
                break;
            case "SIGUSR2":
                proc.usr2Signal();
                break;
                default:
                    System.out.println("Other Signal");
                    break;
        }
    }
}
