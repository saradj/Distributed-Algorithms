package cs451;


import java.util.ArrayList;
import java.util.logging.Level;

public class Main {
    private static Da_proc proc;
    private static void handleSignal() {
        //immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");
        proc.stop();
        //write/flush output file if necessary
        System.out.println("Writing output.");
        proc.printLogs();
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleSignal();
            }
        });
    }

    public static void main(String[] args) throws Exception {
        Parser parser = new Parser(args);
        parser.parse();

        initSignalHandlers();

        // example
        long pid = ProcessHandle.current().pid();
        System.out.println("My PID is " + pid + ".");
        System.out.println("Use 'kill -SIGINT " + pid + " ' or 'kill -SIGTERM " + pid + " ' to stop processing packets.");

        System.out.println("My id is " + parser.myId() + ".");

        System.out.println("List of hosts is:");
        for (Host host: parser.hosts()) {
            System.out.println(host.getId() + ", " + host.getIp() + ", " + host.getPort());
        }

        System.out.println("Barrier: " + parser.barrierIp() + ":" + parser.barrierPort());
        System.out.println("Signal: " + parser.signalIp() + ":" + parser.signalPort());
        System.out.println("Output: " + parser.output());
        // if config is defined; always check before parser.config()
        if (parser.hasConfig()) {
            System.out.println("Config: " + parser.config());
        }
        int nb_messages = 100;


        Coordinator coordinator = new Coordinator(parser.myId(), parser.barrierIp(), parser.barrierPort(), parser.signalIp(), parser.signalPort());
         proc = new Da_proc(parser.myId(), (ArrayList<Host>) parser.hosts(), nb_messages, null, parser.output());
        //Broadcast bc = new FIFOBroadcast((ArrayList<Host>) parser.hosts(), parser.myId(), proc);
	System.out.println("Waiting for all processes for finish initialization");
        coordinator.waitOnBarrier();

	System.out.println("Broadcasting messages...");
//all hosts broadcast to all other here
    proc.start();

	System.out.println("Signaling end of broadcasting messages");
        coordinator.finishedBroadcasting();
        proc.printLogs();

//	while (true) {
//	    // Sleep for 1 hour
//	    Thread.sleep(60 * 60 * 1000);
//	}
    }
}
