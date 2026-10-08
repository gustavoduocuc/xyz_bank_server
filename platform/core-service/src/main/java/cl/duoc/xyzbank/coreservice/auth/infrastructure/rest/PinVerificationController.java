package cl.duoc.xyzbank.coreservice.auth.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationOutcome;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationRequest;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationResponse;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PinVerificationController {

    private final VerifyPinUseCase verifyPinUseCase;

    public PinVerificationController(VerifyPinUseCase verifyPinUseCase) {
        this.verifyPinUseCase = verifyPinUseCase;
    }

    // One transaction: the card's failure count and a CARD_LOCKED outbox row commit together
    @Transactional
    @PostMapping("/internal/auth/atm/pin-verifications")
    public ResponseEntity<PinVerificationResponse> verify(@RequestBody PinVerificationRequest request) {
        PinVerificationOutcome outcome = verifyPinUseCase.execute(request.cardNumber(), request.pin());
        return switch (outcome.result()) {
            case SUCCESS -> ResponseEntity.status(HttpStatus.OK).body(new PinVerificationResponse(outcome.customerId(), outcome.atmSessionId()));
            case INCORRECT -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case LOCKED -> ResponseEntity.status(HttpStatus.LOCKED).build();
        };
    }
}
