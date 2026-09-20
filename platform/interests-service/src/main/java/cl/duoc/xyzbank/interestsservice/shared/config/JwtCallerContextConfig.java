package cl.duoc.xyzbank.interestsservice.shared.config;

import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtCallerContextConfig {

    @Bean
    public JwtCallerContextAdapter jwtCallerContextAdapter(
            @Value("${channel-auth.jwt.secret}") String jwtSecret) {
        return new JwtCallerContextAdapter(jwtSecret);
    }
}
