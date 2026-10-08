package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

final class AccountCommands {

    private AccountCommands() {
    }

    static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw DomainException.validation("Idempotency-Key header is required");
        }
    }

    static long requireVersion(Long version) {
        if (version == null) {
            throw DomainException.validation("version is required");
        }
        return version;
    }

    static Account findExistingAccount(AccountRepository accounts, String accountId) {
        return accounts.findById(Id.create(accountId))
                .orElseThrow(() -> DomainException.notFound("Account " + accountId + " not found"));
    }

    /** Re-reads after saving, so the response carries the version persistence assigned. */
    static Account saveAndReload(AccountRepository accounts, Account account) {
        accounts.save(account);
        return accounts.findById(account.getId()).orElseThrow();
    }
}
