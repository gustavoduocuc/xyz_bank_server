package cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Keeps answering /actuator/health on the customer-facing port, exactly as before actuator moved
 * to the internal management port (service-health spec): only the overall status, from the same
 * health endpoint the management port serves. Circuit-breaker state is never exposed here
 * (add-resilience4j-to-bffs design.md Decision 8).
 */
@RestController
public class PublicHealthController {

    private final HealthEndpoint healthEndpoint;

    public PublicHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, String>> health() {
        Status status = healthEndpoint.health().getStatus();
        return ResponseEntity.status(Status.UP.equals(status) ? 200 : 503)
                .body(Map.of("status", status.getCode()));
    }
}
