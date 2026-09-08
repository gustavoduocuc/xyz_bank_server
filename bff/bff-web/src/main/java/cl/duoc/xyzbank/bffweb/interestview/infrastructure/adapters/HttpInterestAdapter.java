package cl.duoc.xyzbank.bffweb.interestview.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCalls;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class HttpInterestAdapter implements InterestPort {

    private final RestClient coreServiceClient;

    public HttpInterestAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    @Override
    public InterestViewResponse fetchSummary(String accountId, String year) {
        InterestSummaryWire wire = CoreServiceCalls.fetch(() -> coreServiceClient.get()
                .uri("/internal/accounts/{accountId}/interest-summary?year={year}", accountId, year)
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
