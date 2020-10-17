package cs451.perfectLink;

import java.net.InetAddress;

public class Message {
    private int messageId;
    private int processId;

    public Message(int messageId, int processId) {
        this.messageId = messageId;
        this.processId = processId;
    }

    public int getMessageId() {
        return messageId;
    }

    public int getProcessId() {
        return processId;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        if(message.messageId == this.messageId&& message.processId == this.processId)
            return true;
        return hashCode() == message.hashCode();
    }

    public int cantorPairing() {

        int sum = this.messageId + this.processId;
        if (sum % 2 == 0) sum = sum / 2 * (sum + 1);
        else sum = (sum + 1) / 2 * sum;
        int cantorValue = sum + this.processId;
        return cantorValue;
    }

    @Override
    public int hashCode() {
        return this.cantorPairing();
    }
}

class MessageHeader {
    public int destPort;
    public InetAddress destAddress;
    public int content;
    public int messageId;
    public ProtocolType protocol;
    public int originalProcessId;
    public int originalMessageId;
    public int fifoId;
    public int[] vectorClock;

    public MessageHeader(int destPort, InetAddress destAddress, int content, int messageId, ProtocolType protocol, int originalProcessId, int originalMessageId, int fifoId, int[] vectorClock)
    {
        this.destPort = destPort;
        this.destAddress = destAddress;
        this.content = content;
        this.messageId = messageId;
        this.protocol = protocol;
        this.originalProcessId = originalProcessId;
        this.originalMessageId = originalMessageId;
        this.fifoId = fifoId;
        this.vectorClock = vectorClock;
    }
}