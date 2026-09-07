package cl.duoc.xyzbank.coredomain.shared.domain;

public class DomainException extends RuntimeException {

    private final Type type;

    public enum Type {
        NOT_FOUND,
        VALIDATION,
        OTHER
    }

    private DomainException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public static DomainException notFound(String message) {
        return new DomainException(Type.NOT_FOUND, message);
    }

    public static DomainException validation(String message) {
        return new DomainException(Type.VALIDATION, message);
    }

    public static DomainException create(String message) {
        return new DomainException(Type.OTHER, message);
    }

    public Type getType() {
        return type;
    }
}
