package cs451;

public class Constants {

    public static final int ARG_LIMIT_NO_CONFIG = 10;
    public static final int ARG_LIMIT_CONFIG = 11;

    // indexes for id
    public static final int ID_KEY = 0;
    public static final int ID_VALUE = 1;

    // indexes for hosts
    public static final int HOSTS_KEY = 2;
    public static final int HOSTS_VALUE = 3;

    // indexes for barrier
    public static final int BARRIER_KEY = 4;
    public static final int BARRIER_VALUE = 5;

    // indexes for signal
    public static final int SIGNAL_KEY = 6;
    public static final int SIGNAL_VALUE = 7;

    // indexes for output
    public static final int OUTPUT_KEY = 8;
    public static final int OUTPUT_VALUE = 9;

    // indexes for config
    public static final int CONFIG_VALUE = 10;

    public static final int DATAGRAM_LENGTH = 1024;
    public static final int INITIAL_TIMEOUT = 100;
    public static final int MAX_TIMEOUT_FACTOR = 100;
    public static final int TIMER_REFRESH_RATE = 10;

    public static final int PACKET_HEADER_SIZE = 5;
    //The byte that represents an ACK is 6
    public static final int ACK = 6;
    //The byte that represents a normal message is 0
    public static final int NOT_ACK = 0;
    public static final int BATCH_SIZE = 5000;
    public static final String EMPTY_MESSAGE = " ";

}
