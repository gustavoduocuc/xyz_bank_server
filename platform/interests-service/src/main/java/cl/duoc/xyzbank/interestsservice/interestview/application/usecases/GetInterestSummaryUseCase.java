package cl.duoc.xyzbank.interestsservice.interestview.application.usecases;

import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;

public class GetInterestSummaryUseCase {

    private final CoreServicePort coreServicePort;

    public GetInterestSummaryUseCase(CoreServicePort coreServicePort) {
        this.coreServicePort = coreServicePort;
    }

    public InterestSummaryResponse execute(String accountId, String year) {
        validateAccountId(accountId);
        validateYear(year);
        return coreServicePort.fetchInterestSummary(accountId, year);
    }

    private void validateAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw DomainException.validation("Account ID is required");
        }
    }

    private void validateYear(String year) {
        if (year == null || year.isBlank()) {
            throw DomainException.validation("Year is required");
        }
        try {
            Integer.parseInt(year);
        } catch (NumberFormatException e) {
            throw DomainException.validation("Year must be a valid number");
        }
    }
}
