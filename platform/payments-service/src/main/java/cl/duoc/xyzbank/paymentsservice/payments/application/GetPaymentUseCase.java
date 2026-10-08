package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentRepository;

import java.util.Objects;

public class GetPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse execute(String paymentId) {
        return paymentRepository.findById(Objects.requireNonNull(Uuids.parse("paymentId", paymentId)))
                .map(PaymentResponse::from)
                .orElseThrow(() -> PaymentException.notFound(paymentId));
    }
}
