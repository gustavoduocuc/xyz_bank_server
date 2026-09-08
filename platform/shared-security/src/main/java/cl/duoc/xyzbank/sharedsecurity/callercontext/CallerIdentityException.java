package cl.duoc.xyzbank.sharedsecurity.callercontext;

public class CallerIdentityException extends RuntimeException {

    private final Type type;

    public enum Type {
        INVALID,
        FORBIDDEN
    }

    private CallerIdentityException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public static CallerIdentityException invalid(String message) {
        return new CallerIdentityException(Type.INVALID, message);
    }

    public static CallerIdentityException forbidden(String message) {
        return new CallerIdentityException(Type.FORBIDDEN, message);
    }

    public Type getType() {
        return type;
    }
}
