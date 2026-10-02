package cl.duoc.xyzbank.bffatm.shared.application;

/**
 * A dependency other than core-service (today: the authorization server) could not be reached,
 * timed out, failed, or its circuit is open. Answered with the same 503 ProblemDetail as an
 * unavailable core-service, and never counted against core-service's own breaker or retries.
 */
public class DependencyUnavailableException extends RuntimeException {

    private DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DependencyUnavailableException of(String dependency) {
        return new DependencyUnavailableException(dependency + " is unavailable", null);
    }

    public static DependencyUnavailableException of(String dependency, Throwable cause) {
        return new DependencyUnavailableException(dependency + " is unavailable", cause);
    }
}
