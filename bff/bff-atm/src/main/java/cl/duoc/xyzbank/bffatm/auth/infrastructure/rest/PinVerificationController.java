package cl.duoc.xyzbank.bffatm.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffatm.auth.application.dto.VerifiedPin;
import cl.duoc.xyzbank.bffatm.auth.application.ports.PinVerificationPort;
import cl.duoc.xyzbank.bffatm.auth.infrastructure.rest.dto.AtmSessionResponse;
import cl.duoc.xyzbank.bffatm.auth.infrastructure.rest.dto.PinVerificationRequest;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.TerminalIdentity;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies a card PIN through core-service and, on success, issues a 120-second ATM session
 * bound to the terminal's mTLS certificate (design.md Decision 8). This controller never
 * inspects or logs the submitted PIN.
 */
@RestController
public class PinVerificationController {

    private final PinVerificationPort pinVerification;
    private final JwtCallerContextAdapter tokenAdapter;

    public PinVerificationController(PinVerificationPort pinVerification, JwtCallerContextAdapter tokenAdapter) {
        this.pinVerification = pinVerification;
        this.tokenAdapter = tokenAdapter;
    }

    @PostMapping("/pin-verifications")
    public AtmSessionResponse verify(@RequestBody PinVerificationRequest request, @TerminalIdentity String terminalId) {
        VerifiedPin verified = pinVerification.verify(request.cardNumber(), request.pin());

        String sessionToken = tokenAdapter.issue(
                verified.customerId(), Channel.ATM, terminalId, verified.atmSessionId());
        return new AtmSessionResponse(sessionToken);
    }
}
