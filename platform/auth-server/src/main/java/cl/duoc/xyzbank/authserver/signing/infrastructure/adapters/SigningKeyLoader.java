package cl.duoc.xyzbank.authserver.signing.infrastructure.adapters;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Loads the RS256 token signing key from the configured keystore (design.md Decision 5).
 * The key id is the key's RFC 7638 thumbprint, so it is stable across restarts and changes
 * by itself when the key is rotated.
 */
public class SigningKeyLoader {

    private final ResourceLoader resourceLoader;

    public SigningKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public RSAKey load(SigningKeyProperties properties) {
        try {
            char[] password = properties.keystorePassword().toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            Resource keystore = resourceLoader.getResource(properties.keystorePath());
            try (InputStream inputStream = keystore.getInputStream()) {
                keyStore.load(inputStream, password);
            }
            RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(properties.keyAlias(), password);
            RSAPublicKey publicKey = (RSAPublicKey) keyStore.getCertificate(properties.keyAlias()).getPublicKey();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
