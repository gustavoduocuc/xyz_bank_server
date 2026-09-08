package cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public final class DailyWithdrawalUsage {

    private final Money withdrawnAmount;
    private final Optional<LocalDate> date;

    private DailyWithdrawalUsage(Money withdrawnAmount, Optional<LocalDate> date) {
        this.withdrawnAmount = withdrawnAmount;
        this.date = date;
    }

    public static DailyWithdrawalUsage create(Money withdrawnAmount, Optional<LocalDate> date) {
        return new DailyWithdrawalUsage(withdrawnAmount, date);
    }

    public static DailyWithdrawalUsage none(String currency) {
        return create(Money.create(BigDecimal.ZERO, currency), Optional.empty());
    }

    public Money getWithdrawnAmount() {
        return withdrawnAmount;
    }

    public Optional<LocalDate> getDate() {
        return date;
    }

    public DailyWithdrawalUsage recordWithdrawal(Money amount, LocalDate today, Money dailyLimit) {
        Money amountBeforeToday = isFor(today) ? withdrawnAmount : Money.create(BigDecimal.ZERO, amount.getCurrency());
        Money cumulativeAmount = amountBeforeToday.add(amount);
        if (cumulativeAmount.getAmount().compareTo(dailyLimit.getAmount()) > 0) {
            throw DomainException.validation("Daily withdrawal limit exceeded");
        }
        return create(cumulativeAmount, Optional.of(today));
    }

    private boolean isFor(LocalDate day) {
        return date.filter(day::equals).isPresent();
    }
}
