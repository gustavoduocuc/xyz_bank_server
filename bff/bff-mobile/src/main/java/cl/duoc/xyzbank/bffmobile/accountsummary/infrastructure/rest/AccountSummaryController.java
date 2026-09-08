package cl.duoc.xyzbank.bffmobile.accountsummary.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountSummaryResponse;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.usecases.AccountSummaryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountSummaryController {

    private final AccountSummaryUseCase accountSummaryUseCase;

    public AccountSummaryController(AccountSummaryUseCase accountSummaryUseCase) {
        this.accountSummaryUseCase = accountSummaryUseCase;
    }

    @GetMapping("/accounts/{accountId}/summary")
    public AccountSummaryResponse getSummary(@PathVariable String accountId) {
        return accountSummaryUseCase.execute(accountId);
    }
}
