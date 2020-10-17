package cs451.perfectLink;


import sun.misc.SignalHandler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Process {

    private volatile static Process process = new Process();

    private volatile ConcurrentLinkedQueue<DatagramSocket> socketQueue = new ConcurrentLinkedQueue <>();

    int amountMessageToSend;

    public int Id;
    public int Port;
    //public Logger Logger;
    public ArrayList<ProcessHeader> processes = new ArrayList<ProcessHeader>();
    public boolean[] dependencies;

    private Process() {
    }

    public static Process getInstance() {
        return process;
    }
    private boolean isDebug(){
        return java.lang.management.ManagementFactory.getRuntimeMXBean().
                getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
    }
    public void Init(int id, String membershipFileName, int amountMessageToSend) {

        Id = id;
        this.amountMessageToSend = amountMessageToSend;
        //Logger = new Logger(Id);
        this.amountMessageToSend = amountMessageToSend;
        ReadSettingFile(membershipFileName);
        if (!isDebug()) {
            SetupSignalHandlers();
        }
    }

    public ProcessHeader GetProcessById(int id) {

        for (int i = 0; i < processes.size(); i++) {
            ProcessHeader currentProcess = processes.get(i);
            if (currentProcess.id == id) {
                return currentProcess;
            }
        }
        return null;
    }

    public DatagramSocket GetSocketFromQueue()
    {
        DatagramSocket socket = socketQueue.poll();

        if (socket == null) {
            try {
                socket = new DatagramSocket();
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        return socket;
    }

    public void PutSocketToQuery(DatagramSocket socket)
    {
        socketQueue.add(socket);
    }
    //region Private Methods
    private void SetupSignalHandlers() {

        ExtendedSignalHandler.install("TERM", (ExtendedSignalHandler) GetTermHandler());
        ExtendedSignalHandler.install("INT", (ExtendedSignalHandler) GetIntHandler());
        ExtendedSignalHandler.install("USR2", (ExtendedSignalHandler) GetUsr2Handler());
    }

    private void ReadSettingFile(String settingFileName) {

        BufferedReader buff = null;
        try {
            buff = new BufferedReader(new FileReader(settingFileName));

            String num = buff.readLine();
            int numOfProcesses = Integer.parseInt(num);
            for (int i = 0; i < numOfProcesses; i++) {
                String process = buff.readLine();
                String[] splitted = process.split("\\s+");
                if (Integer.parseInt(splitted[0]) == Id) {
                    Port = Integer.parseInt(splitted[2]);
                }

                processes.add(new ProcessHeader(Integer.parseInt(splitted[0]), InetAddress.getByName(splitted[1]), Integer.parseInt(splitted[2])));
            }
            dependencies = new boolean[numOfProcesses + 1];

            for (int i = 0; i < numOfProcesses; i++) {
                String process = buff.readLine();
                String[] splitted = process.split("\\s+");
                if (Integer.parseInt(splitted[0]) == Id){
                    for(int j =1; j < splitted.length; j++ ){
                        int processId = Integer.parseInt(splitted[j]);
                        dependencies[processId] = true;
                    }
                }
            }
            dependencies[Id] = true;
        } catch (Exception e) {
            System.out.println("Exception while parsing file:" + e);
        }
    }
    //endregion

    //region Signal Handlers
    private SignalHandler GetTermHandler() {

        return sig -> {
            System.out.println("TERM");
            //Logger.WriteLogToFile();
            System.exit(-1);
        };
    }

    private SignalHandler GetIntHandler() {

        return sig -> {
            System.out.println("INT");
            //Logger.WriteLogToFile();
            System.exit(-1);
        };
    }

    private SignalHandler GetUsr2Handler() {

        return sig -> {
            System.out.println("USR2");
            for (int i = 1; i <= amountMessageToSend; i++) {
                //TODO SARA LocalCausalBroadcast.getInst().Broadcast(i);
            }
        };
    }
    //endregion
    public class ProcessHeader {
        public int port;
        public InetAddress address;
        public int id;

        public ProcessHeader(int id, InetAddress address, int port) {
            this.port = port;
            this.address = address;
            this.id = id;
        }
    }
}

