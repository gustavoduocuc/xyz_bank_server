package cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters;

public class CoreServiceCallException extends RuntimeException {

    private final int status;

    public CoreServiceCallException(int status, String detail) {
        super(detail);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
