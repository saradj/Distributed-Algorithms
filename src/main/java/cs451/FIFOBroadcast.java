package cs451;

import java.util.*;

public class FIFOBroadcast extends Broadcast{

    private final UniformReliableBroadcast urb;
    private final Da_proc proc;
    private List<List<String>> messagesToDeliver;
    private int[] nextSequenceToDeliver;

    /*
        FIFO broadcast that uses a Uniform Reliable Broadcast and orders messages to ensure FIFO properties.
    */
    public FIFOBroadcast(ArrayList<Host> peers, int selfId, Da_proc proc) throws Exception {
        this.urb = new UniformReliableBroadcast(peers, selfId, this);
        this.proc = proc;
        this.messagesToDeliver = new ArrayList<>();
        for (int i = 0; i < peers.size() + 1; i++) {
            this.messagesToDeliver.add(new ArrayList<>());
        }
        this.nextSequenceToDeliver = new int[peers.size() + 1];
        Arrays.fill(nextSequenceToDeliver, 1);
        System.out.println("finished fifo broadcast initialization");
    }

    public void start(){
        urb.start();
    }

    public void stop(){
        urb.stop();
    }

    // broadcast method used by the upper layer to broadcast messages
    public void broadcast(String message) {
        urb.broadcast(message);
    }

    // deliver method used by the lower layer (uniform reliable broadcast) to deliver messages
    // the algorithm store messages that are out of sequence to deliver them when possible.
    public void deliver(int id, int sequenceNumber, String message){
        synchronized (messagesToDeliver) {
            List<String> messages = messagesToDeliver.get(id);
            int size = messages.size();
            int difference = sequenceNumber - size + 1;
            if (difference > 0) {
                messages.addAll(Collections.nCopies(difference, null));
            }
            messages.add(sequenceNumber, message);
            for(int i = nextSequenceToDeliver[id]; i < size + difference; i ++){
                if(messages.get(i) == null){
                    break;
                }
                proc.deliver(id, i, messages.get(i));
                nextSequenceToDeliver[id] += 1;
            }
        }
    }
}
