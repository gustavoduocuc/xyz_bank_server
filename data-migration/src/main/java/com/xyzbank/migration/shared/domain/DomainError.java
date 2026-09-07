package com.xyzbank.migration.shared.domain;

public class DomainError extends RuntimeException {

    public enum Type {
        NOT_FOUND,
        VALIDATION,
        OTHER
    }

    private final Type type;

    private DomainError(Type type, String message) {
        super(message);
        this.type = type;
    }

    public static DomainError notFound(String message) {
        return new DomainError(Type.NOT_FOUND, message);
    }

    public static DomainError validation(String message) {
        return new DomainError(Type.VALIDATION, message);
    }

    public static DomainError other(String message) {
        return new DomainError(Type.OTHER, message);
    }

    public Type getType() {
        return type;
    }
}
