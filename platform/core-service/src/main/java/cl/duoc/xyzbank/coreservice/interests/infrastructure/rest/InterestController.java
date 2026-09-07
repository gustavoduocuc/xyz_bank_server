package cl.duoc.xyzbank.coreservice.interests.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.interests.application.dto.AnnualInterestSummaryResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.GetAnnualInterestSummaryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterestController {

    private final GetAnnualInterestSummaryUseCase getAnnualInterestSummaryUseCase;

    public InterestController(GetAnnualInterestSummaryUseCase getAnnualInterestSummaryUseCase) {
        this.getAnnualInterestSummaryUseCase = getAnnualInterestSummaryUseCase;
    }

    @GetMapping("/internal/accounts/{accountId}/interest-summary")
    public AnnualInterestSummaryResponse getSummary(
            @PathVariable String accountId, @RequestParam(required = false) String year) {
        return getAnnualInterestSummaryUseCase.execute(accountId, year);
    }
}
