package server;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stores, encode and decodes the websocket message
 */
public class WebSocketMsg {
    //byte representation of the decoded msg
    private byte[] decoded;

    //byte representation of the server encoded msg ready to be send
    private byte[] encoded;

    //username and msg of the websocket msg
    private String userName;
    private String msg;

    private boolean isClosed;

    /**
     * default constructor
     * @param cStream
     */
    public WebSocketMsg(InputStream cStream) {
        userName = msg = "";
        isClosed = false;
        decode(cStream);
        encode();
    }

    /**
     * constructor used when getting msgs from database
     * @param userName
     * @param msg
     */
    public WebSocketMsg(String userName, String msg) {
        this.userName = userName;
        this.msg = msg;
        encode();
    }

    /**
     * decodes the incoming message from the client
     * @param cStream
     */
    private void decode(InputStream cStream) {
        //wrap the input stream
        DataInputStream stream = new DataInputStream(cStream);
        try {
            //get header and length bytes
            byte[] header = new byte[2];
            stream.read(header, 0, 2);

            if((header[0] & 0xF) == 8) {
                isClosed = true;
            }

            byte readLen = (byte) (header[1] & (byte) 127);

            int maskIdx = (readLen == (byte) 126) ? 2 : (readLen == (byte) 127 ? 8 : 0);

            //get msg length
            int msgLen = readLen;
            if (maskIdx > 0) {
                byte[] temp = new byte[maskIdx];
                msgLen = stream.read(temp, 0, maskIdx);
            }
            System.out.println("payload: " + msgLen);

            //get the decoding key
            byte[] key = new byte[4];
            for (int i = 0; i < 4; i++) {
                key[i] = stream.readByte();
            }

            //decoded msg
            byte[] msg = new byte[msgLen];
            for (int i = 0; i < msgLen; i++) {
                msg[i] = (byte) (stream.readByte() ^ key[i % 4]);
            }

            decoded = msg;

            String[] temp = new String(msg).split("\\s+", 2);
            if(temp.length > 1) {
                this.userName = temp[0];
                this.msg = temp[1];
            }
        }catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * encodes the decoded msg after being prosssed as a JSON msg
     */
    private void encode() {
        //JSON representation of the msg
        byte[] JSON = getJSON().getBytes();

        int msgLen = JSON.length;

        byte[] header;

        if(msgLen < 126) {
            header = new byte[]{(byte) 129, (byte) msgLen};
        }else if (msgLen < Short.MAX_VALUE) {
            header = new byte[]{(byte) 129, (byte) 126, (byte) ((msgLen >> 8) & (byte) 255), (byte) (msgLen & (byte) 255)};
        }else {
            header = new byte[10];
            header[0] = (byte) 129;
            header[1] = (byte) 127;
            for (int i = 2, j = 56; i < 10; i++, j -= 8) {
                header[i] = (byte) ((msgLen >> j) & (byte) 255);
            }
        }

        encoded = new byte[header.length + JSON.length];

        int idx = 0;

        for(int i = 0; i < header.length; i++, idx++) {
            encoded[i] = header[i];
        }

        for(int i = 0; i < JSON.length; i++) {
            encoded[idx++] = JSON[i];
        }
    }

    /**
     * @return JSON formatted msg as a string
     */
    private String getJSON() {
        return "{ \"user\" : \"" + userName + "\", \"message\" : \"" + msg + "\" }";
    }

    /**
     * @return username of this dataframe
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @return msg of the this dataframe
     */
    public String getMsg() {
        return msg;
    }

    /**
     * @return returns the byte representation of the decoded msg of the dataframe
     */
    public byte[] getDecoded() {return decoded; }

    /**
     * @return the encoded and ready to sent dataframe
     */
    public byte[] getEncoded() {
        return encoded;
    }

    /**
     * @return whether or not this dataframe tells that the connection has been terminated
     */
    public boolean isClosed() {
        return isClosed;
    }
}