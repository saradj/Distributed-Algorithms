package cs451;

import java.util.ArrayList;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

//Main class of the project which represents a process
public class Da_proc {

    public static void main(int id_process, int nb_msg, ArrayList<Host> hosts, String outPath) throws Exception {
        String membership = "";
        //Check the number of arguments



        //-- Use ParserMembership class to parse the membership table --

        Da_proc process = new Da_proc(id_process,hosts,nb_msg, null, outPath);
        process.start();
        terminate.await();
    }


    //Class Variables
    private CountDownLatch wait = new CountDownLatch(1);
    private static CountDownLatch terminate = new CountDownLatch(1);
    private int numberMessages;
    private int selfId;
    private ArrayList<Pair<Integer,Integer>> logs = new ArrayList<>();
    private Broadcast BC;
    private int countBroadcasted = 0;
    private int countSelfDelivered = 0;
    private final int BATCH_SIZE = 5000;
    private boolean stopped = false;
    private String outPath = "";

    //Constructor of Da_proc
    public Da_proc(int id,ArrayList<Host> hosts, int numberMessages, List<Integer> dependencies, String outPath) throws Exception{
        this.selfId = id;
        this.numberMessages = numberMessages;
        this.outPath = outPath;
        // We instantiate a Localized Causal Broadcast
        //BC = new LCBroadcast(membership, dependencies, id,this);
        BC = new FIFOBroadcast(hosts,id, this);
        signalHandling();
    }


    //Start the process and wait until the USR2 signal is received, when USR2 is received, it will start broadcast
    public void start() throws Exception{
        BC.start();
        //Waiting to get USR2
        //wait.await();
        for (int i = 0; i < BATCH_SIZE && countBroadcasted < numberMessages; ++i){
            BC.broadcast(" ");
            logs.add(Pair.of(-1,i + 1));
            System.out.println("log adding b and " + (i+1));
            countBroadcasted++;
        }
    }

    //Method invoked when the signal USR2 is received, it unlocks the "wait" CountDownLatch
    public void usr2Signal(){
        wait.countDown();
    }


    //Method invoked when the signal SIGTERM or SIGINT is received
    public void stop(){
        stopped = true;
        //wait.countDown();
        BC.stop();
        printLogs();
        terminate.countDown();

    }

    //Handling TERM, INT and USR2 signals
    public void signalHandling(){
        DebugSignalHandler signalhandler = new DebugSignalHandler(this);
        signalhandler.listenTo("TERM");
        signalhandler.listenTo("USR2");
        signalhandler.listenTo("INT");
    }

    //Callback method for FIFO
    public void deliver(int id, int sequenceNumber, String message){
        //In this part of the project, the message is empty
        logs.add(Pair.of(id,sequenceNumber));
        System.out.println("log adding d and id " + id +" seq numb "+sequenceNumber);

        if((this.selfId == id) && (++countSelfDelivered == countBroadcasted) && (!stopped)){
            int maximum = countBroadcasted + BATCH_SIZE;
            for (int i = countBroadcasted; i < maximum && countBroadcasted < numberMessages; ++i){
                BC.broadcast(" ");
                logs.add(Pair.of(-1,i + 1));
                System.out.println("log adding b and " + (i+1));

                countBroadcasted++;
            }
        }
    }

    //Print the log file in a output file.
    public void printLogs() {
        try {
            synchronized (logs) {

                String namefile = "myout" + selfId + ".out";
                int size = logs.size();
                FileWriter writer = new FileWriter(outPath);
                for (int i = 0; i < size; i++) {
                    Pair<Integer, Integer> l = logs.get(i);
                    if (l.first == -1) {
                        writer.write("b " + l.second + "\n");
                    } else {
                        writer.write("d " + l.first + " " + l.second + "\n");
                    }
                }
            writer.close();
            }
        } catch (Exception e) {
            System.err.format("Exception occurred trying to write output file for process "+ selfId);
            e.printStackTrace();
        }
    }
}
