package cl.duoc.xyzbank.coreservice.auth.application.ports;

import java.util.Optional;

/**
 * The customer of an ATM session core-service itself opened after a correct PIN, while that
 * session is still active (adopt-oauth2-tokens-between-services design.md Decision 5).
 */
public interface AtmSessionLookup {

    Optional<String> activeCustomerOf(String atmSessionId);
}
