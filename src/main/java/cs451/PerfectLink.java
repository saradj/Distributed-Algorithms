package cs451;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PerfectLink {
    UniformReliableBroadcast urb;

    private final int DATAGRAM_LENGTH = 1024;
    private final int INITIAL_TIMEOUT = 100;
    private final int MAXIMUM_TIMEOUT_FACTOR = 100;
    private final int TIMER_REFRESH_RATE = 10;

    private HashMap<Pair<Integer, Integer>, Pair<DatagramPacket, Long>> timerPackets;
    private InetAddress sourceIP;
    private int sourcePort;
    private DatagramSocket socket;
    private Map<Integer, Pair<InetAddress, Integer>> peers;
    private Map<Pair<InetAddress, Integer>, Integer> peersInverse;
    private Thread t1;
    private Thread t2;
    private Thread t3;
    private Thread t4;
    private BlockingQueue<PacketWrapper> receiveQueue;
    private BlockingQueue<DatagramPacket> sendQueue;
    // the array of sequence numbers for messages to be sent per peer
    private int[] sequenceNumbers;
    // the array of ACKS the local machine wants to receive messages for per peer
    private int[] localAcks;
    // the array of the last (highest value) ACK received per peer
    private int[] remoteAcks;
    private int[] timeoutFactors;
    private List<List<String>> messagesToSend;
    private List<List<String>> messagesToDeliver;

    /* Perfect link class that ensures perfect link properties.
       Each message on this layer is uniquely identified with a peer ID (int) and a sequence number (int)
       ACKS are used to acknowledge messages and timeouts are used to detect losses and trigger retransmissions.
     */
    public PerfectLink(UniformReliableBroadcast urb, String sourceIP, int sourcePort, ArrayList<Host> peers) throws Exception {
        // uniform reliable broadcast from upper layer (used to deliver messages to)
        this.urb = urb;

        // network parameters
        this.sourceIP = InetAddress.getByName(sourceIP);
        this.sourcePort = sourcePort;
        System.out.println("src ip " +sourceIP + "src prot " + sourcePort);
        this.socket = new DatagramSocket(this.sourcePort, this.sourceIP);

        // peers maps a peer ID (int) to the corresponding IP/port pair
        this.peers = resolveAddresses(peers);
        Map<Pair<InetAddress, Integer>, Integer> peersInverse = new HashMap<>();
        for (Map.Entry<Integer, Pair<InetAddress, Integer>> entry : this.peers.entrySet()) {
            peersInverse.put(entry.getValue(), entry.getKey());
        }

        // peersInverse maps and IP/port pair to the corresponding peer ID (int)
        this.peersInverse = peersInverse;

        // two blocking queues for clean multithreading
        this.receiveQueue = new LinkedBlockingQueue<>();
        this.sendQueue = new LinkedBlockingQueue<>();

        // localAcks stores at position i the sequence number corresponding to the next message this local peer wants
        // to receive from peer i
        this.localAcks = new int[peers.size() + 1];

        // remoteAcks stores at position i the most recent ACK ( = highest sequence number) received from peer i
        this.remoteAcks = new int[peers.size() + 1];

        // sequenceNumbers stores at position i the sequence number of this local peer for messages sent to peer i
        // where each new message has an incremented sequence number
        this.sequenceNumbers = new int[peers.size() + 1];
        Arrays.fill(sequenceNumbers, -1);

        // timeoutFactors contains a factor per peer by which the timeout is multiplied to throttle useless retransmissions
        this.timeoutFactors = new int[peers.size() + 1];
        Arrays.fill(timeoutFactors, 1);

        // stores all messages to be sends per peer
        this.messagesToSend = new ArrayList<>();
        for (int i = 0; i < peers.size() + 1; i++) {
            this.messagesToSend.add(new ArrayList<>());
        }

        // stores all messages to be delivered per peer
        this.messagesToDeliver = new ArrayList<>();
        for (int i = 0; i < peers.size() + 1; i++) {
            this.messagesToDeliver.add(new ArrayList<>());
        }

        // contains all messages to be retransmitted after a given amount of time
        this.timerPackets = new HashMap<>();
        // start 4 different threads
        // multithreading was not necessary but definitely simplifies the code and may improve performance
        t1 = new Thread() {
            public void run() {
                receiveLoop();
            }
        };
        t2 = new Thread() {
            public void run() {
                sendLoop();
            }
        };
        t3 = new Thread() {
            public void run() {
                handler();
            }
        };
        t4 = new Thread() {
            public void run() {
                handleTimer();
            }
        };

    }

    public void start() {
        System.out.println("starting perfect link in parallel?");
        t1.start();
        t2.start();
        t3.start();
        t4.start();
    }

    public void stop() {
        t1.interrupt();
        t2.interrupt();
        t3.interrupt();
        t4.interrupt();
        socket.close();
    }

    // delivers a message to the upper layer
    private void deliver(String message, Integer senderID) {
        urb.plDeliver(message, senderID);
    }

    // send a message to a given peer, method used by the upper layer
    public void send(String message, int destinationID) {
        synchronized (messagesToSend) {
            messagesToSend.get(destinationID).add(message);
        }

        InetAddress destinationIP = peers.get(destinationID).first;
        int destinationPort = peers.get(destinationID).second;
        int sequenceNumber = ++sequenceNumbers[destinationID];
        DatagramPacket packet = PacketWrapper.createSimpleMessage(message, sequenceNumber, destinationIP, destinationPort);

        sendQueue.add(packet);

        synchronized (timerPackets) {
            timerPackets.put(Pair.of(destinationID, sequenceNumber), Pair.of(packet, System.currentTimeMillis()));
        }
    }

    /*
        This method has a timer for each message sent. When a messages timeouts, it is retransmitted as long
        as we have not received an acknowledgement for that message or for a more recent message.

        Timeouts times are peer dependent and are incremented for each timeout, allowing to throttle timeouts
        for inactive peers. As soon as a message is received for a peer, the peers' timeout time is reset to the
        initial value, as it means that the peer is probably live.
     */
    private void handleTimer() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (timerPackets) {
                    long now = System.currentTimeMillis();
                    List<Pair<Integer, Integer>> toBeRemoved = new ArrayList<>();
                    List<Pair<Integer, Integer>> toBeRetransmitted = new ArrayList<>();
                    for (Pair<Integer, Integer> id_m : timerPackets.keySet()) {
                        if (remoteAcks[id_m.first] > id_m.second) {
                            toBeRemoved.add(id_m);
                        } else {
                            if (now - timerPackets.get(id_m).second >= INITIAL_TIMEOUT * timeoutFactors[id_m.first]) {
                                toBeRetransmitted.add(id_m);
                                if (timeoutFactors[id_m.first] < MAXIMUM_TIMEOUT_FACTOR) {
                                    timeoutFactors[id_m.first] += 2;
                                }
                            }
                        }
                    }
                    for (Pair<Integer, Integer> id_m : toBeRemoved) {
                        timerPackets.remove(id_m);
                    }
                    for (Pair<Integer, Integer> id_m : toBeRetransmitted) {
                        DatagramPacket packet = timerPackets.get(id_m).first;
                        timerPackets.put(id_m, Pair.of(packet, now));
                        sendQueue.add(packet);
                    }


                }
                // every 10 ms, we check to see if some packets have timed out
                Thread.sleep(TIMER_REFRESH_RATE);
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

    }

    /*
        Handles received packets by consuming them from the receiveQueue.
    */
    private void handler() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                PacketWrapper packet = receiveQueue.take();
                int sequenceNumber = packet.getSequenceNumber();
                int id = peersInverse.get(Pair.of(packet.getIP(), packet.getPort()));
                timeoutFactors[id] = 1;
                // ***** CASE 1 - THE PACKET IS A AN ACK *****
                if (packet.isACK()) {
                    int ack = packet.getSequenceNumber();

                    // This ack is not outdated
                    if (ack > remoteAcks[id]) {
                        remoteAcks[id] = ack;
                        // The ack corresponds to a message in memory
                        if (ack <= sequenceNumbers[id]) {
                            String message;
                            synchronized (messagesToSend) {
                                message = messagesToSend.get(id).get(ack);
                            }
                            DatagramPacket messagePacket = PacketWrapper.createSimpleMessage(message, ack, packet.getIP(), packet.getPort());
                            sendQueue.add(messagePacket);
                        }
                        // This ack is outdated, nothing needs to be done
                    } else {
                    }
                    // ***** CASE 2 - THE PACKET IS A MESSAGE *****
                } else {
                    // this synchronized blocks adds enough elements to the list to fit the received message
                    // at its position corresponding to the sequence numbers.
                    synchronized (messagesToDeliver) {
                        int size = messagesToDeliver.get(id).size();
                        int difference = sequenceNumber - size + 1;
                        if (difference > 0) {
                            messagesToDeliver.get(id).addAll(Collections.nCopies(difference, null));
                        }
                    }
                    // The message was the expected message in sequence.
                    if (localAcks[id] == sequenceNumber) {
                        synchronized (messagesToDeliver) {
                            messagesToDeliver.get(id).set(sequenceNumber, packet.getData());
                            for (int i = sequenceNumber; i < messagesToDeliver.get(id).size(); i++) {
                                String message = messagesToDeliver.get(id).get(i);
                                if (message == null) {
                                    break;
                                }
                                deliver(message, id);
                                localAcks[id]++;
                            }
                        }
                    }
                    // The message was not expected, either with higher or lower sequence number.
                    // nothing special is to be done
                    else {
                    }

                    // For any received message (in sequence or not) an ACK is sent with the next expected message sequence
                    DatagramPacket ackPacket = PacketWrapper.createACK(localAcks[id], packet.getIP(), packet.getPort());
                    sendQueue.add(ackPacket);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    // this method runs in a separate thread, it listens to the UDP sockets and add packets to the receiveQueue
    private void receiveLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("trying to receive trough perfect link");

                DatagramPacket packet = new DatagramPacket(new byte[DATAGRAM_LENGTH], DATAGRAM_LENGTH);
                socket.receive(packet);
                receiveQueue.add(new PacketWrapper(packet));
                System.out.println("received smth!");
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    // this method runs in a separate thread, it consumes the sendQueue and send packets on the UDP socket
    private void sendLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("trying to send trough perfect link");

                DatagramPacket packet = sendQueue.take();
                socket.send(packet);
                System.out.println("sent a packet!");

            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    // computes the peers map
    private HashMap<Integer, Pair<InetAddress, Integer>> resolveAddresses(ArrayList<Host> hosts) throws Exception {
        HashMap<Integer, Pair<InetAddress, Integer>> peers = new HashMap<>();
        for (Host host: hosts) {
            Integer id = host.getId();
            peers.put(id, Pair.of(InetAddress.getByName(host.getIp()), host.getPort()));
        }
        return peers;
    }
}