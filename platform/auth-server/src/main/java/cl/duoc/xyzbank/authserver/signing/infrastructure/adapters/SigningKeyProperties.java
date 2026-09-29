package cl.duoc.xyzbank.authserver.signing.infrastructure.adapters;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the token signing key lives: a PKCS12 keystore provisioned from outside the
 * application (environment variables in the local stack), never generated at startup.
 */
@ConfigurationProperties(prefix = "auth.signing")
public record SigningKeyProperties(String keystorePath, String keystorePassword, String keyAlias) {
}
