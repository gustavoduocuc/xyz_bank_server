package cl.duoc.xyzbank.customersservice.customers.domain;

public class CustomerException extends RuntimeException {

    public enum Type {
        NOT_FOUND,
        VALIDATION,
        VERSION_CONFLICT
    }

    private final Type type;

    private CustomerException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public static CustomerException notFound(String message) {
        return new CustomerException(Type.NOT_FOUND, message);
    }

    public static CustomerException validation(String message) {
        return new CustomerException(Type.VALIDATION, message);
    }

    public static CustomerException versionConflict(String message) {
        return new CustomerException(Type.VERSION_CONFLICT, message);
    }

    public Type type() {
        return type;
    }
}
