package cl.duoc.xyzbank.interestsservice.interestview.infrastructure.rest;

import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.usecases.GetInterestSummaryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterestController {

    private final GetInterestSummaryUseCase getInterestSummaryUseCase;

    public InterestController(GetInterestSummaryUseCase getInterestSummaryUseCase) {
        this.getInterestSummaryUseCase = getInterestSummaryUseCase;
    }

    @GetMapping("/accounts/{accountId}/interest-summary")
    public InterestSummaryResponse getInterestSummary(
            @PathVariable String accountId,
            @RequestParam String year) {
        // Authorization is stashed in MDC by CallerContextInterceptor for outbound forwarding
        return getInterestSummaryUseCase.execute(accountId, year);
    }
}
