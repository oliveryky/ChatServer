package server;

/**
 * If server is unable to parse the request a bad resquest exception will be thrown
 */
public class BadRequestException extends Exception {
    public BadRequestException() {
        this("400BadRequestException");
    }

    public BadRequestException(String msg) {
        super(msg);
    }
}