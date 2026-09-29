package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.coredomain.auth.domain.repositories.DeviceRegistrationRepository;
import cl.duoc.xyzbank.coredomain.auth.domain.repositories.RefreshTokenRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.services.PinHasher;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.RevokeDeviceUseCase;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.RotateMobileRefreshTokenUseCase;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.RotateWebRefreshTokenUseCase;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import cl.duoc.xyzbank.sharedsecurity.callercontext.OpaqueTokenGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

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

    // Until core-service records ATM sessions (verified PINs), no ATM session is known
    @Bean
    public AtmSessionLookup atmSessionLookup() {
        return atmSessionId -> Optional.empty();
    }

    @Bean
    public OpaqueTokenGenerator opaqueTokenGenerator() {
        return new OpaqueTokenGenerator();
    }

    @Bean
    public VerifyPinUseCase verifyPinUseCase(CardRepository cardRepository, PinHasher pinHasher) {
        return new VerifyPinUseCase(cardRepository, pinHasher);
    }

    @Bean
    public RotateWebRefreshTokenUseCase rotateWebRefreshTokenUseCase(
            RefreshTokenRepository refreshTokenRepository, OpaqueTokenGenerator tokenGenerator) {
        return new RotateWebRefreshTokenUseCase(refreshTokenRepository, tokenGenerator);
    }

    @Bean
    public RotateMobileRefreshTokenUseCase rotateMobileRefreshTokenUseCase(
            RefreshTokenRepository refreshTokenRepository,
            DeviceRegistrationRepository deviceRegistrationRepository,
            OpaqueTokenGenerator tokenGenerator) {
        return new RotateMobileRefreshTokenUseCase(refreshTokenRepository, deviceRegistrationRepository,
                tokenGenerator);
    }

    @Bean
    public RevokeDeviceUseCase revokeDeviceUseCase(DeviceRegistrationRepository deviceRegistrationRepository) {
        return new RevokeDeviceUseCase(deviceRegistrationRepository);
    }
}
