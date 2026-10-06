package cl.duoc.xyzbank.interestsservice.shared.infrastructure.security;

/**
 * The caller presented no access token, or one interests-service does not accept (401).
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String message) {
        super(message);
    }

    public InvalidAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
