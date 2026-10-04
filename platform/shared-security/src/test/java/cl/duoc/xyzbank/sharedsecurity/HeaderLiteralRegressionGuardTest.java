package cl.duoc.xyzbank.sharedsecurity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against reintroducing header-based caller identity (X-Customer-Id, X-Channel,
 * X-Terminal-Id) anywhere outside {@code platform/shared-security} now that every channel
 * resolves identity from a real credential instead. This is a plain source-text scan, not an
 * ArchUnit rule: ArchUnit reasons about class/package dependencies, not string literals, so it
 * cannot see a header name passed to {@code request.getHeader(...)}.
 */
@DisplayName("Header-based caller identity regression guard")
class HeaderLiteralRegressionGuardTest {

    private static final List<String> FORBIDDEN_LITERALS = List.of("X-Customer-Id", "X-Channel", "X-Terminal-Id");
    private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

    @Test
    @DisplayName("no module's main source outside shared-security reads a header-based identity literal")
    void noModuleOutsideSharedSecurityReadsAHeaderBasedIdentityLiteral() throws IOException {
        Path repoRoot = findRepoRoot();
        List<String> modules = readReactorModules(repoRoot);

        for (String module : modules) {
            if (module.equals("platform/shared-security")) {
                continue;
            }
            Path sourceRoot = repoRoot.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            scanForForbiddenLiterals(sourceRoot);
        }
    }

    private void scanForForbiddenLiterals(Path sourceRoot) throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String content;
                        try {
                            content = Files.readString(path);
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                        for (String literal : FORBIDDEN_LITERALS) {
                            assertTrue(
                                    !content.contains("\"" + literal + "\""),
                                    () -> path + " contains the header-based identity literal \"" + literal + "\"");
                        }
                    });
        }
    }

    private Path findRepoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("docker-compose.yaml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the repository root (no docker-compose.yaml found above " + Path.of("").toAbsolutePath() + ")");
    }

    private List<String> readReactorModules(Path repoRoot) throws IOException {
        String pomContent = Files.readString(repoRoot.resolve("pom.xml"));
        Matcher matcher = MODULE_PATTERN.matcher(pomContent);
        List<String> modules = new java.util.ArrayList<>();
        while (matcher.find()) {
            modules.add(matcher.group(1).trim());
        }
        return modules;
    }
}
