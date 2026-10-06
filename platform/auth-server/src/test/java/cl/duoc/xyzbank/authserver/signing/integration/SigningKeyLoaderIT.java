package cl.duoc.xyzbank.authserver.signing.integration;

import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyLoader;
import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyProperties;
import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyUnavailableException;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The SigningKeyLoader")
class SigningKeyLoaderIT {

    /*
     * Cases:
     * 1. Loads the RSA signing key from the keystore, identified by its RFC 7638 thumbprint
     * 2. Refuses when no keystore path is configured
     * 3. Refuses when the keystore file does not exist
     * 4. Refuses a wrong keystore password
     * 5. Refuses an alias the keystore does not contain
     * 6. Refuses a key that is not RSA
     */

    private static final String PASSWORD = "xyzbank-dev";
    private static final String ALIAS = "auth-server-signing";

    private final SigningKeyLoader loader = new SigningKeyLoader(new DefaultResourceLoader());

    @Test
    @DisplayName("loads the RSA signing key identified by its thumbprint, the same on every load")
    void loadsTheRsaSigningKeyIdentifiedByItsThumbprintTheSameOnEveryLoad() throws Exception {
        SigningKeyProperties properties =
                new SigningKeyProperties("classpath:signing/signing.p12", PASSWORD, ALIAS);

        RSAKey first = loader.load(properties);
        RSAKey second = loader.load(properties);

        assertTrue(first.isPrivate());
        assertEquals(first.computeThumbprint().toString(), first.getKeyID());
        assertEquals(first.getKeyID(), second.getKeyID());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("refuses to sign without a configured keystore path")
    void refusesToSignWithoutAConfiguredKeystorePath(String keystorePath) {
        SigningKeyProperties properties = new SigningKeyProperties(keystorePath, PASSWORD, ALIAS);

        SigningKeyUnavailableException exception =
                assertThrows(SigningKeyUnavailableException.class, () -> loader.load(properties));

        assertTrue(exception.getMessage().contains("AUTH_SIGNING_KEYSTORE_PATH"), exception.getMessage());
    }

    @Test
    @DisplayName("refuses a keystore file that does not exist")
    void refusesAKeystoreFileThatDoesNotExist() {
        assertRefusedNaming(new SigningKeyProperties("file:/nonexistent/signing.p12", PASSWORD, ALIAS),
                "file:/nonexistent/signing.p12");
    }

    @Test
    @DisplayName("refuses a wrong keystore password")
    void refusesAWrongKeystorePassword() {
        assertRefusedNaming(new SigningKeyProperties("classpath:signing/signing.p12", "wrong", ALIAS),
                "classpath:signing/signing.p12");
    }

    @Test
    @DisplayName("refuses an alias the keystore does not contain")
    void refusesAnAliasTheKeystoreDoesNotContain() {
        assertRefusedNaming(new SigningKeyProperties("classpath:signing/signing.p12", PASSWORD, "missing-alias"),
                "missing-alias");
    }

    @Test
    @DisplayName("refuses a key that is not RSA")
    void refusesAKeyThatIsNotRsa() {
        assertRefusedNaming(new SigningKeyProperties("classpath:signing/ec-signing.p12", PASSWORD, ALIAS),
                "RSA");
    }

    private void assertRefusedNaming(SigningKeyProperties properties, String expectedDetail) {
        SigningKeyUnavailableException exception =
                assertThrows(SigningKeyUnavailableException.class, () -> loader.load(properties));

        assertTrue(exception.getMessage().contains(expectedDetail), exception.getMessage());
    }
}
