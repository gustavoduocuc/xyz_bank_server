package cl.duoc.xyzbank.bffweb.interestview.infrastructure.rest;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.application.usecases.InterestViewUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterestViewController {

    private final InterestViewUseCase interestViewUseCase;

    public InterestViewController(InterestViewUseCase interestViewUseCase) {
        this.interestViewUseCase = interestViewUseCase;
    }

    @GetMapping("/accounts/{accountId}/interest-summary")
    public InterestViewResponse getInterestView(
            @PathVariable String accountId, @RequestParam(required = false) String year) {
        return interestViewUseCase.execute(accountId, year);
    }
}
