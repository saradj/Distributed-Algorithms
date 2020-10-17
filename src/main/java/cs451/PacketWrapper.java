package cs451;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.Charset;

//Class useful to manipulate packet and create some
public class PacketWrapper {

    //Class Variables
    private final int SIZEHEADER = 5;
    //The byte that represents an ACK is 6
    private static final int ACK = 6;
    //The byte that represents a normal message is 0
    private static final int notACK = 0;
    private Boolean isACK;
    private int sequenceNumber;
    private InetAddress IP;
    private int port;
    private DatagramPacket packet;
    private String data;

    //Constructor of PacketWrapper
    public PacketWrapper(DatagramPacket packet) {
        this.packet = packet;
        this.IP = packet.getAddress();
        this.port = packet.getPort();
        this.readPacketContent();
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
    private void readPacketContent(){
        byte[] content = packet.getData();
        //The ACK byte if the first byte
        int ACKbyte = content[0];
        //If the ACkbyte is 6 then the packet is an ACK packet
        if(ACKbyte == ACK){
            this.isACK = true;
            this.data = null;
        } else {
            this.isACK = false;
            this.data = Utils.bytesArraytoString(content,SIZEHEADER,packet.getLength()-SIZEHEADER);
        }
        this.sequenceNumber = Utils.bytesArraytoInt(content,1);

    }


    //Static Methods

    //Return the Header given a sequence number and isAck
    private static byte[] header(int sequenceNumber, Boolean isACK){
        byte[] seq = Utils.intTo4BytesArray(sequenceNumber);
        byte ackByte;

        //Set ackByte to ACK if it is an ACK and to notACK otherwise
        if(isACK){
            ackByte = (byte)ACK;
        }else {
            ackByte = (byte)notACK;
        }
        return new byte[]{ackByte,seq[0],seq[1],seq[2],seq[3]};
    }

    //Create a DatagramPacket corresponding to an ACK
    public static DatagramPacket createACK(int sequenceNumber, InetAddress destinationIP,int destinationPort){
        byte[] content = header(sequenceNumber,true);
        return new DatagramPacket(content, content.length, destinationIP, destinationPort);
    }

    //Create a DatagramPacket corresponding to a Simple Message
    public static DatagramPacket createSimpleMessage(String message, int sequenceNumber, InetAddress destinationIP, int destinationPort){
        //Merge header and content
        byte[] header = header(sequenceNumber,false);
        byte[] msg = message.getBytes(Charset.forName("ISO-8859-1"));
        //Copy the header and msg in only one array
        int aLen = header.length;
        int bLen = msg.length;
        byte[] content = new byte[aLen+bLen];
        System.arraycopy(header, 0, content, 0, aLen);
        System.arraycopy(msg, 0, content, aLen, bLen);

        return new DatagramPacket(content,content.length,destinationIP,destinationPort);
    }
}
