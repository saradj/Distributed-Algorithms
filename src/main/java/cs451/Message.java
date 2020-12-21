package cs451;

public class Message {
    private final int id;
    private final int sequenceNumber;
    private final String content = " "; // we only send empty messages in this project

    public Message(int id, int sequenceNumber) {
        this.id = id;
        this.sequenceNumber = sequenceNumber;
    }

    public int getId() {
        return id;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Message))
            return false;
        Message message = (Message) obj;
        return (message.id == id) && (message.sequenceNumber == sequenceNumber);
    }

    @Override
    public int hashCode() {
        //This has the advantage that if id and sequenceNumber are both in the range 0..2^16-1,
        // we get a unique hashcode for each distinct (unordered) pair.
        int res = Math.max(id, sequenceNumber);
        res = (res << 16) | (res >>> 16);  // exchange top and bottom 16 bits.
        res = res ^ Math.min(id, sequenceNumber);
        return res;
    }
}
