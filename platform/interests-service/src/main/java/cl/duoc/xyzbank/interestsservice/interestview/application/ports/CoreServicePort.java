package cl.duoc.xyzbank.interestsservice.interestview.application.ports;

import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;

public interface CoreServicePort {

    InterestSummaryResponse fetchInterestSummary(String accountId, String year, String bearerToken);
}
