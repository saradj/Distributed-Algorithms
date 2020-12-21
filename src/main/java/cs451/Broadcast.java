package cs451;

public abstract class Broadcast {

    /**
     * Broadcasting a message, used by the upper layer
     */
    abstract void broadcast(String message);

    /**
     * Delivers the message, used by the lower level
     */
    abstract void deliver(int id, int sequenceNumber, String message);

    abstract void run();

    abstract void stop();
}
