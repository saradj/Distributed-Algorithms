package cs451.perfectLink;



import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;


public class PerfectLink {

    private static volatile ConcurrentHashMap<Message, Boolean> receivedMessages;
    volatile static AtomicInteger messageId;

    static volatile LinkedHashMap<InetSocketAddress, ConcurrentLinkedDeque<MessageHeader>> messageBuckets = new LinkedHashMap<>();

    private static volatile ArrayList<ConcurrentLinkedDeque<MessageHeader>> messageBucketsArray = new ArrayList<>();

    private volatile static AtomicInteger currentBucketToSend;
    public final int SenderThreadPoolSize = 1;
    private static ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);// TODO add more threads
    private static volatile PerfectLink perfectLink = new PerfectLink();

    private PerfectLink() {

        //sendEvent = new SendEvent();

        ///deliverEvent = new DeliverEvent();
        receivedMessages = new ConcurrentHashMap<>();
    }

    public static PerfectLink getInst() {

        return perfectLink;
    }

    /**
     * For PerfectLink
     */
    public void Send(int content, InetAddress destAddress, int destPort) {
        int id = messageId.incrementAndGet();
        messageId = new AtomicInteger(0);
        currentBucketToSend = new AtomicInteger(0);
        for (int i = 0; i < SenderThreadPoolSize; i++) {
            threadPool.getQueue().add(new SendThread());
        }

        for (Process.ProcessHeader process : Process.getInstance().processes) {
            ConcurrentLinkedDeque<MessageHeader> dequeu = new ConcurrentLinkedDeque<>();
            messageBuckets.put(new InetSocketAddress(process.address, process.port), dequeu);
            messageBucketsArray.add(dequeu);
        }
        threadPool.prestartAllCoreThreads();
        //sendEvent.SendMessage(content, destAddress, destPort, ProtocolType.PerfectLink, 0, 0, id, 0, null);
    }

    /**
     * For BestEffordBroadcast

    public void Send(int content, InetAddress destAddress, int destPort, ProtocolType protocol, int messageId) {

        sendEvent.SendMessage(content, destAddress, destPort, protocol, 0, 0, messageId, 0, null);
    }

    /**
     * For UniformReliableBroadcast

    public void Send(int content, InetAddress destAddress, int destPort, ProtocolTypeEnum protocol, int originalProcessId, int originalMessageId, int messageId) {

        sendEvent.SendMessage(content, destAddress, destPort, protocol, originalProcessId, originalMessageId, messageId, 0, null);
    }

    /**
     * For FIFOBroadcast

    public void Send(int content, InetAddress destAddress, int destPort, ProtocolTypeEnum protocol, int originalProcessId, int originalMessageId, int messageId, int fifoId) {

        sendEvent.SendMessage(content, destAddress, destPort, protocol, originalProcessId, originalMessageId, messageId, fifoId, null);
    }

    /**
     * For LocalCausalBroadcast

    public synchronized void Send(int content, InetAddress destAddress, int destPort, ProtocolTypeEnum protocol, int originalProcessId, int originalMessageId, int messageId, int[] vectorClock) {

        sendEvent.SendMessage(content, destAddress, destPort, protocol, originalProcessId, originalMessageId, messageId, 0, vectorClock);
    }*/


    public boolean Deliver(Message message, int content, int port, InetAddress address) {

        //deliverEvent.sendAck(port, address, message.getMessageId());
        DatagramSocket socket = null;
        try {

            socket = Process.getInstance().GetSocketFromQueue();

            int[] data = {message.getMessageId()};
            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
            IntBuffer intBuffer = byteBuffer.asIntBuffer();
            intBuffer.put(data);
            byte[] sentData = byteBuffer.array();
            //System.out.println("Ack Send Id: " + messageId);
            socket.send(new DatagramPacket(sentData, sentData.length, address, port));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Process.getInstance().PutSocketToQuery(socket);
        }
        return receivedMessages.putIfAbsent(message, true) == null;
    }


    class SendThread extends Thread {
        public void run() {
            while (true) {

                int currentBucket = currentBucketToSend.getAndUpdate(x -> x >= messageBuckets.size() - 1 ? 0 : currentBucketToSend.get() + 1);

                MessageHeader message = messageBucketsArray.get(currentBucket).poll();
                if (message == null) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                Send s = new Send(message);
                s.run();
            }
        }
    }
}

