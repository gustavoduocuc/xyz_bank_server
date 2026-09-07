package cl.duoc.xyzbank.coreservice.accounts.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountSummaryResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CustomerProfileResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetCustomerProfileUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/customers")
public class CustomerController {

    private final GetCustomerProfileUseCase getCustomerProfileUseCase;
    private final ListAccountsForCustomerUseCase listAccountsForCustomerUseCase;

    public CustomerController(
            GetCustomerProfileUseCase getCustomerProfileUseCase,
            ListAccountsForCustomerUseCase listAccountsForCustomerUseCase) {
        this.getCustomerProfileUseCase = getCustomerProfileUseCase;
        this.listAccountsForCustomerUseCase = listAccountsForCustomerUseCase;
    }

    @GetMapping("/{customerId}")
    public CustomerProfileResponse getProfile(@PathVariable String customerId) {
        return getCustomerProfileUseCase.execute(customerId);
    }

    @GetMapping("/{customerId}/accounts")
    public List<AccountSummaryResponse> listAccounts(@PathVariable String customerId) {
        return listAccountsForCustomerUseCase.execute(customerId);
    }
}
