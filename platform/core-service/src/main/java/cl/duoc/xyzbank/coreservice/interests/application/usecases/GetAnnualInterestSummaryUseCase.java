package cl.duoc.xyzbank.coreservice.interests.application.usecases;

import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.interests.application.dto.AnnualInterestSummaryResponse;

public class GetAnnualInterestSummaryUseCase {

    private final InterestSummaryRepository interestSummaryRepository;

    public GetAnnualInterestSummaryUseCase(InterestSummaryRepository interestSummaryRepository) {
        this.interestSummaryRepository = interestSummaryRepository;
    }

    public AnnualInterestSummaryResponse execute(String accountId, String year) {
        Id id = Id.create(accountId);
        int parsedYear = parseYear(year);

        AnnualInterestSummary summary = interestSummaryRepository.findByAccountIdAndYear(id, parsedYear)
                .orElseThrow(() -> DomainException.notFound(
                        "No interest summary for account " + accountId + " and year " + year));

        return toResponse(summary);
    }

    private int parseYear(String year) {
        if (year == null || year.isBlank()) {
            throw DomainException.validation("Year is required");
        }
        try {
            return Integer.parseInt(year);
        } catch (NumberFormatException exception) {
            throw DomainException.validation("Year must be a whole number");
        }
    }

    private AnnualInterestSummaryResponse toResponse(AnnualInterestSummary summary) {
        return new AnnualInterestSummaryResponse(
                summary.getAccountId().getValue(),
                summary.getYear(),
                summary.getOpeningBalance().getAmount(),
                summary.getClosingBalance().getAmount(),
                summary.getInterestRate(),
                summary.getInterestAmount().getAmount(),
                summary.getOpeningBalance().getCurrency());
    }
}
