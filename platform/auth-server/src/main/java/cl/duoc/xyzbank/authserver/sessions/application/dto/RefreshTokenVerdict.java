package cl.duoc.xyzbank.authserver.sessions.application.dto;

/**
 * What a presented refresh token turned out to be: a login's current token, a token that was
 * already rotated out (reuse), or a token never issued.
 */
public enum RefreshTokenVerdict {
    ACCEPTED,
    REUSED,
    UNKNOWN
}
