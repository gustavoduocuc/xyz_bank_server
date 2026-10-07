package cl.duoc.xyzbank.coreservice.accounts.application.ports;

public class CustomerDirectoryUnavailableException extends RuntimeException {

    public CustomerDirectoryUnavailableException(String message) {
        super(message);
    }

    public CustomerDirectoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
