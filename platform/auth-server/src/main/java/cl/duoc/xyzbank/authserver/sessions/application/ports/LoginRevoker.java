package cl.duoc.xyzbank.authserver.sessions.application.ports;

/**
 * Ends a login: its current refresh token, and every token later derived from it, stop working.
 */
public interface LoginRevoker {

    void revoke(String authorizationId);
}
