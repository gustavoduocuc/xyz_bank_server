package cl.duoc.xyzbank.coreservice.interests.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.interests.application.dto.AnnualInterestSummaryResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.GetAnnualInterestSummaryUseCase;
import cl.duoc.xyzbank.coreservice.interests.config.InterestsFeatureProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterestController {

    private static final Logger log = LoggerFactory.getLogger(InterestController.class);

    private final GetAnnualInterestSummaryUseCase getAnnualInterestSummaryUseCase;
    private final InterestsFeatureProperties featureProperties;

    public InterestController(
            GetAnnualInterestSummaryUseCase getAnnualInterestSummaryUseCase,
            InterestsFeatureProperties featureProperties) {
        this.getAnnualInterestSummaryUseCase = getAnnualInterestSummaryUseCase;
        this.featureProperties = featureProperties;
    }

    @GetMapping("/internal/accounts/{accountId}/interest-summary")
    public ResponseEntity<AnnualInterestSummaryResponse> getSummary(
            @PathVariable String accountId, @RequestParam(required = false) String year) {

        if (featureProperties.isDisabled()) {
            log.info("INTEREST_REQUEST source=core-service status=disabled accountId={}", accountId);
            return ResponseEntity.status(HttpStatus.GONE)
                    .header("X-Deprecated-Endpoint", "true")
                    .build();
        }

        if (featureProperties.isShadowMode()) {
            log.info("INTEREST_REQUEST source=core-service status=shadow_mode accountId={}", accountId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Shadow-Mode", "true")
                    .build();
        }

        log.info("INTEREST_REQUEST source=core-service status=active accountId={}", accountId);
        AnnualInterestSummaryResponse response = getAnnualInterestSummaryUseCase.execute(accountId, year);
        return ResponseEntity.ok(response);
    }
}
