package cl.duoc.xyzbank.interestsservice.interestview.application.ports;

import cl.duoc.xyzbank.interestsservice.interestview.application.dto.AccountBalanceResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.CreditInterestCommand;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;

public interface CoreServicePort {

    InterestSummaryResponse fetchInterestSummary(String accountId, String year);

    AccountBalanceResponse fetchAccountBalance(String accountId);

    InterestCreditResponse creditInterest(CreditInterestCommand command);
}
