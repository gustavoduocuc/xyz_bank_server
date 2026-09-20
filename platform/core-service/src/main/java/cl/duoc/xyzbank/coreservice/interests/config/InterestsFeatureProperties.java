package cl.duoc.xyzbank.coreservice.interests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "features.interests")
public record InterestsFeatureProperties(
        boolean enabled,
        boolean shadowMode) {

    public boolean isActive() {
        return enabled && !shadowMode;
    }

    public boolean isShadowMode() {
        return enabled && shadowMode;
    }

    public boolean isDisabled() {
        return !enabled;
    }
}
