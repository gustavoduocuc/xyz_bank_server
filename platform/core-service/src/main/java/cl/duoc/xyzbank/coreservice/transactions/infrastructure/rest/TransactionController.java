package cl.duoc.xyzbank.coreservice.transactions.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.transactions.application.dto.ListAccountTransactionsRequest;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionDetailResponse;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionPageResponse;
import cl.duoc.xyzbank.coreservice.transactions.application.usecases.GetTransactionDetailUseCase;
import cl.duoc.xyzbank.coreservice.transactions.application.usecases.ListAccountTransactionsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final ListAccountTransactionsUseCase listAccountTransactionsUseCase;
    private final GetTransactionDetailUseCase getTransactionDetailUseCase;

    public TransactionController(
            ListAccountTransactionsUseCase listAccountTransactionsUseCase,
            GetTransactionDetailUseCase getTransactionDetailUseCase) {
        this.listAccountTransactionsUseCase = listAccountTransactionsUseCase;
        this.getTransactionDetailUseCase = getTransactionDetailUseCase;
    }

    @GetMapping("/internal/accounts/{accountId}/transactions")
    public TransactionPageResponse list(
            @PathVariable String accountId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize) {
        return listAccountTransactionsUseCase.execute(
                new ListAccountTransactionsRequest(accountId, from, to, type, cursor, pageSize));
    }

    @GetMapping("/internal/transactions/{transactionId}")
    public TransactionDetailResponse getDetail(@PathVariable String transactionId) {
        return getTransactionDetailUseCase.execute(transactionId);
    }
}
