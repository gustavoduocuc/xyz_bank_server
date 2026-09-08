package cl.duoc.xyzbank.bffatm.shared.config;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.CallerContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CallerContextInterceptor callerContextInterceptor;

    public WebMvcConfig(CallerContextInterceptor callerContextInterceptor) {
        this.callerContextInterceptor = callerContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(callerContextInterceptor)
                .excludePathPatterns("/actuator/**", "/v3/api-docs", "/v3/api-docs/**");
    }
}
