package cl.duoc.xyzbank.coreservice.transactions.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.transactions.application.dto.ListAccountTransactionsRequest;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionPageResponse;
import cl.duoc.xyzbank.coreservice.transactions.application.usecases.ListAccountTransactionsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts/{accountId}/transactions")
public class TransactionController {

    private final ListAccountTransactionsUseCase listAccountTransactionsUseCase;

    public TransactionController(ListAccountTransactionsUseCase listAccountTransactionsUseCase) {
        this.listAccountTransactionsUseCase = listAccountTransactionsUseCase;
    }

    @GetMapping
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
}
