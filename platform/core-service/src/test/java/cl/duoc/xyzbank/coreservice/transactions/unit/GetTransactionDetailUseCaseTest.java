package cl.duoc.xyzbank.coreservice.transactions.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionDetailResponse;
import cl.duoc.xyzbank.coreservice.transactions.application.usecases.GetTransactionDetailUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The GetTransactionDetail use case")
class GetTransactionDetailUseCaseTest {

    /*
     * Cases:
     * 1. Returns the detail of an existing transaction
     * 2. Throws not found for an unknown transaction id
     * 3. Throws validation for a malformed transaction id
     */

    @Test
    @DisplayName("returns the detail of an existing transaction")
    void returnsTheDetailOfAnExistingTransaction() {
        Id id = Id.generate();
        Id accountId = Id.generate();
        Transaction transaction = Transaction.create(
                id, accountId, TransactionType.DEBIT,
                Money.create(new BigDecimal("42.00"), "USD"), LocalDate.of(2026, 1, 5), "Groceries");
        InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
        repository.save(transaction);
        GetTransactionDetailUseCase useCase = new GetTransactionDetailUseCase(repository);

        TransactionDetailResponse response = useCase.execute(id.getValue());

        assertEquals(id.getValue(), response.id());
        assertEquals(accountId.getValue(), response.accountId());
        assertEquals("DEBIT", response.type());
        assertEquals(new BigDecimal("42.00"), response.amount());
        assertEquals("USD", response.currency());
        assertEquals("2026-01-05", response.occurredOn());
        assertEquals("Groceries", response.description());
    }

    @Test
    @DisplayName("throws not found for an unknown transaction id")
    void throwsNotFoundForAnUnknownTransactionId() {
        GetTransactionDetailUseCase useCase = new GetTransactionDetailUseCase(new InMemoryTransactionRepository());

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(Id.generate().getValue()));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a malformed transaction id")
    void throwsValidationForAMalformedTransactionId() {
        GetTransactionDetailUseCase useCase = new GetTransactionDetailUseCase(new InMemoryTransactionRepository());

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute("   "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
