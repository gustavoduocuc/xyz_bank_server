package cl.duoc.xyzbank.bffatm.shared.application;

public class RequestRejectedException extends RuntimeException {

    private RequestRejectedException(String message) {
        super(message);
    }

    public static RequestRejectedException validation(String message) {
        return new RequestRejectedException(message);
    }
}
