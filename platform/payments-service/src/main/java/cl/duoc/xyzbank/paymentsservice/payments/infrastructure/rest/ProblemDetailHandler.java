package cl.duoc.xyzbank.paymentsservice.payments.infrastructure.rest;

import cl.duoc.xyzbank.paymentsservice.payments.application.CoreUnavailableException;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ProblemDetailHandler {

    private static final Map<PaymentException.Type, HttpStatus> STATUS_BY_TYPE = Map.of(
            PaymentException.Type.NOT_FOUND, HttpStatus.NOT_FOUND,
            PaymentException.Type.VALIDATION, HttpStatus.BAD_REQUEST,
            PaymentException.Type.CONFLICT, HttpStatus.CONFLICT);

    @ExceptionHandler(PaymentException.class)
    public ProblemDetail handlePaymentException(PaymentException exception) {
        return ProblemDetail.forStatusAndDetail(STATUS_BY_TYPE.get(exception.type()), exception.getMessage());
    }

    // The payment stays PENDING; repeating the request with the same key completes it
    @ExceptionHandler(CoreUnavailableException.class)
    public ProblemDetail handleCoreUnavailable(CoreUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "core-service is unavailable; the payment is PENDING, retry with the same Idempotency-Key");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }
}
