package cl.duoc.xyzbank.interestsservice.interests.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InterestRatePolicy {

    private final BigDecimal annualRate;

    public InterestRatePolicy(BigDecimal annualRate) {
        this.annualRate = annualRate;
    }

    public BigDecimal annualRate() {
        return annualRate;
    }

    public BigDecimal calculateInterestAmount(BigDecimal openingBalance) {
        return openingBalance.multiply(annualRate).setScale(2, RoundingMode.HALF_UP);
    }
}
