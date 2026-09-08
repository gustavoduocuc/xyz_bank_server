package cl.duoc.xyzbank.bffatm.balanceinquiry.application.ports;

import cl.duoc.xyzbank.bffatm.balanceinquiry.application.dto.BalanceResponse;

public interface AccountsPort {

    BalanceResponse fetchBalance(String accountId);
}
