package cl.duoc.xyzbank.interestsservice.interests.application.usecases;

import cl.duoc.xyzbank.interestsservice.interests.domain.services.InterestRatePolicy;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.AccountBalanceResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.CreditInterestCommand;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;

import java.math.BigDecimal;

public class ApplyAnnualInterestUseCase {

    private final CoreServicePort coreServicePort;
    private final InterestRatePolicy interestRatePolicy;

    public ApplyAnnualInterestUseCase(CoreServicePort coreServicePort, InterestRatePolicy interestRatePolicy) {
        this.coreServicePort = coreServicePort;
        this.interestRatePolicy = interestRatePolicy;
    }

    public InterestSummaryResponse execute(String accountId, String year) {
        validateAccountId(accountId);
        validateYear(year);
        int yearValue = Integer.parseInt(year);
        AccountBalanceResponse balance = coreServicePort.fetchAccountBalance(accountId);
        BigDecimal openingBalance = balance.balance();
        BigDecimal interestAmount = interestRatePolicy.calculateInterestAmount(openingBalance);
        BigDecimal closingBalance = openingBalance.add(interestAmount);
        String idempotencyKey = "interest-" + accountId + "-" + year;
        coreServicePort.creditInterest(new CreditInterestCommand(
                accountId,
                yearValue,
                interestAmount,
                balance.currency(),
                interestRatePolicy.annualRate(),
                openingBalance,
                closingBalance,
                idempotencyKey));
        return new InterestSummaryResponse(
                accountId,
                yearValue,
                openingBalance,
                closingBalance,
                interestRatePolicy.annualRate(),
                interestAmount,
                balance.currency());
    }

    private void validateAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw DomainException.validation("Account ID is required");
        }
    }

    private void validateYear(String year) {
        if (year == null || year.isBlank()) {
            throw DomainException.validation("Year is required");
        }
        try {
            Integer.parseInt(year);
        } catch (NumberFormatException e) {
            throw DomainException.validation("Year must be a valid number");
        }
    }
}
