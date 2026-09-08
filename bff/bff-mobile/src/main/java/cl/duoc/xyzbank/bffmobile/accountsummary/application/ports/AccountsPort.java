package cl.duoc.xyzbank.bffmobile.accountsummary.application.ports;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountBalance;

public interface AccountsPort {

    AccountBalance fetchBalance(String accountId);
}
