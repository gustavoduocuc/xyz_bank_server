package com.xyzbank.migration.shared.domain;

/**
 * 1-based line number of a record in its source CSV file. It is the tie-breaker
 * that makes duplicate resolution deterministic: the lowest line wins.
 */
public record SourceLine(int number) {

    public SourceLine {
        if (number <= 0) {
            throw DomainError.validation("Source line must be positive, got " + number);
        }
    }

    public static SourceLine of(int number) {
        return new SourceLine(number);
    }
}
