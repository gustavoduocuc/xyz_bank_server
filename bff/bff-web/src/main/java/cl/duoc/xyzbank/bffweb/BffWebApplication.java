package cl.duoc.xyzbank.bffweb;

import cl.duoc.xyzbank.bffweb.interestview.config.InterestsFeatureProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InterestsFeatureProperties.class)
public class BffWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffWebApplication.class, args);
    }
}
