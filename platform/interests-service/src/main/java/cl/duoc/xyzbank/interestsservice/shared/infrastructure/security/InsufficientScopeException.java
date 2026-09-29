package cl.duoc.xyzbank.interestsservice.shared.infrastructure.security;

/**
 * The caller's valid access token does not grant what the endpoint requires (403).
 */
public class InsufficientScopeException extends RuntimeException {

    public InsufficientScopeException(String message) {
        super(message);
    }
}
