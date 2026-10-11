package cl.duoc.xyzbank.paymentsservice.payments.application;

/** core-service could not answer a posting, even after retries, or its circuit is open. */
public class CoreUnavailableException extends RuntimeException {

    public CoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
