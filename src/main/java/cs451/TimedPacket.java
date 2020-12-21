package cs451;

import java.net.DatagramPacket;

public class TimedPacket {
    private final int destinationID;
    private final int sequenceNumber;
    private final DatagramPacket packet;
    private long sendingTime;

    public TimedPacket(int destinationID, int sequenceNumber, DatagramPacket packet, long sendingTime) {
        this.destinationID = destinationID;
        this.sequenceNumber = sequenceNumber;
        this.packet = packet;
        this.sendingTime = sendingTime;
    }

    public DatagramPacket getPacket() {
        return packet;
    }

    public int getDestinationID() {
        return destinationID;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public long getSendingTime() {
        return sendingTime;
    }

    public void setSendingTime(long sendingTime) {
        this.sendingTime = sendingTime;
    }

}
