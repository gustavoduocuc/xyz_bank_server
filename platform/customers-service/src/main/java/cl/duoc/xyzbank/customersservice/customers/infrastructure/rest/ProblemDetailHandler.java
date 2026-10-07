package cl.duoc.xyzbank.customersservice.customers.infrastructure.rest;

import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ProblemDetailHandler {

    private static final Map<CustomerException.Type, HttpStatus> STATUS_BY_TYPE = Map.of(
            CustomerException.Type.NOT_FOUND, HttpStatus.NOT_FOUND,
            CustomerException.Type.VALIDATION, HttpStatus.BAD_REQUEST,
            CustomerException.Type.VERSION_CONFLICT, HttpStatus.CONFLICT);

    @ExceptionHandler(CustomerException.class)
    public ProblemDetail handleCustomerException(CustomerException exception) {
        return ProblemDetail.forStatusAndDetail(STATUS_BY_TYPE.get(exception.type()), exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }
}
