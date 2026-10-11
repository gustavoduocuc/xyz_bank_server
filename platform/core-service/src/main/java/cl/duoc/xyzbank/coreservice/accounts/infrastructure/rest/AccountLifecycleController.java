package cl.duoc.xyzbank.coreservice.accounts.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CloseAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.OpenAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.UpdateAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.CloseAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.OpenAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.UpdateAccountUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class AccountLifecycleController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final OpenAccountUseCase openAccountUseCase;
    private final UpdateAccountUseCase updateAccountUseCase;
    private final CloseAccountUseCase closeAccountUseCase;

    public AccountLifecycleController(
            OpenAccountUseCase openAccountUseCase,
            UpdateAccountUseCase updateAccountUseCase,
            CloseAccountUseCase closeAccountUseCase) {
        this.openAccountUseCase = openAccountUseCase;
        this.updateAccountUseCase = updateAccountUseCase;
        this.closeAccountUseCase = closeAccountUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody OpenAccountRequest request) {
        return openAccountUseCase.execute(idempotencyKey, request);
    }

    @PatchMapping("/{accountId}")
    public AccountResponse update(
            @PathVariable String accountId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody UpdateAccountRequest request) {
        return updateAccountUseCase.execute(accountId, idempotencyKey, request);
    }

    @PostMapping("/{accountId}/closure")
    public AccountResponse close(
            @PathVariable String accountId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody CloseAccountRequest request) {
        return closeAccountUseCase.execute(accountId, idempotencyKey, request);
    }
}
