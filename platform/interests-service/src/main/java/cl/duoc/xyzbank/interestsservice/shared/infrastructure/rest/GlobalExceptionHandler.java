package cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest;

import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException exception) {
        HttpStatus status = mapToHttpStatus(exception.getType());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }

    private HttpStatus mapToHttpStatus(DomainException.Type type) {
        return switch (type) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CONFLICT -> HttpStatus.CONFLICT;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case OTHER -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
