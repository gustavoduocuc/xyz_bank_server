package cl.duoc.xyzbank.authserver.signing.integration;

import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyLoader;
import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyProperties;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The SigningKeyLoader")
class SigningKeyLoaderIT {

    /*
     * Cases:
     * 1. Loads the RSA signing key from the keystore, identified by its RFC 7638 thumbprint
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
}
