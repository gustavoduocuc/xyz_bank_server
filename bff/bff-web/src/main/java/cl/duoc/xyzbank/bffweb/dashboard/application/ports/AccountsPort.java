package cl.duoc.xyzbank.bffweb.dashboard.application.ports;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.AccountBalance;

import java.util.List;

public interface AccountsPort {

    List<AccountBalance> fetchAccountsForCustomer(String customerId);
}
