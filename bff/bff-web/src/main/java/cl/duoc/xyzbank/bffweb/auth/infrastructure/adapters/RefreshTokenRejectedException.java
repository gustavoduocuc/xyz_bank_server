package cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters;

/** The authorization server refused a refresh token: expired, revoked, or reused after rotation. */
public class RefreshTokenRejectedException extends RuntimeException {

    public RefreshTokenRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
