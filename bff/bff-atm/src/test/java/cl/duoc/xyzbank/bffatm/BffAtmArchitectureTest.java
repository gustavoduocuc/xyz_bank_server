package cl.duoc.xyzbank.bffatm;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AnalyzeClasses(packages = "cl.duoc.xyzbank.bffatm", importOptions = ImportOption.DoNotIncludeTests.class)
class BffAtmArchitectureTest {

    @ArchTest
    static final ArchRule doesNotImportPersistenceTypes = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "org.springframework.data.jpa..");

    @ArchTest
    static final ArchRule doesNotImportCoreDomain = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("cl.duoc.xyzbank.coredomain..");

    @ArchTest
    static final ArchRule doesNotImportOtherBffs = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("cl.duoc.xyzbank.bffweb..", "cl.duoc.xyzbank.bffmobile..");

    @ArchTest
    static final ArchRule exposesOnlyBalanceAndWithdrawalControllers = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleName("BalanceController")
            .orShould()
            .haveSimpleName("WithdrawalController")
            .orShould()
            .haveSimpleName("OpenApiDocumentController")
            .orShould()
            .haveSimpleName("PinVerificationController")
            // Not a new endpoint: answers the /actuator/health that actuator served on this port
            // before it moved to the management port (add-resilience4j-to-bffs Decision 8)
            .orShould()
            .haveSimpleName("PublicHealthController");

    @ArchTest
    static final ArchRule useCasesControllersAndAdaptersDoNotReadServletIdentityHeaders = noClasses()
            .that()
            .resideInAnyPackage("..application..", "..infrastructure.adapters..")
            .or()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("jakarta.servlet.http.HttpServletRequest");

    /*
     * Not an ArchUnit rule: ArchUnit reasons about class/package dependencies, not string
     * literals, so it cannot see a header name passed to request.getHeader(...). This scans
     * the module's own source text instead, guarding against reintroducing header-based
     * terminal/caller identity now that CallerContextInterceptor resolves it from the mTLS
     * certificate and the session JWT.
     */
    @Test
    @DisplayName("no source file reads a header-based identity literal")
    void noSourceFileReadsAHeaderBasedIdentityLiteral() throws IOException {
        List<String> forbiddenLiterals = List.of("X-Customer-Id", "X-Channel", "X-Terminal-Id");
        Path sourceRoot = Path.of("src/main/java");

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String content;
                        try {
                            content = Files.readString(path);
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                        for (String literal : forbiddenLiterals) {
                            assertTrue(
                                    !content.contains("\"" + literal + "\""),
                                    () -> path + " contains the header-based identity literal \"" + literal + "\"");
                        }
                    });
        }
    }
}
