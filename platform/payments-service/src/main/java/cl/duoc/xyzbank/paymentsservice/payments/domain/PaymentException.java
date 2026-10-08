package cl.duoc.xyzbank.paymentsservice.payments.domain;

public class PaymentException extends RuntimeException {

    public enum Type {
        NOT_FOUND,
        VALIDATION
    }

    private final Type type;

    private PaymentException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public static PaymentException notFound(String paymentId) {
        return new PaymentException(Type.NOT_FOUND, "Payment " + paymentId + " not found");
    }

    public static PaymentException validation(String message) {
        return new PaymentException(Type.VALIDATION, message);
    }

    public Type type() {
        return type;
    }
}
