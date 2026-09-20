package cl.duoc.xyzbank.coreservice;

import cl.duoc.xyzbank.coreservice.interests.config.InterestsFeatureProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InterestsFeatureProperties.class)
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
