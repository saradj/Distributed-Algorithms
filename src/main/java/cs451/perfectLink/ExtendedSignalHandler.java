package cs451.perfectLink;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class ExtendedSignalHandler implements SignalHandler {

    public static void install(String signalName, ExtendedSignalHandler handler) {

        Signal signal = new Signal(signalName);
        ExtendedSignalHandler diagnosticSignalHandler = new ExtendedSignalHandler();
        Signal.handle(signal, diagnosticSignalHandler);
        diagnosticSignalHandler.setHandler(handler);
    }

    private ExtendedSignalHandler handler;

    private ExtendedSignalHandler() {
    }

    private void setHandler(ExtendedSignalHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handle(Signal sig) {
        System.out.println("Diagnostic Signal handler called for signal " + sig);
        try {
            handler.handle(sig);

        } catch (Exception e) {
            System.out.println("Signal handler failed, reason " + e);
        }
    }

}
