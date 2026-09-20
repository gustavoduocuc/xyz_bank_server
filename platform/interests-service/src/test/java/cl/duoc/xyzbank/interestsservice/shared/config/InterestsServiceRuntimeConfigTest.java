package cl.duoc.xyzbank.interestsservice.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("interests-service runtime configuration")
class InterestsServiceRuntimeConfigTest {

    @Test
    @DisplayName("runtime profile requires a non-optional Config Server import")
    void runtimeProfileRequiresNonOptionalConfigServerImport() throws IOException {
        Path mainYaml = locateMainApplicationYaml();
        String yaml = Files.readString(mainYaml);
        assertTrue(yaml.contains("configserver:"), yaml);
        assertFalse(yaml.contains("optional:configserver:"), yaml);
    }

    private static Path locateMainApplicationYaml() {
        Path[] candidates = {
                Path.of("src/main/resources/application.yml"),
                Path.of("platform/interests-service/src/main/resources/application.yml")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate interests-service main application.yml");
    }
}
