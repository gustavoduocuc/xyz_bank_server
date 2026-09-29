package cl.duoc.xyzbank.coreservice.auth.application.ports;

/**
 * The presented token is not an access token core-service accepts: bad signature, wrong
 * issuer or audience, expired, or its client does not match its channel.
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String message) {
        super(message);
    }

    public InvalidAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
