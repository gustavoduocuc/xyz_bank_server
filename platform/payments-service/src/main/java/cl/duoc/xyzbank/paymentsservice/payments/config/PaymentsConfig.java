package cl.duoc.xyzbank.paymentsservice.payments.config;

import cl.duoc.xyzbank.paymentsservice.payments.application.GetPaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.MakePaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingGateway;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentRepository;
import cl.duoc.xyzbank.paymentsservice.payments.application.PaymentMetrics;
import cl.duoc.xyzbank.paymentsservice.payments.infrastructure.metrics.MicrometerPaymentMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PaymentsConfig {

    @Bean
    public PaymentMetrics paymentMetrics(MeterRegistry meterRegistry) {
        return new MicrometerPaymentMetrics(meterRegistry);
    }

    @Bean
    public MakePaymentUseCase makePaymentUseCase(
            PaymentRepository paymentRepository, PostingGateway postingGateway, PaymentMetrics paymentMetrics) {
        return new MakePaymentUseCase(paymentRepository, postingGateway, paymentMetrics, Clock.systemUTC());
    }

    @Bean
    public GetPaymentUseCase getPaymentUseCase(PaymentRepository paymentRepository) {
        return new GetPaymentUseCase(paymentRepository);
    }
}
