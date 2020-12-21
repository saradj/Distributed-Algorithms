package cs451;

public class Payload {
    private final String payload;
    private final int senderID;

    public Payload(String payload, int senderID) {
        this.payload = payload;
        this.senderID = senderID;
    }

    public int getSenderID() {
        return senderID;
    }

    public String getPayload() {
        return payload;
    }
}
