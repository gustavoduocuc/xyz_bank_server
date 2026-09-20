package cl.duoc.xyzbank.configserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("Config Server Application")
class ConfigServerApplicationTest {

    @Test
    @DisplayName("loads the Spring context successfully")
    void contextLoads() {
        // Smoke test: verifies the application starts without errors
    }
}
