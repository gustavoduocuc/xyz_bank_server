package cl.duoc.xyzbank.coredomain.transactions.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The Transaction")
class TransactionTest {

    /*
     * Cases:
     * 1. Creates with a valid account id, type, amount, date, and description
     * 2. Exposes its amount as a Money value object
     */

    @Test
    @DisplayName("creates with a valid account id, type, amount, date, and description")
    void createsWithAValidAccountIdTypeAmountDateAndDescription() {
        Id id = Id.generate();
        Id accountId = Id.generate();
        Money amount = Money.create(new BigDecimal("50.00"), "USD");
        LocalDate occurredOn = LocalDate.of(2026, 3, 15);

        Transaction transaction = Transaction.create(
                id, accountId, TransactionType.DEBIT, amount, occurredOn, "Coffee shop");

        assertEquals(id, transaction.getId());
        assertEquals(accountId, transaction.getAccountId());
        assertEquals(TransactionType.DEBIT, transaction.getType());
        assertEquals(amount, transaction.getAmount());
        assertEquals(occurredOn, transaction.getOccurredOn());
        assertEquals("Coffee shop", transaction.getDescription());
    }

    @Test
    @DisplayName("exposes its amount as a Money value object")
    void exposesItsAmountAsAMoneyValueObject() {
        Money amount = Money.create(new BigDecimal("120.75"), "CLP");

        Transaction transaction = Transaction.create(
                Id.generate(), Id.generate(), TransactionType.CREDIT, amount,
                LocalDate.of(2026, 4, 1), null);

        assertEquals(new BigDecimal("120.75"), transaction.getAmount().getAmount());
        assertEquals("CLP", transaction.getAmount().getCurrency());
    }
}
