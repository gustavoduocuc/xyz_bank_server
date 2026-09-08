package cl.duoc.xyzbank.bffweb.transactionhistory.config;

import cl.duoc.xyzbank.bffweb.transactionhistory.application.ports.TransactionsPort;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.usecases.TransactionHistoryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionHistoryConfig {

    @Bean
    public TransactionHistoryUseCase transactionHistoryUseCase(TransactionsPort transactionsPort) {
        return new TransactionHistoryUseCase(transactionsPort);
    }
}
