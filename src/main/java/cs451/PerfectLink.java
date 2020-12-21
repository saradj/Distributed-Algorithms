package cs451;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static cs451.Constants.*;

public class PerfectLink {
    private final ArrayList<TimedPacket> timedTimedPackets;
    private final InetAddress srcIP;
    private final int srcPort;
    private final DatagramSocket socket;
    private final Thread receiveThread, sendThread, handleThread, timerThread;
    private final ArrayList<Host> hosts;
    private final BlockingQueue<PacketHelper> receiveQueue;
    private final BlockingQueue<DatagramPacket> sendQueue;
    // the array of sequence numbers for messages to be sent per host
    private final int[] sequenceNumbers;
    // the array of ACKS the local machine wants to receive messages for per host
    private final int[] nextExpectedAck;
    // the array of the last (highest value) ACK received per host
    private final int[] mostRecentReceivedAck;
    // keeps the timeout factor values for each host
    private final int[] timeoutFactors;
    private final List<List<String>> messagesToSend;
    private final List<List<String>> messagesToDeliver;
    UniformReliableBroadcast urb;

    /**
     * Perfect link class ensuring the perfect link properties.
     * Each unique message sent, is delivered exactly once. Uses ACKs to acknowledge messages,
     * detect losses and retransmit in case of a message loss.
     */
    public PerfectLink(UniformReliableBroadcast urb, String srcIP, int srcPort, ArrayList<Host> hosts) throws Exception {
        // uniform reliable broadcast from upper layer (used to deliver messages to)
        this.urb = urb;
        // network parameters
        this.srcIP = InetAddress.getByName(srcIP);
        this.srcPort = srcPort;
        this.socket = new DatagramSocket(this.srcPort, this.srcIP);
        this.hosts = hosts;
        // two blocking queues use for multithreading
        this.receiveQueue = new LinkedBlockingQueue<>();
        this.sendQueue = new LinkedBlockingQueue<>();

        // mostRecentReceivedAck stores at position i the most recent ACK ( = highest sequence number) received from peer i
        this.mostRecentReceivedAck = new int[hosts.size() + 1];

        // nextExpectedAck stores at position i the sequence number corresponding to the next message this local peer wants
        // to receive from peer (at position) i
        this.nextExpectedAck = new int[hosts.size() + 1];

        // sequenceNumbers stores at position i the sequence number of this local peer for messages sent to peer i
        // where each new message has an incremented sequence number
        this.sequenceNumbers = new int[hosts.size() + 1];

        // timeoutFactors contains a factor per peer by which the timeout is multiplied to throttle useless retransmissions
        this.timeoutFactors = new int[hosts.size() + 1];

        Arrays.fill(sequenceNumbers, -1);
        Arrays.fill(timeoutFactors, 1);

        // stores all messages to be sends per peer
        this.messagesToSend = new ArrayList<>();
        // stores all messages to be delivered per peer
        this.messagesToDeliver = new ArrayList<>();
        for (int i = 0; i < hosts.size() + 1; i++) {
            this.messagesToSend.add(new ArrayList<>());
            this.messagesToDeliver.add(new ArrayList<>());
        }

        // stores all messages to be retransmitted after a certain amount of time
        this.timedTimedPackets = new ArrayList<>();
        // starting multithreading and use 4 threads to send, receive and handle packets (timed as well)
        receiveThread = new Thread() {
            public void run() {
                receivePackets();
            }
        };
        sendThread = new Thread() {
            public void run() {
                sendPackets();
            }
        };
        handleThread = new Thread() {
            public void run() {
                handler();
            }
        };
        timerThread = new Thread() {
            public void run() {
                handleTimer();
            }
        };

    }

    public void start() {
        receiveThread.start();
        sendThread.start();
        handleThread.start();
        timerThread.start();
    }

    public void stop() {
        receiveThread.interrupt();
        sendThread.interrupt();
        handleThread.interrupt();
        timerThread.interrupt();
        socket.close();
    }

    /**
     * Delivers a message to the upper layer (urb)
     */
    private void deliver(String message, Integer senderID) {
        urb.plDeliver(message, senderID);
    }

    /**
     * Sends a message to a given destination ID, method used by the upper layer (urb)
     */
    public void send(String message, int destinationID) {
        synchronized (messagesToSend) {
            messagesToSend.get(destinationID).add(message);
        }
        InetAddress destinationIP = null;
        int destinationPort = -1;
        for (Host host : hosts) {
            if (host.getId() == destinationID) {
                destinationIP = host.getInetAddress();
                destinationPort = host.getPort();
            }
        }
        int sequenceNumber = ++sequenceNumbers[destinationID];
        DatagramPacket packet = PacketHelper.createSimpleMessage(message, sequenceNumber, destinationIP, destinationPort);

        sendQueue.add(packet);

        synchronized (timedTimedPackets) {
            timedTimedPackets.add(new TimedPacket(destinationID, sequenceNumber, packet, System.currentTimeMillis()));
        }
    }

