package cl.duoc.xyzbank.bffweb.dashboard.infrastructure.rest;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.DashboardResponse;
import cl.duoc.xyzbank.bffweb.dashboard.application.usecases.DashboardUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class DashboardController {

    private final DashboardUseCase dashboardUseCase;

    public DashboardController(DashboardUseCase dashboardUseCase) {
        this.dashboardUseCase = dashboardUseCase;
    }

    @GetMapping("/{customerId}/dashboard")
    public DashboardResponse getDashboard(@PathVariable String customerId) {
        return dashboardUseCase.execute(customerId);
    }
}
