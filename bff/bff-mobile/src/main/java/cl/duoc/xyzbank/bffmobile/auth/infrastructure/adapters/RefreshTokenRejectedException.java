package cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters;

/** The authorization server refused a refresh token: mismatched device, revoked, or reused. */
public class RefreshTokenRejectedException extends RuntimeException {

    public RefreshTokenRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
