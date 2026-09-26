package cl.duoc.xyzbank.interestsservice.interests.application.usecases;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCalculatedNotice;
import cl.duoc.xyzbank.interestsservice.interests.application.ports.InterestCalculatedPublisher;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import cl.duoc.xyzbank.interestsservice.interests.domain.services.InterestRatePolicy;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.AccountBalanceResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.CreditInterestCommand;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public class ApplyAnnualInterestUseCase {

    private final CoreServicePort coreServicePort;
    private final InterestRatePolicy interestRatePolicy;
    private final boolean creditViaKafka;
    private final InterestCalculationRepository calculations;
    private final InterestCalculatedPublisher publisher;

    public ApplyAnnualInterestUseCase(CoreServicePort coreServicePort, InterestRatePolicy interestRatePolicy) {
        this(coreServicePort, interestRatePolicy, false, new UntrackedCalculations(), notice -> {
        });
    }

    public ApplyAnnualInterestUseCase(
            CoreServicePort coreServicePort,
            InterestRatePolicy interestRatePolicy,
            InterestCalculationRepository calculations,
            InterestCalculatedPublisher publisher) {
        this(coreServicePort, interestRatePolicy, true, calculations, publisher);
    }

    private ApplyAnnualInterestUseCase(
            CoreServicePort coreServicePort,
            InterestRatePolicy interestRatePolicy,
            boolean creditViaKafka,
            InterestCalculationRepository calculations,
            InterestCalculatedPublisher publisher) {
        this.coreServicePort = coreServicePort;
        this.interestRatePolicy = interestRatePolicy;
        this.creditViaKafka = creditViaKafka;
        this.calculations = calculations;
        this.publisher = publisher;
    }

    public InterestSummaryResponse execute(String accountId, String year) {
        validateAccountId(accountId);
        validateYear(year);
        int yearValue = Integer.parseInt(year);
        AccountBalanceResponse balance = coreServicePort.fetchAccountBalance(accountId);
        BigDecimal openingBalance = balance.balance();
        BigDecimal interestAmount = interestRatePolicy.calculateInterestAmount(openingBalance);
        BigDecimal closingBalance = openingBalance.add(interestAmount);
        if (creditViaKafka) {
            publishCalculation(accountId, yearValue, interestAmount, balance.currency(), openingBalance, closingBalance);
        } else {
            creditThroughHttp(accountId, year, yearValue, interestAmount, balance.currency(), openingBalance, closingBalance);
        }
        return new InterestSummaryResponse(
                accountId,
                yearValue,
                openingBalance,
                closingBalance,
                interestRatePolicy.annualRate(),
                interestAmount,
                balance.currency());
    }

    private void publishCalculation(
            String accountId,
            int yearValue,
            BigDecimal interestAmount,
            String currency,
            BigDecimal openingBalance,
            BigDecimal closingBalance) {
        String eventId = "interest:" + accountId + ":" + yearValue;
        calculations.save(InterestCalculation.pending(eventId, accountId, yearValue, interestAmount, currency));
        publisher.publish(new InterestCalculatedNotice(
                eventId,
                "InterestCalculated",
                1,
                accountId,
                yearValue,
                interestAmount,
                currency,
                interestRatePolicy.annualRate(),
                openingBalance,
                closingBalance,
                LocalDate.now().toString()));
    }

    private void creditThroughHttp(
            String accountId,
            String year,
            int yearValue,
            BigDecimal interestAmount,
            String currency,
            BigDecimal openingBalance,
            BigDecimal closingBalance) {
        coreServicePort.creditInterest(new CreditInterestCommand(
                accountId,
                yearValue,
                interestAmount,
                currency,
                interestRatePolicy.annualRate(),
                openingBalance,
                closingBalance,
                "interest-" + accountId + "-" + year));
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

    private static final class UntrackedCalculations implements InterestCalculationRepository {

        @Override
        public void save(InterestCalculation calculation) {
        }

        @Override
        public Optional<InterestCalculation> findByEventId(String eventId) {
            return Optional.empty();
        }
    }
}
