package cl.duoc.xyzbank.coreservice.accounts.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountSummaryResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/customers")
public class CustomerController {

    private final ListAccountsForCustomerUseCase listAccountsForCustomerUseCase;

    public CustomerController(ListAccountsForCustomerUseCase listAccountsForCustomerUseCase) {
        this.listAccountsForCustomerUseCase = listAccountsForCustomerUseCase;
    }

    @GetMapping("/{customerId}/accounts")
    public List<AccountSummaryResponse> listAccounts(@PathVariable String customerId) {
        return listAccountsForCustomerUseCase.execute(customerId);
    }
}
