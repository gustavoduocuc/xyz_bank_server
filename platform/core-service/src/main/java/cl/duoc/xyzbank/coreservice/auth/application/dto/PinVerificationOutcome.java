package cl.duoc.xyzbank.coreservice.auth.application.dto;

public record PinVerificationOutcome(Result result, String customerId, String atmSessionId) {

    public enum Result {
        SUCCESS,
        INCORRECT,
        LOCKED
    }
}
