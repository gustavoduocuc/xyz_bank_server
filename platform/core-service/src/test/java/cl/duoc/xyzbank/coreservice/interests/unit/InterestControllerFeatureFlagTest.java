package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coreservice.interests.application.dto.AnnualInterestSummaryResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.GetAnnualInterestSummaryUseCase;
import cl.duoc.xyzbank.coreservice.interests.config.InterestsFeatureProperties;
import cl.duoc.xyzbank.coreservice.interests.infrastructure.rest.InterestController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterestController Feature Flag")
class InterestControllerFeatureFlagTest {

    /*
     * Cases:
     * 1. Feature enabled (active) - returns interest summary
     * 2. Feature in shadow mode - returns 503 with X-Shadow-Mode header
     * 3. Feature disabled - returns 410 Gone with X-Deprecated-Endpoint header
     */

    @Nested
    @DisplayName("when feature is enabled")
    class WhenFeatureEnabled {

        @Test
        @DisplayName("returns interest summary with 200 OK")
        void returnsInterestSummaryWhen200OK() {
            InterestsFeatureProperties properties = new InterestsFeatureProperties(true, false);
            StubGetAnnualInterestSummaryUseCase useCase = new StubGetAnnualInterestSummaryUseCase();
            InterestController controller = new InterestController(useCase, properties);

            ResponseEntity<AnnualInterestSummaryResponse> response = controller.getSummary("account-123", "2025");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("account-123", response.getBody().accountId());
        }
    }

    @Nested
    @DisplayName("when feature is in shadow mode")
    class WhenFeatureShadowMode {

        @Test
        @DisplayName("returns 503 Service Unavailable with X-Shadow-Mode header")
        void returns503WithShadowModeHeader() {
            InterestsFeatureProperties properties = new InterestsFeatureProperties(true, true);
            StubGetAnnualInterestSummaryUseCase useCase = new StubGetAnnualInterestSummaryUseCase();
            InterestController controller = new InterestController(useCase, properties);

            ResponseEntity<AnnualInterestSummaryResponse> response = controller.getSummary("account-123", "2025");

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertEquals("true", response.getHeaders().getFirst("X-Shadow-Mode"));
            assertNull(response.getBody());
        }
    }

    @Nested
    @DisplayName("when feature is disabled")
    class WhenFeatureDisabled {

        @Test
        @DisplayName("returns 410 Gone with X-Deprecated-Endpoint header")
        void returns410WithDeprecatedHeader() {
            InterestsFeatureProperties properties = new InterestsFeatureProperties(false, false);
            StubGetAnnualInterestSummaryUseCase useCase = new StubGetAnnualInterestSummaryUseCase();
            InterestController controller = new InterestController(useCase, properties);

            ResponseEntity<AnnualInterestSummaryResponse> response = controller.getSummary("account-123", "2025");

            assertEquals(HttpStatus.GONE, response.getStatusCode());
            assertEquals("true", response.getHeaders().getFirst("X-Deprecated-Endpoint"));
            assertNull(response.getBody());
        }
    }

    static class StubGetAnnualInterestSummaryUseCase extends GetAnnualInterestSummaryUseCase {

        public StubGetAnnualInterestSummaryUseCase() {
            super(null);
        }

        @Override
        public AnnualInterestSummaryResponse execute(String accountId, String year) {
            return new AnnualInterestSummaryResponse(
                    accountId,
                    2025,
                    new BigDecimal("1000.00"),
                    new BigDecimal("1035.00"),
                    new BigDecimal("0.035"),
                    new BigDecimal("35.00"),
                    "USD");
        }
    }
}
