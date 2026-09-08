package cl.duoc.xyzbank.bffweb.dashboard.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.AccountBalance;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Component
public class HttpAccountsAdapter implements AccountsPort {

    private final RestClient coreServiceClient;

    public HttpAccountsAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    @Override
    public List<AccountBalance> fetchAccountsForCustomer(String customerId) {
        List<AccountSummaryWire> wires = coreServiceClient.get()
                .uri("/internal/customers/{customerId}/accounts", customerId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountSummaryWire>>() {
                });
        return wires.stream()
                .map(wire -> new AccountBalance(wire.id(), wire.accountNumber(), wire.balance(), wire.currency()))
                .toList();
    }

    private record AccountSummaryWire(String id, String accountNumber, BigDecimal balance, String currency) {
    }
}
