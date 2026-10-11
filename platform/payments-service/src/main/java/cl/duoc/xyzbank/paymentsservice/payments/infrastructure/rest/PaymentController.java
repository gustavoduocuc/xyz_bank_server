package cl.duoc.xyzbank.paymentsservice.payments.infrastructure.rest;

import cl.duoc.xyzbank.paymentsservice.payments.application.GetPaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.MakePaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.PaymentRequest;
import cl.duoc.xyzbank.paymentsservice.payments.application.PaymentResponse;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** A POST answers 201 with the payment once core-service has applied (COMPLETED) or refused (REJECTED) it. */
@RestController
public class PaymentController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final MakePaymentUseCase makePaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;

    public PaymentController(MakePaymentUseCase makePaymentUseCase, GetPaymentUseCase getPaymentUseCase) {
        this.makePaymentUseCase = makePaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
    }

    @PostMapping("/internal/transfers")
    public ResponseEntity<PaymentResponse> transfer(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody TransferBody body) {
        return makePayment(idempotencyKey, new PaymentRequest(PaymentType.TRANSFER,
                body.sourceAccountId(), body.destinationAccountId(), body.amount(), body.currency()));
    }

    @PostMapping("/internal/deposits")
    public ResponseEntity<PaymentResponse> deposit(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody DepositBody body) {
        return makePayment(idempotencyKey, new PaymentRequest(PaymentType.DEPOSIT,
                null, body.destinationAccountId(), body.amount(), body.currency()));
    }

    @PostMapping("/internal/bill-payments")
    public ResponseEntity<PaymentResponse> billPayment(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody BillPaymentBody body) {
        return makePayment(idempotencyKey, new PaymentRequest(PaymentType.BILL_PAYMENT,
                body.sourceAccountId(), null, body.amount(), body.currency()));
    }

    @GetMapping("/internal/payments/{paymentId}")
    public PaymentResponse get(@PathVariable String paymentId) {
        return getPaymentUseCase.execute(paymentId);
    }

    private ResponseEntity<PaymentResponse> makePayment(String idempotencyKey, PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(makePaymentUseCase.execute(idempotencyKey, request));
    }

    public record TransferBody(String sourceAccountId, String destinationAccountId, BigDecimal amount, String currency) {
    }

    public record DepositBody(String destinationAccountId, BigDecimal amount, String currency) {
    }

    public record BillPaymentBody(String sourceAccountId, BigDecimal amount, String currency) {
    }
}
