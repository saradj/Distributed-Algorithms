package cs451;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UniformReliableBroadcast extends Broadcast {

    private final HashMap<Message, Set<Integer>> nbrAcks = new HashMap<>();
    private final HashMap<Message, String> messages = new HashMap<>();
    private final Set<Message> delivered = new HashSet<>();
    private final ArrayList<Host> hosts;
    private final int selfId;
    private final Broadcast BC;
    private final PerfectLink perfectLink;
    private final BlockingQueue<Payload> receiveQueue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private final int majority;
    private final boolean debug = true;
    private Integer sequenceNumber = 1;

    /**
     * Uniform reliable broadcast built on top of a perfect link. It deliver messages only after
     * a majority of peers have ACKed a given message to ensure URB properties.
     */
    public UniformReliableBroadcast(ArrayList<Host> hosts, int selfId, Broadcast BC) throws Exception {
        this.hosts = hosts;
        this.perfectLink = new PerfectLink(this, hosts.get(selfId - 1).getIp(), hosts.get(selfId - 1).getPort(), hosts);
        this.majority = hosts.size() / 2 + 1;
        this.selfId = selfId;
        this.BC = BC;
        this.thread = new Thread() {
            public void run() {
                handler();
            }
        };
    }

    public void run() {
        thread.start();
        perfectLink.start();
    }

    public void stop() {
        perfectLink.stop();
        thread.interrupt();
    }

    /**
     * Called by the layer below -  perfect link, to trigger a deliver
     */
    public void plDeliver(String payload, Integer senderID) {
        this.receiveQueue.add(new Payload(payload, senderID));
    }

    /**
     * Called by the layer above - FIFO to broadcast a new message
     *
     * @param message to be broadcasted
     */
    public void broadcast(String message) {
        Message messageIdentifier;
        synchronized (sequenceNumber) {
            messageIdentifier = new Message(selfId, sequenceNumber++);
        }
        Set<Integer> ackedSet = new HashSet<>();
        ackedSet.add(selfId);
        synchronized (nbrAcks) {
            nbrAcks.put(messageIdentifier, ackedSet);
        }
        synchronized (messages) {
            messages.put(messageIdentifier, message);
        }
        finishBroadcast(message, messageIdentifier);
    }

    private void finishBroadcast(String message, Message messageIdentifier) {
        for (Host host : hosts) {
            synchronized (nbrAcks) {
                if (!nbrAcks.get(messageIdentifier).contains(host.getId())) {
                    String senderId = Helper.intToString(messageIdentifier.getId());
                    String sequence = Helper.intToString(messageIdentifier.getSequenceNumber());

                    perfectLink.send(senderId + sequence + message, host.getId());
                    if (debug)
                        System.out.println("Sending (" + messageIdentifier.getId() + "," + messageIdentifier.getSequenceNumber() + ") to " + host.getId());
                }
            }
        }
    }

    /**
     * Main handling thread that consumes the receiveQueue. It broadcasts new messages and send ACKS to received
     * broadcasts.
     */
    private void handler() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Payload payloadAndId = receiveQueue.take();
                String payload = payloadAndId.getPayload();
                Integer senderId = payloadAndId.getSenderID();
                byte[] bytes = payload.getBytes(StandardCharsets.ISO_8859_1);
                Integer id = Helper.bytesToInt(bytes, 0);
                Integer sequence = Helper.bytesToInt(bytes, 4);
                String message = Helper.bytesToString(bytes, 8, payload.length() - 8);
                if (debug) System.out.println("Received (" + id + "," + sequence + ") from " + senderId);
                Message msg = new Message(id, sequence);

                Set<Integer> ackedSet;
                boolean containsMessage;
                synchronized (messages) {
                    containsMessage = messages.containsKey(msg);
                }
                if (!containsMessage) {
                    // Add message to all messages seen so far
                    synchronized (messages) {
                        messages.put(msg, message);
                    }
                    //initialize the ack set for this message with the sender and the current id
                    ackedSet = new HashSet<>();
                    ackedSet.add(selfId);
                    ackedSet.add(senderId);
                    ackedSet.add(id);
                    synchronized (nbrAcks) {
                        nbrAcks.put(msg, ackedSet);
                    }
                    finishBroadcast(message, msg);

                } else { //We only add the sender to the ack set if we've seen this message before
                    synchronized (nbrAcks) {
                        ackedSet = nbrAcks.get(msg);
                        ackedSet.add(senderId);
                    }
                }

                // With enough ACKs for the message, it can be delivered
                synchronized (delivered) {
                    if (ackedSet.size() >= majority && !delivered.contains(msg)) {
                        delivered.add(msg);
                        synchronized (messages) {
                            deliver(id, sequence, messages.get(msg));
                        }
                    }
                }

                // We ack any message we have not yet acked
                if (message.length() > 0) {
                    perfectLink.send(payload.substring(0, 8), senderId);
                    if (debug)
                        System.out.println("Sending (" + id + "," + sequence + ") to " + senderId);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    public void deliver(int id, int sequenceNumber, String message) {
        BC.deliver(id, sequenceNumber, message);
        if (debug) System.out.println("deliver " + id + " " + sequenceNumber);
    }
}
