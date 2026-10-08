package cl.duoc.xyzbank.paymentsservice.payments.config;

import cl.duoc.xyzbank.paymentsservice.payments.application.GetPaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.MakePaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingGateway;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PaymentsConfig {

    @Bean
    public MakePaymentUseCase makePaymentUseCase(PaymentRepository paymentRepository, PostingGateway postingGateway) {
        return new MakePaymentUseCase(paymentRepository, postingGateway, Clock.systemUTC());
    }

    @Bean
    public GetPaymentUseCase getPaymentUseCase(PaymentRepository paymentRepository) {
        return new GetPaymentUseCase(paymentRepository);
    }
}
