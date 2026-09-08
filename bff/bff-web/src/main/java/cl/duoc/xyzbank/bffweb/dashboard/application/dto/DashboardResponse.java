package cl.duoc.xyzbank.bffweb.dashboard.application.dto;

import java.util.List;

public record DashboardResponse(CustomerProfile profile, List<AccountSummary> accounts) {
}
