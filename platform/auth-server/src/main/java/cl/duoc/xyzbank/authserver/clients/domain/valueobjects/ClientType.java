package cl.duoc.xyzbank.authserver.clients.domain.valueobjects;

/**
 * How a client proves its identity at the token endpoint: a CONFIDENTIAL client holds a
 * secret, a PUBLIC client holds none and relies on PKCE alone.
 */
public enum ClientType {
    CONFIDENTIAL,
    PUBLIC
}
