package cl.duoc.xyzbank.authserver.signing.config;

import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyLoader;
import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Declaring this JWKSource is what stops Spring Boot's authorization-server
 * auto-configuration from generating a throwaway RSA key at startup.
 */
@Configuration
public class SigningKeyConfig {

    @Bean
    public JWKSource<SecurityContext> jwkSource(SigningKeyProperties properties, ResourceLoader resourceLoader) {
        return new ImmutableJWKSet<>(new JWKSet(new SigningKeyLoader(resourceLoader).load(properties)));
    }
}
