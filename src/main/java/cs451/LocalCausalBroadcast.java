package cs451;

import java.util.*;

/**
 * Class encapsulating Localized Causal Broadcast,build on top of Uniform Reliable Broadcast
 * and using a vector clock to ensure localized causal properties.
 */
public class LocalCausalBroadcast extends Broadcast {

    private final Broadcast urb;
    private final Process proc;
    private final HashMap<Message, String> pending;
    private final int[] vectorClock;
    private final Set<Integer> nonDependencies; //we nee
    private final int selfId;
    private final int hostsCount;
    private int lsn;

    /**
     * In order to make the broadcast "localized" (in comparison to regular causal broadcast), when a host hi sends its vector
     * clock to another host, it will set to 0 all the entries of the vector clock corresponding to hosts hj
     * so that hi does not depend on hj.
     */
    public LocalCausalBroadcast(ArrayList<Host> hosts, Set<Integer> dependencies, int selfId, Process process) throws Exception {
        this.urb = new UniformReliableBroadcast(hosts, selfId, this);
        this.selfId = selfId;
        this.lsn = 1;
        this.proc = process;
        this.hostsCount = hosts.size();

        this.nonDependencies = new HashSet<>();
        for (int i = 1; i < hostsCount; i++) {
            if (!dependencies.contains(i)) {
                nonDependencies.add(i);
            }
        }
        this.pending = new HashMap<>();
        this.vectorClock = new int[hostsCount + 1];
        Arrays.fill(vectorClock, 1);
    }

    public void run() {
        urb.run();
    }

    public void stop() {
        urb.stop();
    }

    // method used by the URB to broadcast messages
    public void broadcast(String message) {
        synchronized (vectorClock) {
            int[] vectorClockCopy = vectorClock.clone();
            for (int i : nonDependencies) {
                vectorClockCopy[i] = 1;
            }
            vectorClockCopy[selfId] = lsn;
            lsn += 1;
            urb.broadcast(Helper.VCToString(vectorClockCopy) + message);
        }
    }

    /**
     * Method that checks if the vector clock vClock1 is smaller or equal to vClock2
     *
     * @param vClock1
     * @param vClock2
     * @return
     */
    private boolean isSmaller(int[] vClock1, int[] vClock2) {
        if (vClock1.length != vClock2.length)
            return false;
        for (int i = 0; i < vClock1.length; i++) {
            if (vClock1[i] > vClock2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deliver method used by the URB to deliver messages
     *
     * @param id
     * @param sequenceNumber
     * @param payload
     */
    public void deliver(int id, int sequenceNumber, String payload) {
        synchronized (this) {
            pending.put(new Message(id, sequenceNumber), payload);
            Iterator<Message> iterator = pending.keySet().iterator();
            while (iterator.hasNext()) {
                Message msg = iterator.next();
                int[] receivedVectorClock = Helper.stringToVC(pending.get(msg), hostsCount + 1);
                String message = Helper.getMessageVC(pending.get(msg), hostsCount + 1);
                if (isSmaller(receivedVectorClock, vectorClock) && (vectorClock[msg.getId()] == msg.getSequenceNumber())) {
                    vectorClock[msg.getId()] += 1;
                    proc.deliver(msg.getId(), msg.getSequenceNumber());
                    iterator.remove();
                }
            }
        }
    }

}
