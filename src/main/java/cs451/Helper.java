package cs451;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Helper {


    /**
     * Returns the integer corresponding to 4 bytes in an array at a given offset
     */
    public static int bytesToInt(byte[] bytes, int offset) {
        int ret = 0;
        for (int i = 0; i < 4 && i + offset < bytes.length; i++) {
            ret <<= 8;
            ret |= (int) bytes[i + offset] & 0xFF;
        }
        return ret;
    }

    /**
     * Returns a String of an array of bytes
     */
    public static String bytesToString(byte[] bytes, int offset, int length) {
        //ISO-8859-1 is the default charset encoding
        return new String(Arrays.copyOfRange(bytes, offset, offset + length), StandardCharsets.ISO_8859_1);
    }


    /**
     * Returns a string of 4 characters corresponding to a 4 bytes int
     */
    public static String intToString(int a) {
        return new String(ByteBuffer.allocate(4).putInt(a).array(), StandardCharsets.ISO_8859_1);
    }

    /**
     * Return the string representation of a Vector clock
     */
    public static String VCToString(int[] VC) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < VC.length; i++)
            b.append(intToString(VC[i]));
        return b.toString();
    }


    /**
     * Return the Vector clock corresponding to a string
     *
     * @param msg
     * @param nbrPeers
     * @return the VC as an int array
     */
    public static int[] stringToVC(String msg, int nbrPeers) {
        int[] VC = new int[nbrPeers];
        byte[] byteMsg = msg.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = 0; i < nbrPeers; i++) {
            VC[i] = bytesToInt(byteMsg, i * 4);
        }
        return VC;
    }

    /**
     * Return message following the Vector Clock
     *
     * @param msg
     * @param nbrPeers
     * @return the String message
     */
    public static String getMessageVC(String msg, int nbrPeers) {
        //If the message is empty
        if (msg.length() == nbrPeers * 4) {
            return "";
        } else {
            return msg.substring(nbrPeers * 4);
        }
    }


}
