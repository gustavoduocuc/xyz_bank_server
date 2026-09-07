package cl.duoc.xyzbank.coreservice.accounts.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountBalanceResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetAccountBalanceUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class AccountController {

    private final GetAccountBalanceUseCase getAccountBalanceUseCase;

    public AccountController(GetAccountBalanceUseCase getAccountBalanceUseCase) {
        this.getAccountBalanceUseCase = getAccountBalanceUseCase;
    }

    @GetMapping("/{accountId}/balance")
    public AccountBalanceResponse getBalance(@PathVariable String accountId) {
        return getAccountBalanceUseCase.execute(accountId);
    }
}
