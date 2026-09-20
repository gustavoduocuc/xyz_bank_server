package cl.duoc.xyzbank.bffweb.interestview.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCalls;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
@Qualifier("interestsServiceAdapter")
public class HttpInterestsServiceAdapter implements InterestPort {

    private final RestClient interestsServiceClient;

    public HttpInterestsServiceAdapter(@Qualifier("interestsServiceClient") RestClient interestsServiceClient) {
        this.interestsServiceClient = interestsServiceClient;
    }

    @Override
    public InterestViewResponse fetchSummary(String accountId, String year) {
        InterestSummaryWire wire = CoreServiceCalls.fetch(() -> interestsServiceClient.get()
                .uri("/accounts/{accountId}/interest-summary?year={year}", accountId, year)
                .retrieve()
                .body(InterestSummaryWire.class));
        return new InterestViewResponse(
                wire.accountId(),
                wire.year(),
                wire.openingBalance(),
                wire.closingBalance(),
                wire.interestRate(),
                wire.interestAmount(),
                wire.currency());
    }

    private record InterestSummaryWire(
            String accountId,
            int year,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal interestRate,
            BigDecimal interestAmount,
            String currency) {
    }
}
