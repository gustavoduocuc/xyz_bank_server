package cl.duoc.xyzbank.interestsservice.interests.infrastructure.adapters;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import org.springframework.stereotype.Component;

@Component
public class JwtServiceTokenAdapter implements ServiceTokenPort {

    private final JwtCallerContextAdapter jwtCallerContextAdapter;

    public JwtServiceTokenAdapter(JwtCallerContextAdapter jwtCallerContextAdapter) {
        this.jwtCallerContextAdapter = jwtCallerContextAdapter;
    }

    @Override
    public String issueServiceToken() {
        return jwtCallerContextAdapter.issue("interests-service", Channel.INTERESTS, null);
    }
}
