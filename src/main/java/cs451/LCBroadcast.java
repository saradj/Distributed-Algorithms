package cs451;

import java.util.*;

public class LCBroadcast extends Broadcast{

    private final Broadcast urb;
    private final Da_proc proc;
    private HashMap<Pair<Integer, Integer>, Pair<int[], String>> pending;
    private int[] vectorClock;
    private List<Integer> dependencies;
    private Set<Integer> nonDependencies;
    private int lsn;
    private int selfId;
    private int nbrPeers;

    /*
        Localized causal broadcast that uses a Uniform Reliable Broadcast and a vector clock to ensure localized causal properties.

        The difference with the regular causal broadcast algorithm as described in the book
        "Introduction to reliable and secure distributed programming" is that when a peer pi sends its vector
        clock to another peer, it sets to 0 all the entries of the vector clock corresponding to peers pj s.t.
        pi does not depend on pj.
    */
    public LCBroadcast(HashMap<Integer, Pair<String, Integer>> peers, List<Integer> dependencies, int selfId, Da_proc proc) throws Exception {
        // we use an instance of Uniform Reliable Broadcast
        this.urb = null; //todo sara make it urb with  correct hosts
        this.dependencies = dependencies;
        this.selfId = selfId;
        this.lsn = 1;
        this.proc = proc;
        this.nbrPeers = peers.size();

        this.nonDependencies = new HashSet<>();
        for(int i = 1; i < nbrPeers; i++) {
            if(!dependencies.contains(i)){
                nonDependencies.add(i);
            }
        }
        this.pending = new HashMap<>();
        this.vectorClock = new int[nbrPeers + 1];
        Arrays.fill(vectorClock, 1);
    }

    public void start() {
        urb.start();
    }

    public void stop() {
        urb.stop();
    }

    // broadcast method used by the upper layer to broadcast messages
    public void broadcast(String message) {
        synchronized (this) {
            int[] vectorClockCopy = vectorClock.clone();
            for (int i : nonDependencies) {
                vectorClockCopy[i] = 1;
            }
            vectorClockCopy[selfId] = lsn;
            lsn += 1;
            urb.broadcast(Utils.VCToString(vectorClockCopy) + message);
        }
    }

    // checks if the vector clock vc1 is smaller or equal to vc2
    private boolean isSmaller(int[] vc1, int[] vc2){
        for(int i = 0; i < vc1.length; i++){
            if(vc1[i] > vc2[i]){
                return false;
            }
        }
        return true;
    }

    // deliver method used by the lower layer (uniform reliable broadcast) to deliver messages
    public void deliver(int id, int sequenceNumber, String payload) {
        synchronized (this) {
            int[] receivedVectorClock = Utils.stringToVC(payload, nbrPeers + 1);
            String message = Utils.getMessageVC(payload, nbrPeers + 1);
            pending.put(Pair.of(id, sequenceNumber), Pair.of(receivedVectorClock, message));
            Iterator<Pair<Integer, Integer>> iterator = pending.keySet().iterator();
            while (iterator.hasNext()) {
                Pair<Integer, Integer> pair = iterator.next();
                if (isSmaller(pending.get(pair).first, vectorClock)) {
                    vectorClock[pair.first] += 1;
                    proc.deliver(pair.first, pair.second, pending.get(pair).second);
                    iterator.remove();
                }
            }
        }
    }
}
