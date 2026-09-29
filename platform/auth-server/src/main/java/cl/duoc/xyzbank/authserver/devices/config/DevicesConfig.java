package cl.duoc.xyzbank.authserver.devices.config;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.devices.application.usecases.AssertDeviceActiveUseCase;
import cl.duoc.xyzbank.authserver.devices.application.usecases.RegisterDeviceForLoginUseCase;
import cl.duoc.xyzbank.authserver.devices.application.usecases.RevokeDeviceUseCase;
import cl.duoc.xyzbank.authserver.devices.domain.repositories.DeviceRegistrationRepository;
import cl.duoc.xyzbank.authserver.devices.infrastructure.adapters.DeviceAuthorizationRequestValidator;
import cl.duoc.xyzbank.authserver.devices.infrastructure.persistence.JdbcDeviceRegistrationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DevicesConfig {

    @Bean
    public DeviceRegistrationRepository deviceRegistrationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDeviceRegistrationRepository(jdbcTemplate);
    }

    @Bean
    public AssertDeviceActiveUseCase assertDeviceActiveUseCase(DeviceRegistrationRepository devices) {
        return new AssertDeviceActiveUseCase(devices);
    }

    @Bean
    public RegisterDeviceForLoginUseCase registerDeviceForLoginUseCase(DeviceRegistrationRepository devices) {
        return new RegisterDeviceForLoginUseCase(devices);
    }

    @Bean
    public RevokeDeviceUseCase revokeDeviceUseCase(DeviceRegistrationRepository devices) {
        return new RevokeDeviceUseCase(devices);
    }

    @Bean
    public DeviceAuthorizationRequestValidator deviceAuthorizationRequestValidator(
            ChannelClientRepository channelClients, AssertDeviceActiveUseCase assertDeviceActive) {
        return new DeviceAuthorizationRequestValidator(channelClients, assertDeviceActive);
    }
}