    /**
     * Fills the receiveQueue with UDP packets
     */
    private void receivePackets() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(new byte[DATAGRAM_LENGTH], DATAGRAM_LENGTH);
                socket.receive(packet);
                receiveQueue.add(new PacketHelper(packet));
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Consumes the sendQueue and send packets on the UDP socket
     */
    private void sendPackets() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = sendQueue.take();
                socket.send(packet);

            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * This method has a timer for each message sent. When a messages timeouts, it is retransmitted as long
     * as we have not received an acknowledgement for that message or for a more recent message.
     * <p>
     * Timeouts times are peer dependent and are incremented for each timeout, allowing to throttle timeouts
     * for inactive peers. As soon as a message is received for a peer, the peers' timeout time is reset to the
     * initial value, as it means that the peer is probably live.
     */
    private void handleTimer() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (timedTimedPackets) {
                    long now = System.currentTimeMillis();
                    List<TimedPacket> toBeRemoved = new ArrayList<>();
                    List<TimedPacket> toBeRetransmitted = new ArrayList<>();
                    for (TimedPacket timedPacket : timedTimedPackets) {
                        int id = timedPacket.getDestinationID();
                        if (mostRecentReceivedAck[id] > timedPacket.getSequenceNumber()) {
                            toBeRemoved.add(timedPacket);
                        } else {
                            if (now - timedPacket.getSendingTime() >= INITIAL_TIMEOUT * timeoutFactors[id]) {
                                toBeRetransmitted.add(timedPacket);
                                if (timeoutFactors[id] < MAX_TIMEOUT_FACTOR) {
                                    timeoutFactors[id] += 2;
                                }
                            }
                        }
                    }
                    for (TimedPacket timedPacket : toBeRemoved) {
                        timedTimedPackets.remove(timedPacket);
                    }
                    for (TimedPacket timedPacket : toBeRetransmitted) {
                        timedPacket.setSendingTime(now);
                        sendQueue.add(timedPacket.getPacket());
                    }

                }
                // every 10 ms, we check to see if some packets have timed out
                Thread.sleep(TIMER_REFRESH_RATE);
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

    }

    /**
     * Handles the received packets
     * from the receiveQueue
     **/
    private void handler() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                PacketHelper packet = receiveQueue.take();
                int sequenceNumber = packet.getSequenceNumber();
                int id = -1;
                for (Host host : hosts) {
                    if (host.getInetAddress().equals(packet.getIP()) && (host.getPort() == packet.getPort())) {
                        id = host.getId();
                    }
                }
                timeoutFactors[id] = 1;

                if (packet.isACK()) {
                    int ack = packet.getSequenceNumber();

                    // if the ack is not outdated
                    if (ack > mostRecentReceivedAck[id]) {
                        mostRecentReceivedAck[id] = ack;
                        // The ack corresponds to a message in memory
                        if (ack <= sequenceNumbers[id]) {
                            String message;
                            synchronized (messagesToSend) {
                                message = messagesToSend.get(id).get(ack);
                            }
                            DatagramPacket messagePacket = PacketHelper.createSimpleMessage(message, ack, packet.getIP(), packet.getPort());
                            sendQueue.add(messagePacket);
                        }
                        // This ack is outdated, nothing needs to be done
                    }
                    // if the packet is a message
                } else {
                    // this synchronized blocks adds enough elements to the list just before, to fit the received message
                    // at its position corresponding to the sequence number
                    synchronized (messagesToDeliver) {
                        int size = messagesToDeliver.get(id).size();
                        int difference = sequenceNumber - size + 1;
                        if (difference > 0) {
                            messagesToDeliver.get(id).addAll(Collections.nCopies(difference, null));
                        }
                    }
                    if (nextExpectedAck[id] == sequenceNumber) {
                        synchronized (messagesToDeliver) {
                            messagesToDeliver.get(id).set(sequenceNumber, packet.getData());
                            for (int i = sequenceNumber; i < messagesToDeliver.get(id).size(); i++) {
                                String message = messagesToDeliver.get(id).get(i);
                                if (message == null) {
                                    break;
                                }
                                deliver(message, id);
                                nextExpectedAck[id]++;
                            }
                        }
                    }

                    // For any received message (in sequence or not) an ACK is sent with the next expected message sequence
                    DatagramPacket ackPacket = PacketHelper.createACK(nextExpectedAck[id], packet.getIP(), packet.getPort());
                    sendQueue.add(ackPacket);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }


}