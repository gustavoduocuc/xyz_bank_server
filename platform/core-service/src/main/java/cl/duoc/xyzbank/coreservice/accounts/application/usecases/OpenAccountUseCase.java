package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.OpenAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectory;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Opens an empty account for a customer that customers-service knows. Repeating an
 * Idempotency-Key returns the account the first request opened, without asking again.
 */
public class OpenAccountUseCase {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final CustomerDirectory customerDirectory;

    public OpenAccountUseCase(AccountRepository accountRepository, CustomerDirectory customerDirectory) {
        this.accountRepository = accountRepository;
        this.customerDirectory = customerDirectory;
    }

    public AccountResponse execute(String idempotencyKey, OpenAccountRequest request) {
        AccountCommands.requireIdempotencyKey(idempotencyKey);
        return accountRepository.findByOpeningIdempotencyKey(idempotencyKey)
                .map(AccountResponse::from)
                .orElseGet(() -> open(idempotencyKey, request));
    }

    private AccountResponse open(String idempotencyKey, OpenAccountRequest request) {
        Id customerId = Id.create(request.customerId());
        requireCurrencyCode(request.currency());
        requireKnownCustomer(customerId);
        Account account = Account.open(
                Id.generate(), newAccountNumber(), customerId, request.currency(), request.alias());
        return AccountResponse.from(accountRepository.saveOpened(account, idempotencyKey));
    }

    private void requireCurrencyCode(String currency) {
        if (currency == null || !CURRENCY.matcher(currency).matches()) {
            throw DomainException.validation("currency must be a three-letter code");
        }
    }

    private void requireKnownCustomer(Id customerId) {
        if (!customerDirectory.exists(customerId.getValue())) {
            throw DomainException.validation("Customer " + customerId.getValue() + " does not exist");
        }
    }

    private static AccountNumber newAccountNumber() {
        return AccountNumber.create(String.format("%010d", RANDOM.nextLong(10_000_000_000L)));
    }
}
