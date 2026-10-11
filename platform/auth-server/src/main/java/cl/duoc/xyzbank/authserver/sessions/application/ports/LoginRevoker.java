package cl.duoc.xyzbank.authserver.sessions.application.ports;

import java.util.Optional;

/**
 * Ends a login: its current refresh token, and every token later derived from it, stop working.
 */
public interface LoginRevoker {

    /** Revokes the login and returns the username it belonged to, when it still existed. */
    Optional<String> revoke(String authorizationId);
}
