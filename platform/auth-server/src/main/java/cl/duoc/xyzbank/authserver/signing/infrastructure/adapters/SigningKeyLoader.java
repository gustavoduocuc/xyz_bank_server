package cl.duoc.xyzbank.authserver.signing.infrastructure.adapters;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Loads the RS256 token signing key from the configured keystore (design.md Decision 5).
 * The key id is the key's RFC 7638 thumbprint, so it is stable across restarts and changes
 * by itself when the key is rotated. Any problem is a SigningKeyUnavailableException: there
 * is deliberately no fallback to a generated key, which would invalidate every issued token
 * on each restart.
 */
public class SigningKeyLoader {

    private final ResourceLoader resourceLoader;

    public SigningKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public RSAKey load(SigningKeyProperties properties) {
        String keystorePath = properties.keystorePath();
        if (keystorePath == null || keystorePath.isBlank()) {
            throw new SigningKeyUnavailableException(
                    "No token signing keystore configured: set AUTH_SIGNING_KEYSTORE_PATH (auth.signing.keystore-path)");
        }
        char[] password = passwordOf(properties);
        KeyStore keyStore = readKeystore(keystorePath, password);
        return rsaKeyOf(keyStore, keystorePath, properties.keyAlias(), password);
    }

    private static char[] passwordOf(SigningKeyProperties properties) {
        return properties.keystorePassword() == null ? new char[0] : properties.keystorePassword().toCharArray();
    }

    private KeyStore readKeystore(String keystorePath, char[] password) {
        Resource keystore = resourceLoader.getResource(keystorePath);
        if (!keystore.exists()) {
            throw new SigningKeyUnavailableException("Token signing keystore not found: " + keystorePath);
        }
        try (InputStream inputStream = keystore.getInputStream()) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, password);
            return keyStore;
        } catch (IOException | GeneralSecurityException exception) {
            throw new SigningKeyUnavailableException(
                    "Token signing keystore " + keystorePath + " could not be opened (wrong password or corrupt file)",
                    exception);
        }
    }

    private static RSAKey rsaKeyOf(KeyStore keyStore, String keystorePath, String alias, char[] password) {
        try {
            Key key = keyStore.getKey(alias, password);
            Certificate certificate = keyStore.getCertificate(alias);
            if (key == null || certificate == null) {
                throw new SigningKeyUnavailableException(
                        "Token signing keystore " + keystorePath + " has no key entry under alias " + alias);
            }
            if (!(key instanceof RSAPrivateKey privateKey)
                    || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
                throw new SigningKeyUnavailableException(
                        "Token signing key " + alias + " in " + keystorePath + " must be an RSA key for RS256");
            }
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (GeneralSecurityException | JOSEException exception) {
            throw new SigningKeyUnavailableException(
                    "Token signing key " + alias + " in " + keystorePath + " could not be read", exception);
        }
    }
}
