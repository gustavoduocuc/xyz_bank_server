package cl.duoc.xyzbank.interestsservice.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CoreServiceClientConfig")
class CoreServiceClientConfigTest {

    @Test
    @DisplayName("discovery-enabled RestClient builder is LoadBalanced when Eureka is enabled")
    void discoveryEnabledBuilderIsLoadBalancedWhenEurekaEnabled() throws Exception {
        Method method = CoreServiceClientConfig.class.getDeclaredMethod("loadBalancedRestClientBuilder");
        LoadBalanced loadBalanced = method.getAnnotation(LoadBalanced.class);
        ConditionalOnProperty condition = method.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(loadBalanced);
        assertNotNull(condition);
        assertEquals("eureka.client.enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertTrue(condition.matchIfMissing());
    }

    @Test
    @DisplayName("hermetic RestClient builder is used when Eureka is disabled")
    void hermeticBuilderWhenEurekaDisabled() throws Exception {
        Method method = CoreServiceClientConfig.class.getDeclaredMethod("plainRestClientBuilder");
        ConditionalOnProperty condition = method.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(condition);
        assertEquals("eureka.client.enabled", condition.name()[0]);
        assertEquals("false", condition.havingValue());
    }
}
