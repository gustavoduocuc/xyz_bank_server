package cl.duoc.xyzbank.interestsservice.shared.domain;

public class CoreServiceUnavailableException extends RuntimeException {

    public CoreServiceUnavailableException(String message) {
        super(message);
    }

    public CoreServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
