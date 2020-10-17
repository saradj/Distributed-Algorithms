package cs451.perfectLink;

import cs451.Constants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Send {

    MessageHeader message;

    // ThreadSend constructor
    public Send(MessageHeader message) {
        this.message = message;
    }

    private int[] concatenate(int[]... arrays) {
        int length = 0;
        for (int[] array : arrays) {
            length += array.length;
        }
        int[] result = new int[length];
        int pos = 0;
        for (int[] array : arrays) {
            for (int element : array) {
                result[pos] = element;
                pos++;
            }
        }
        return result;
    }

    public void run() {


        byte[] in_data = new byte[32];    // ack packet with no data

        int[] data = {message.messageId,
                message.protocol.ordinal(),
                message.content,
                Process.getInstance().Id,
                message.originalProcessId,
                message.originalMessageId,
                message.fifoId};

        if (message.vectorClock != null){
            data = concatenate(data, message.vectorClock);
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(data);
        byte[] out_data = byteBuffer.array();

        DatagramPacket sendingPacket = new DatagramPacket(out_data, out_data.length, message.destAddress, message.destPort);
        DatagramPacket receivePacket = new DatagramPacket(in_data, in_data.length);
        DatagramSocket socketOut = null;
        try {
            socketOut = Process.getInstance().GetSocketFromQueue();
            //System.out.println("s " + messageId + " port " + socketOut.getLocalPort());

            socketOut.setSoTimeout(Constants.TIMEOUT_VAL);
            boolean isMessageSent = SendMessage(socketOut, sendingPacket, receivePacket, 3);


            if(!isMessageSent)
            {
                InetSocketAddress key = new InetSocketAddress(message.destAddress, message.destPort);
                PerfectLink.messageBuckets.get(key).addFirst(message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Process.getInstance().PutSocketToQuery(socketOut);
            //System.out.println("SendEvent: socketOut added to query!");
        }
    }

    private boolean SendMessage(DatagramSocket socketOut, DatagramPacket sendingPacket, DatagramPacket receivePacket, int attempts) throws IOException {


        int counter = 0;
        while (attempts == -1 || counter < attempts) {

            socketOut.send(sendingPacket);
            try {
                socketOut.receive(receivePacket);
                ByteBuffer wrapped = ByteBuffer.wrap(receivePacket.getData()); // big-endian by default
                int messageId = wrapped.getInt();
                // System.out.println("Ack receive id: " + messageId + " expected :" + this.messageId + " port " + socketOut.getLocalPort());
                if (message.messageId == messageId) {
                    return true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout reached: From Process" + Process.getInstance().Id + " to: " + message.destPort  + " MessageId:" + PerfectLink.messageId + e);
            }
            ++counter;
        }
        return false;
    }
}