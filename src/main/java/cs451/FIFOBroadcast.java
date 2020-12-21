package cs451;

import java.util.*;

public class FIFOBroadcast extends Broadcast {

    private final Process process;
    private final UniformReliableBroadcast urb;
    private final Map<Integer, List<String>> messagesToDeliver; //holds the messages to deliver to host at position i
    private final int[] nextSequenceToDeliver; //holds the next sequence to deliver for each host i

    /**
     * FIFO broadcast using Uniform Reliable Broadcast with message ordering to ensure FIFO properties.
     */
    public FIFOBroadcast(ArrayList<Host> hosts, int selfId, Process process) throws Exception {
        this.urb = new UniformReliableBroadcast(hosts, selfId, this);
        this.process = process;
        this.messagesToDeliver = new HashMap<>();
        for (int i = 0; i < hosts.size() + 1; i++) {
            this.messagesToDeliver.put(i, new ArrayList<>());
        }
        this.nextSequenceToDeliver = new int[hosts.size() + 1];
        Arrays.fill(nextSequenceToDeliver, 1);
    }

    public void run() {
        urb.run();
    }

    public void stop() {
        urb.stop();
    }

    public void broadcast(String message) {
        urb.broadcast(message);
    }

    /**
     * Delivering method used by the uniform reliable broadcast to deliver messages.
     * The out of sequence messages are stored to be delivered later
     */
    public void deliver(int id, int sequenceNumber, String message) {
        synchronized (messagesToDeliver) {
            List<String> messages = messagesToDeliver.get(id);
            int size = messages.size();
            int difference = sequenceNumber - size + 1;
            if (difference > 0) {
                messages.addAll(Collections.nCopies(difference, null)); //padding until the sequence number
            }
            messages.add(sequenceNumber, message);
            for (int i = nextSequenceToDeliver[id]; i < size + difference; i++) {
                if (messages.get(i) == null) {
                    //if there is a seq number that was out of order we need to wait for the previous
                    //ones so we just do not deliver any further msg with a seq number greater than the current
                    break;
                }
                process.deliver(id, i);
                nextSequenceToDeliver[id] += 1;
            }
        }
    }
}
