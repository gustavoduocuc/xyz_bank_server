package cl.duoc.xyzbank.bffatm.balanceinquiry.unit;

import cl.duoc.xyzbank.bffatm.balanceinquiry.application.dto.BalanceResponse;
import cl.duoc.xyzbank.bffatm.balanceinquiry.application.usecases.BalanceInquiryUseCase;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Balance Inquiry use case")
class BalanceInquiryUseCaseTest {

    @Test
    @DisplayName("returns the account balance and currency")
    void returnsTheAccountBalanceAndCurrency() {
        BalanceResponse expected = new BalanceResponse(new BigDecimal("250.00"), "USD");
        BalanceInquiryUseCase useCase = new BalanceInquiryUseCase(accountId -> expected);

        assertEquals(expected, useCase.execute("account-1"));
    }

    @Test
    @DisplayName("propagates not-found for an unknown account")
    void propagatesNotFoundForAnUnknownAccount() {
        BalanceInquiryUseCase useCase = new BalanceInquiryUseCase(accountId -> {
            throw new CoreServiceCallException(404, "Account not found");
        });

        CoreServiceCallException exception =
                assertThrows(CoreServiceCallException.class, () -> useCase.execute("unknown"));

        assertEquals(404, exception.getStatus());
    }
}
