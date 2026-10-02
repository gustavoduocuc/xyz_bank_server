package cl.duoc.xyzbank.bffatm.auth.application.dto;

/** What core-service returns for a correct PIN: whose card it is and the ATM session it opened. */
public record VerifiedPin(String customerId, String atmSessionId) {
}
