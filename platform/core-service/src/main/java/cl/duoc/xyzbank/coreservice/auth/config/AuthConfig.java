package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.coredomain.cards.domain.repositories.AtmSessionRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.services.PinHasher;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters.StoredAtmSessionLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthConfig {

    // Adapter: the concrete BCrypt-backed hasher (shared-security) is an infrastructure
    // detail. Only its `matches` behavior is exposed to the domain, through the PinHasher port.
    @Bean
    public PinHasher pinHasher() {
        cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher bcryptHasher =
                new cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher();
        return bcryptHasher::matches;
    }

    @Bean
    public AtmSessionLookup atmSessionLookup(AtmSessionRepository atmSessionRepository) {
        return new StoredAtmSessionLookup(atmSessionRepository, Clock.systemUTC());
    }

    @Bean
    public VerifyPinUseCase verifyPinUseCase(
            CardRepository cardRepository, PinHasher pinHasher, AtmSessionRepository atmSessionRepository) {
        return new VerifyPinUseCase(cardRepository, pinHasher, atmSessionRepository, Clock.systemUTC());
    }
}
