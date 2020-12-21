package cs451;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Class that encapsulates the current running Process
 */
public class Process {

    private final int selfId;
    private final int numberMessages;
    private final ArrayList<String> logs = new ArrayList<>();
    private final Broadcast broadcast;
    private int countBroadcasted = 0;
    private int countSelfDelivered = 0;
    private boolean stopped = false;
    private String outPath = "";

    public Process(int id, ArrayList<Host> hosts, int numberMessages, List<Integer> dependencies, String outPath) throws Exception {
        this.selfId = id;
        this.numberMessages = numberMessages;
        this.outPath = outPath;
        // Constructing a new LocalCausalBroadcast instance
        broadcast = new LocalCausalBroadcast(hosts, new HashSet(dependencies), id, this);
    }

    public void start() {
        broadcast.run();
        for (int i = 0; i < Constants.BATCH_SIZE && countBroadcasted < numberMessages; ++i) {
            broadcast.broadcast(Constants.EMPTY_MESSAGE);
            logs.add("b " + (i + 1) + "\n");
            countBroadcasted++;
        }
    }

    public void stop() {
        stopped = true;
        broadcast.stop();
        printLogs();
    }

    public void deliver(int id, int sequenceNumber) {
        logs.add("d " + id + " " + sequenceNumber + "\n");
        if ((this.selfId == id) && (++countSelfDelivered == countBroadcasted) && (!stopped)) {
            int maximum = countBroadcasted + Constants.BATCH_SIZE;
            for (int i = countBroadcasted; i < maximum && countBroadcasted < numberMessages; ++i) {
                broadcast.broadcast(Constants.EMPTY_MESSAGE);
                logs.add("b " + (i + 1) + "\n");
                countBroadcasted++;
            }
        }
    }

    /**
     * Prints the logs in an output file.
     */
    public void printLogs() {
        try {
            synchronized (logs) {
                FileWriter writer = new FileWriter(outPath);
                for (String log : logs) {
                    writer.write(log);
                }
                writer.close();
            }
        } catch (Exception e) {
            System.err.format("Exception occurred trying to write output file for process " + selfId);
            e.printStackTrace();
        }
    }
}
