package server;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a HTTP request into an passable object
 */
public class Request {
    //string containing the request type
    private String requestType;
    //name of the file that's requested
    private String fileName;

    //stores whether or not the request is a bad request or websocket
    private boolean isWS, isBadReq;

    //stores the web socket key
    private String wsKey;

    /**
     * The constructor checks and determines what type of request is coming
     * from the client and whether or not it is valid.
     * @param request
     */
    public Request(Scanner request) {
        request.useDelimiter("\\r\\n\\r\\n");

        //initialize everything
        isWS = false;
        isBadReq = false;
        wsKey = "";

        //check for bad request
        if(!request.hasNextLine()) {
            requestType = "400BadRequest";
            fileName = "400BadRequest";
            isBadReq = true;
            return;
        }

        //splits up request to check for web socket HS
        String header = request.next();
        String[] req = header.split("\\r\\n");
        Matcher get = Pattern.compile("^GET").matcher(req[0]);

        if(get.find()) {
            Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(header);
            //if is WS request
            if(match.find()) {
                wsKey = match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                isWS = true;
            }else {
                String[] str = req[0].split("\\s+");
                fileName = str[1];
                if(fileName.equals("/")){
                    fileName = "/index.html";
                }
            }
        }else {
            requestType = "400BadRequest";
            fileName = "400BadRequest";
            isBadReq = true;
        }
    }

    /**
     * @return whether or not this request is a WS HS
     */
    public boolean isWebSocket() {
        return isWS;
    }

    /**
     * @return whether or not this request is a bad request
     */
    public boolean isBadReq() {
        return isBadReq;
    }

    /**
     * @return websocket key if req is WS
     */
    public String getWsKey() {
        return isWS ? wsKey : null;
    }

    /**
     * @return type of request
     */
    public String getRequestType(){
        return requestType;
    }

    /**
     * @return the file requested
     */
    public String getFileName(){
        return fileName;
    }
}