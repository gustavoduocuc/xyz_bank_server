package cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;

import java.time.LocalDate;
import java.util.Optional;

public final class DateRange {

    private final Optional<LocalDate> from;
    private final Optional<LocalDate> to;

    private DateRange(Optional<LocalDate> from, Optional<LocalDate> to) {
        this.from = from;
        this.to = to;
    }

    public static DateRange create(Optional<LocalDate> from, Optional<LocalDate> to) {
        if (from.isPresent() && to.isPresent() && from.get().isAfter(to.get())) {
            throw DomainException.validation("From date cannot be after to date");
        }
        return new DateRange(from, to);
    }

    public Optional<LocalDate> getFrom() {
        return from;
    }

    public Optional<LocalDate> getTo() {
        return to;
    }
}
