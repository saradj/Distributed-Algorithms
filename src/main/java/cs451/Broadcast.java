package cs451;

public abstract class Broadcast {
    abstract void start();

    abstract void stop();

    // broadcast method used by the upper layer to broadcast messages
    abstract void broadcast(String message);

    // deliver method used by the lower layer to deliver messages
    // the algorithm store messages that are out of sequence to deliver them when possible.
    abstract void deliver(int id, int sequenceNumber, String message);
}
