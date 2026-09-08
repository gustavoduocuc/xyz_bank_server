package cl.duoc.xyzbank.bffweb.dashboard.config;

import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.CustomerProfilePort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.TransactionsPort;
import cl.duoc.xyzbank.bffweb.dashboard.application.usecases.DashboardUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashboardConfig {

    @Bean
    public DashboardUseCase dashboardUseCase(
            CustomerProfilePort customerProfilePort,
            AccountsPort accountsPort,
            TransactionsPort transactionsPort) {
        return new DashboardUseCase(customerProfilePort, accountsPort, transactionsPort);
    }
}
