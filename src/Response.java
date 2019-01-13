package server;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Stores all the necessary response information and determines what type of response to send based on the request.
 *
 */
public class Response {
    //stores the file for the response if there is one
    private File file;

    //the request this response comes from
    private Request req;

    /**
     * constructor
     *
     * @param req
     */
    Response(Request req) {
        this.req = req;
    }

    /**
     * writes out the appropriate response
     *
     * @param out
     * @throws BadRequestException
     */
    public void write(OutputStream out) throws BadRequestException, IOException {
        //check bad request
        if (req.isBadReq()) {
            write400(out);
            throw new BadRequestException();
        }

        //check for websocket handshake
        if (req.isWebSocket()) {
            writeHS(out, req.getWsKey());
            return;
        }

        //http file request
        file = new File("resources" + req.getFileName());
        //check that file exists
        if (!file.exists() || file.isDirectory()) {
            write404(out);
            return;
        }

        writeContent(out, file);
    }

    /**
     * method to write out a bad request resposne
     *
     * @param out
     */
    private void write400(OutputStream out) throws IOException {
        out.write("HTTP/1.1 400 Bad Request\n".getBytes());
        out.write(("Content-Length: " + "400: Bad Request".getBytes().length + "\n\n").getBytes());
        out.write("400: Bad Request\n".getBytes());
    }

    /**
     * writes out file not fount response
     *
     * @param out
     */
    private void write404(OutputStream out) throws IOException {

        out.write("HTTP/1.1 404 Not Found\n".getBytes());
        out.write(("Content-Length: " + "404: Does Not Exist".getBytes().length + "\n\n").getBytes());
        out.write("404: Does Not Exist\n".getBytes());
    }

    /**
     * writes out the websocket handshake response
     * @param out
     * @param key
     * @throws IOException
     */
    private void writeHS(OutputStream out, String key) throws IOException {
        try {
            //standard WS response template
            byte[] res = ("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Sec-WebSocket-Accept: "
                    + DatatypeConverter
                    .printBase64Binary(
                            MessageDigest
                                    .getInstance("SHA-1")
                                    .digest(key
                                            .getBytes("UTF-8")))
                    + "\r\n\r\n")
                    .getBytes("UTF-8");
            out.write(res, 0, res.length);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * server command to get the time
     * @param out
     * @throws IOException
     */
    private void writeTime(OutputStream out) throws IOException {
        String time = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date());
        out.write("HTTP/1.1 200 OK\n".getBytes());
        out.write(("Content-Length: " + time.getBytes().length + "\n\n").getBytes());
        out.write((time + "\n").getBytes());
    }

    /**
     * writes out the content for a requested file
     * @param out
     * @param file
     * @throws IOException
     */
    private void writeContent(OutputStream out, File file) throws IOException {
        //byte array to store the file as bytes
        byte[] byteFile = new byte[(int) file.length()];

        //use buffered stream instead of reading one byte a time
        BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file));
        stream.read(byteFile, 0, byteFile.length);

        //write stat + header
        out.write("HTTP/1.1 200 OK\n".getBytes());
        out.write(("Content-Length: " + byteFile.length + "\n\n").getBytes());

        //write content body
        out.write(byteFile, 0, byteFile.length);
        stream.close();
    }
}