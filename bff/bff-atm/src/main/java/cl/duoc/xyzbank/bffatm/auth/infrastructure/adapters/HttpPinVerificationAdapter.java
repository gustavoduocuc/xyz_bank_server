package cl.duoc.xyzbank.bffatm.auth.infrastructure.adapters;

import cl.duoc.xyzbank.bffatm.auth.application.dto.VerifiedPin;
import cl.duoc.xyzbank.bffatm.auth.application.ports.PinVerificationPort;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCalls;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls core-service's TLS-only PIN-verification endpoint. core-service's incorrect/locked
 * responses (401/423) carry no body and no distinguishing detail beyond the status code (no
 * card-existence oracle), so they propagate as-is via {@link CoreServiceCalls#fetch}; the PIN is
 * never inspected or logged here.
 */
@Component
public class HttpPinVerificationAdapter implements PinVerificationPort {

    private final RestClient corePinVerificationClient;

    public HttpPinVerificationAdapter(@Qualifier("corePinVerificationClient") RestClient corePinVerificationClient) {
        this.corePinVerificationClient = corePinVerificationClient;
    }

    @Override
    public VerifiedPin verify(String cardNumber, String pin) {
        CorePinVerificationResponse response = CoreServiceCalls.fetch(() -> corePinVerificationClient
                .post()
                .uri("/internal/auth/atm/pin-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CorePinVerificationRequest(cardNumber, pin))
                .retrieve()
                .body(CorePinVerificationResponse.class));
        return new VerifiedPin(response.customerId(), response.atmSessionId());
    }

    private record CorePinVerificationRequest(String cardNumber, String pin) {
    }

    private record CorePinVerificationResponse(String customerId, String atmSessionId) {
    }
}
