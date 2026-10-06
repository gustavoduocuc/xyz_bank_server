package cl.duoc.xyzbank.authserver.sessions.application.ports;

/**
 * Whether a refresh token is the live refresh token of some login.
 */
public interface CurrentRefreshTokens {

    boolean isCurrent(String refreshToken);
}
