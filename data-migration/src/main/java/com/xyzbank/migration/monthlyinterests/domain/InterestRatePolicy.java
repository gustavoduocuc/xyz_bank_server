package com.xyzbank.migration.monthlyinterests.domain;

public final class InterestRatePolicy {

    private InterestRatePolicy() {
    }

    public static double rateFor(Account account) {
        double savingsRate = 0.01;
        double seniorSavingsRate = 0.015;
        double loanRate = 0.015;
        double mortgageRate = 0.008;

        return switch (account.type()) {
            case SAVINGS -> account.isSenior() ? seniorSavingsRate : savingsRate;
            case LOAN -> loanRate;
            case MORTGAGE -> mortgageRate;
        };
    }

    public static InterestApplied apply(Account account) {
        double rate = rateFor(account);
        return new InterestApplied(account, rate, account.balance().multiply(1 + rate));
    }
}
