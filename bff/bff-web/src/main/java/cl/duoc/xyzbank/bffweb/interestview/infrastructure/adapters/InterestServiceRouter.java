package cl.duoc.xyzbank.bffweb.interestview.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.interestview.config.InterestsFeatureProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class InterestServiceRouter implements InterestPort {

    private static final Logger log = LoggerFactory.getLogger(InterestServiceRouter.class);

    private final InterestPort coreServiceAdapter;
    private final InterestPort interestsServiceAdapter;
    private final InterestsFeatureProperties featureProperties;

    public InterestServiceRouter(
            @Qualifier("httpInterestAdapter") InterestPort coreServiceAdapter,
            @Qualifier("interestsServiceAdapter") InterestPort interestsServiceAdapter,
            InterestsFeatureProperties featureProperties) {
        this.coreServiceAdapter = coreServiceAdapter;
        this.interestsServiceAdapter = interestsServiceAdapter;
        this.featureProperties = featureProperties;
    }

    @Override
    public InterestViewResponse fetchSummary(String accountId, String year) {
        if (featureProperties.useInterestsService()) {
            log.info("INTEREST_ROUTING target=interests-service accountId={}", accountId);
            return interestsServiceAdapter.fetchSummary(accountId, year);
        }
        log.info("INTEREST_ROUTING target=core-service accountId={}", accountId);
        return coreServiceAdapter.fetchSummary(accountId, year);
    }
}
