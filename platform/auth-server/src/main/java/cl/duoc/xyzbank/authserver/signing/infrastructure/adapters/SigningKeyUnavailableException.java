package cl.duoc.xyzbank.authserver.signing.infrastructure.adapters;

/**
 * The token signing key could not be loaded. Thrown while the application context starts,
 * so the server refuses to start instead of falling back to an in-memory key.
 */
public class SigningKeyUnavailableException extends RuntimeException {

    public SigningKeyUnavailableException(String message) {
        super(message);
    }

    public SigningKeyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
