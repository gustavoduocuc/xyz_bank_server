package cl.duoc.xyzbank.eurekaserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("Eureka Server Application")
class EurekaServerApplicationTest {

    @Test
    @DisplayName("loads the Spring context successfully")
    void contextLoads() {
        // Smoke test: verifies the application starts without errors
    }
}
