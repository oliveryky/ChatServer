package server;

public class BadRequestException extends Exception {
    public BadRequestException() {
        this("400BadRequestException");
    }

    public BadRequestException(String msg) {
        super(msg);
    }
}