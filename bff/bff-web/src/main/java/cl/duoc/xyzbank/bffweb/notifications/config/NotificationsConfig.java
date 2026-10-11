package cl.duoc.xyzbank.bffweb.notifications.config;

import cl.duoc.xyzbank.bffweb.notifications.application.ports.NotificationsPort;
import cl.duoc.xyzbank.bffweb.notifications.application.usecases.NotificationsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationsConfig {

    @Bean
    public NotificationsUseCase notificationsUseCase(NotificationsPort notificationsPort) {
        return new NotificationsUseCase(notificationsPort);
    }
}
