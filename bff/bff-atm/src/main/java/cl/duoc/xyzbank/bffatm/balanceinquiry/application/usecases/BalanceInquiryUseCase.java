package cl.duoc.xyzbank.bffatm.balanceinquiry.application.usecases;

import cl.duoc.xyzbank.bffatm.balanceinquiry.application.dto.BalanceResponse;
import cl.duoc.xyzbank.bffatm.balanceinquiry.application.ports.AccountsPort;

public class BalanceInquiryUseCase {

    private final AccountsPort accountsPort;

    public BalanceInquiryUseCase(AccountsPort accountsPort) {
        this.accountsPort = accountsPort;
    }

    public BalanceResponse execute(String accountId) {
        return accountsPort.fetchBalance(accountId);
    }
}
