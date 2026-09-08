package cl.duoc.xyzbank.bffweb.interestview.application.usecases;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.shared.application.RequestRejectedException;

public class InterestViewUseCase {

    private final InterestPort interestPort;

    public InterestViewUseCase(InterestPort interestPort) {
        this.interestPort = interestPort;
    }

    public InterestViewResponse execute(String accountId, String year) {
        if (year == null || year.isBlank()) {
            throw RequestRejectedException.validation("Year is required");
        }
        return interestPort.fetchSummary(accountId, year);
    }
}
