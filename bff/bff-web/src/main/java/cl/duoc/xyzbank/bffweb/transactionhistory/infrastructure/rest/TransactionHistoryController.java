package cl.duoc.xyzbank.bffweb.transactionhistory.infrastructure.rest;

import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.usecases.TransactionHistoryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionHistoryController {

    private final TransactionHistoryUseCase transactionHistoryUseCase;

    public TransactionHistoryController(TransactionHistoryUseCase transactionHistoryUseCase) {
        this.transactionHistoryUseCase = transactionHistoryUseCase;
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public TransactionHistoryResponse getHistory(
            @PathVariable String accountId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize) {
        return transactionHistoryUseCase.execute(accountId, from, to, type, cursor, pageSize);
    }
}
