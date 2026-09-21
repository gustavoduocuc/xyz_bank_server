package cl.duoc.xyzbank.bffweb.interestview.unit;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.interestview.config.InterestsFeatureProperties;
import cl.duoc.xyzbank.bffweb.interestview.infrastructure.adapters.InterestServiceRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("The InterestServiceRouter")
class InterestServiceRouterTest {

    /*
     * Cases:
     * 1. Routes to interests-service when feature flag is enabled
     * 2. Routes to core-service when feature flag is disabled
     */

    private final InterestViewResponse coreResponse = summary("core");
    private final InterestViewResponse interestsResponse = summary("interests");
    private final AtomicReference<String> routedTo = new AtomicReference<>();

    private final InterestPort coreAdapter = (accountId, year) -> {
        routedTo.set("core-service");
        return coreResponse;
    };
    private final InterestPort interestsAdapter = (accountId, year) -> {
        routedTo.set("interests-service");
        return interestsResponse;
    };

    @Nested
    @DisplayName("when routing interest summary fetches")
    class WhenRoutingInterestSummaryFetches {

        @Test
        @DisplayName("routes to interests-service when feature flag is enabled")
        void routesToInterestsServiceWhenFeatureFlagIsEnabled() {
            InterestServiceRouter router = new InterestServiceRouter(
                    coreAdapter, interestsAdapter, new InterestsFeatureProperties(true));

            InterestViewResponse result = router.fetchSummary("account-1", "2025");

            assertSame(interestsResponse, result);
            assertEquals("interests-service", routedTo.get());
        }

        @Test
        @DisplayName("routes to core-service when feature flag is disabled")
        void routesToCoreServiceWhenFeatureFlagIsDisabled() {
            InterestServiceRouter router = new InterestServiceRouter(
                    coreAdapter, interestsAdapter, new InterestsFeatureProperties(false));

            InterestViewResponse result = router.fetchSummary("account-1", "2025");

            assertSame(coreResponse, result);
            assertEquals("core-service", routedTo.get());
        }
    }

    private static InterestViewResponse summary(String source) {
        return new InterestViewResponse(
                source,
                2025,
                new BigDecimal("1000.00"),
                new BigDecimal("1035.00"),
                new BigDecimal("0.035"),
                new BigDecimal("35.00"),
                "USD");
    }
}
