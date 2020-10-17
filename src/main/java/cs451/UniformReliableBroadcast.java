package cs451;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UniformReliableBroadcast extends Broadcast {

    private ArrayList<Host> hosts;//todo sara make it a set of integer id's to keep all the unique hosts
    private int majority;
    private PerfectLink perfectLink;
    private Broadcast BC;

    // Maps the message identifier to the number of ack received for that message
    // a message is uniquely identified by a pair (peerID, sequenceNumber)
    private final HashMap<Pair<Integer, Integer>, Set<Integer>> nbrAcks = new HashMap<>();

    // Maps the message identifier to the actual message (string)
    private final HashMap<Pair<Integer, Integer>, String> messages = new HashMap<>();
    private final Set<Pair<Integer, Integer>> delivered = new HashSet<>();

    private BlockingQueue<Pair<String, Integer>> receiveQueue = new LinkedBlockingQueue<>();

    //List to store the messages that have been sent
    private int selfId;
    private Integer sequenceNumber = 1;
    private Thread t1;

    private boolean debug = true;

    /*
        Uniform reliable broadcast built on top of a perfect link. It deliver messages only after
        a majority of peers have ACKed a given message to ensure URB properties.
     */
    public UniformReliableBroadcast(ArrayList<Host> hosts, int selfId, Broadcast BC) throws Exception {
        this.hosts = hosts;
        this.perfectLink = new PerfectLink(this, hosts.get(selfId-1).getIp(), hosts.get(selfId-1).getPort(), hosts);
        this.majority = hosts.size() / 2 + 1;
        this.selfId = selfId;
        this.BC = BC;
        this.t1 = new Thread() {
            public void run() {
                System.out.println("starting thread 1 ");

                handler();
            }
        };
    }

    public void start() {
        t1.start();
        perfectLink.start();
    }

    public void stop() {
        perfectLink.stop();
        t1.interrupt();
    }

    // method called by the perfect link (layer below) to trigger a "deliver".
    public void plDeliver(String payload, Integer senderID) {
        this.receiveQueue.add(Pair.of(payload, senderID));
    }

    // Start Broadcasting a message
    // this method is called by the layer above (FIFO) to broadcast a new message
    public void broadcast(String message) {
        System.out.println("inside broadcast for fifo! urb ");
        Pair<Integer, Integer> messageIdentifier;
        synchronized(sequenceNumber) {
            messageIdentifier = Pair.of(selfId, sequenceNumber++);
        }
        Set<Integer> ackedSet = new HashSet<>();
        ackedSet.add(selfId);
        synchronized(nbrAcks){
            nbrAcks.put(messageIdentifier, ackedSet);
        }
        synchronized (message) {
            messages.put(messageIdentifier, message);
        }
        broadcast(message, messageIdentifier);
    }

    // Broadcasts a message given its messageIdentifier and its content
    private void broadcast(String message, Pair<Integer, Integer> messageIdentifier) {
        for (Host host : hosts) {
            synchronized(nbrAcks) {
                System.out.println("try to broadcast nbrAcks.get(messageIdentifier).contains(host.getId()) = " + nbrAcks.get(messageIdentifier).contains(host.getId()));
                if (!nbrAcks.get(messageIdentifier).contains(host.getId())) {
                    String senderId = Utils.intToString(messageIdentifier.first);
                    String sequence = Utils.intToString(messageIdentifier.second);
                    System.out.println("can it send it?");

                    perfectLink.send(senderId + sequence + message, host.getId());
                    if (debug)
                        System.out.println("Sending (" + messageIdentifier.first + "," + messageIdentifier.second + ") to " + host.getId());
                }
            }
        }
    }

    /*
        Main handling thread that consumes the receiveQueue. It broadcasts new messages and send ACKS to received
        broadcasts.
     */
    private void handler(){
        try {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("running in thread 1");

                Pair<String, Integer> payloadAndId = receiveQueue.take();
                String payload = payloadAndId.first;
                Integer senderId = payloadAndId.second;
                System.out.println("payload is " + payload + "senderid " + senderId);

                // Unpacking
                byte[] bytes = payload.getBytes(Charset.forName("ISO-8859-1"));
                Integer id = Utils.bytesArraytoInt(bytes, 0);
                Integer sequence = Utils.bytesArraytoInt(bytes, 4);
                String message = Utils.bytesArraytoString(bytes, 8, payload.length() - 8);
                if (debug) System.out.println("Received (" + id + "," + sequence + ") from " + senderId);
                Pair<Integer, Integer> messageIdentifier = Pair.of(id, sequence);

                Set<Integer> ackedSet;

                // When it's the first time we see a message
                boolean containsMessage;
                synchronized (messages) {
                     containsMessage = messages.containsKey(messageIdentifier);
                }
                if (!containsMessage) {
                    System.out.println("first time seeing "+ message);

                    // Add message to all messages seen so far
                    synchronized (messages) {
                        messages.put(messageIdentifier, message);
                    }

                    ackedSet = new HashSet<>();
                    ackedSet.add(selfId);
                    // Add sender of message to peers who acked it
                    ackedSet.add(senderId);
                    // Add origin of message to peers who acked it
                    ackedSet.add(id);
                    synchronized(nbrAcks) {
                        nbrAcks.put(messageIdentifier, ackedSet);
                    }
                    broadcast(message, messageIdentifier);

                } else { //When it's not the first time we see a message
                    System.out.println("saw this message before "+ message);

                    synchronized(nbrAcks) {
                        ackedSet = nbrAcks.get(messageIdentifier);
                        ackedSet.add(senderId);
                    }
                }

                // If we have enough ACKS for the message, we can deliver it
                synchronized (delivered) {
                    System.out.println("check if we have enough acks to deliver majority = "+majority + "ack size = " + ackedSet.size());

                    if (ackedSet.size() >= majority && !delivered.contains(messageIdentifier)) {
                        System.out.println("we do");
                        System.out.println("Majority = "+majority);
                        delivered.add(messageIdentifier);
                        synchronized (messages) {
                            deliver(id, sequence, messages.get(messageIdentifier));
                        }
                    }
                }

                // We ack any message we have not yet acked
                if (message.length() > 0) {
                    perfectLink.send(payload.substring(0, 8), senderId);
                    if (debug && this.selfId == 1) System.out.println("Sending (" + id + "," + sequence + ") to " + senderId);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    // Deliver methode that calls deliver of the upper layer
    public void deliver(int id, int sequenceNumber, String message) {
        BC.deliver(id, sequenceNumber, message);
        if (debug) System.out.println("deliver " + id + " " + sequenceNumber);
    }
}
