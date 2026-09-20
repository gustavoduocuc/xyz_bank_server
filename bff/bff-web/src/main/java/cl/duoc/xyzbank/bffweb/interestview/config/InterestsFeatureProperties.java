package cl.duoc.xyzbank.bffweb.interestview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "features.interests")
public record InterestsFeatureProperties(boolean useInterestsService) {
}
