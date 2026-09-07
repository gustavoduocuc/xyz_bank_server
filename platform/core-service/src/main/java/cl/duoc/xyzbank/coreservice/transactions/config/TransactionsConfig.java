package cl.duoc.xyzbank.coreservice.transactions.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.transactions.application.usecases.GetTransactionDetailUseCase;
import cl.duoc.xyzbank.coreservice.transactions.application.usecases.ListAccountTransactionsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionsConfig {

    @Bean
    public ListAccountTransactionsUseCase listAccountTransactionsUseCase(
            AccountRepository accountRepository, TransactionRepository transactionRepository) {
        return new ListAccountTransactionsUseCase(accountRepository, transactionRepository);
    }

    @Bean
    public GetTransactionDetailUseCase getTransactionDetailUseCase(TransactionRepository transactionRepository) {
        return new GetTransactionDetailUseCase(transactionRepository);
    }
}
