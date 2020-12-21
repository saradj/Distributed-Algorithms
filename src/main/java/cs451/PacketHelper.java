package cs451;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static cs451.Constants.*;

public class PacketHelper {


    private final InetAddress IP;
    private final int port;
    private final DatagramPacket packet;
    private Boolean isACK;
    private int sequenceNumber;
    private String data;

    public PacketHelper(DatagramPacket packet) {
        this.packet = packet;
        this.IP = packet.getAddress();
        this.port = packet.getPort();
        this.readPacketContent();
    }

    /**
     * Returns the Header given a sequence number and if it isAck
     */
    private static byte[] header(int sequenceNumber, Boolean isACK) {

        byte[] seq = ByteBuffer.allocate(4).putInt(sequenceNumber).array();
        byte ackByte = isACK ? (byte) ACK : (byte) NOT_ACK;
        return new byte[]{ackByte, seq[0], seq[1], seq[2], seq[3]};
    }

    //Create a DatagramPacket corresponding to an ACK
    public static DatagramPacket createACK(int sequenceNumber, InetAddress destinationIP, int destinationPort) {
        byte[] content = header(sequenceNumber, true);
        return new DatagramPacket(content, content.length, destinationIP, destinationPort);
    }

    //Create a DatagramPacket corresponding to a Simple Message
    public static DatagramPacket createSimpleMessage(String message, int sequenceNumber, InetAddress destinationIP, int destinationPort) {
        //Merge header and content
        byte[] header = header(sequenceNumber, false);
        byte[] msg = message.getBytes(StandardCharsets.ISO_8859_1);
        //Copy the header and msg in only one array
        int aLen = header.length;
        int bLen = msg.length;
        byte[] content = new byte[aLen + bLen];
        System.arraycopy(header, 0, content, 0, aLen);
        System.arraycopy(msg, 0, content, aLen, bLen);

        return new DatagramPacket(content, content.length, destinationIP, destinationPort);
    }

    //Return true if the packet is an ACK
    public Boolean isACK() {
        return isACK;
    }

    //Return the sequence number
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    //Return the IP Address
    public InetAddress getIP() {
        return IP;
    }


    //Static Methods

    //Return the Port number
    public int getPort() {
        return port;
    }

    //Return the content of a packet
    //It is null if it is an ACK packet
    public String getData() {
        return data;
    }

    //Read packet content in order to know if it is an ACK and to get sequence number and content
    private void readPacketContent() {
        byte[] content = packet.getData();
        //The ACK byte if the first byte
        int ACKbyte = content[0];
        //If the ACkbyte is 6 then the packet is an ACK packet
        if (ACKbyte == ACK) {
            this.isACK = true;
            this.data = null;
        } else {
            this.isACK = false;
            this.data = Helper.bytesToString(content, PACKET_HEADER_SIZE, packet.getLength() - PACKET_HEADER_SIZE);
        }
        this.sequenceNumber = Helper.bytesToInt(content, 1);

    }
}
