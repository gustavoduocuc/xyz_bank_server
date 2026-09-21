package cl.duoc.xyzbank.interestsservice.interests.infrastructure.rest;

import cl.duoc.xyzbank.interestsservice.interests.application.usecases.ApplyAnnualInterestUseCase;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterestApplicationController {

    private final ApplyAnnualInterestUseCase applyAnnualInterestUseCase;

    public InterestApplicationController(ApplyAnnualInterestUseCase applyAnnualInterestUseCase) {
        this.applyAnnualInterestUseCase = applyAnnualInterestUseCase;
    }

    @PostMapping("/accounts/{accountId}/interest-applications")
    public InterestSummaryResponse applyAnnualInterest(
            @PathVariable String accountId,
            @RequestParam String year) {
        return applyAnnualInterestUseCase.execute(accountId, year);
    }
}
