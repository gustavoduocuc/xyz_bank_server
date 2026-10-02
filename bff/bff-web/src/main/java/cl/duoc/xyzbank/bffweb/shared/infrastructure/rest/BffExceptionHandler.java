package cl.duoc.xyzbank.bffweb.shared.infrastructure.rest;

import cl.duoc.xyzbank.bffweb.shared.application.DependencyUnavailableException;
import cl.duoc.xyzbank.bffweb.shared.application.RequestRejectedException;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BffExceptionHandler {

    // The detail CoreServiceCalls already gives every unavailable downstream, interests-service included
    private static final String CORE_SERVICE_UNAVAILABLE = "Core service is unavailable";

    @ExceptionHandler(CallerIdentityException.class)
    public ProblemDetail handleCallerIdentity(CallerIdentityException exception) {
        HttpStatus status = exception.getType() == CallerIdentityException.Type.INVALID
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.FORBIDDEN;
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }

    @ExceptionHandler(RequestRejectedException.class)
    public ProblemDetail handleRequestRejected(RequestRejectedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ProblemDetail handleDependencyUnavailable(DependencyUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    /** An open circuit answers like the dependency being down: the same 503, with nothing cached. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleOpenCircuit(CallNotPermittedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, CORE_SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(CoreServiceCallException.class)
    public ProblemDetail handleCoreServiceCall(CoreServiceCallException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(exception.getStatus()), exception.getMessage());
    }
}
