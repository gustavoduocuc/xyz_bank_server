package cl.duoc.xyzbank.bffweb.interestview.application.ports;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;

public interface InterestPort {

    InterestViewResponse fetchSummary(String accountId, String year);
}
