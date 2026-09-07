package cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;

import java.util.Objects;
import java.util.regex.Pattern;

public final class AccountNumber {

    private static final Pattern FORMAT = Pattern.compile("\\d{10}");

    private final String value;

    private AccountNumber(String value) {
        this.value = value;
    }

    public static AccountNumber create(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw DomainException.validation("Account number must be exactly 10 digits");
        }
        return new AccountNumber(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AccountNumber other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    public String toPrimitives() {
        return value;
    }
}
