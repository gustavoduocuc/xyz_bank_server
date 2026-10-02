package cl.duoc.xyzbank.interestsservice.shared.domain;

/**
 * interests-service could not obtain its own service token from the authorization server
 * (unreachable, timed out, failed, refused, or its circuit is open). core-service is then never
 * called, so this is not a core-service failure and is ignored by core-service's breaker and
 * retries (interests spec, "interests-service's service token request is retried and protected on
 * its own").
 */
public class ServiceTokenUnavailableException extends RuntimeException {

    public ServiceTokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
