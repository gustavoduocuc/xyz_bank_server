package cl.duoc.xyzbank.authserver.customers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.demo-customer")
public record DemoCustomerProperties(String username, String password, String customerId) {
}
