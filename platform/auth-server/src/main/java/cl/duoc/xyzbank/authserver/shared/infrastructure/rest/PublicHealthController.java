package cl.duoc.xyzbank.authserver.shared.infrastructure.rest;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Keeps answering /actuator/health on the public TLS port, with only the overall status, from the
 * same health endpoint the internal management port serves (authorization-server spec: health
 * without credentials or details; no other actuator endpoint on this port).
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
