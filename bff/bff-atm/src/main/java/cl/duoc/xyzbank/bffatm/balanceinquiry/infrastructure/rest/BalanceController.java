package cl.duoc.xyzbank.bffatm.balanceinquiry.infrastructure.rest;

import cl.duoc.xyzbank.bffatm.balanceinquiry.application.dto.BalanceResponse;
import cl.duoc.xyzbank.bffatm.balanceinquiry.application.usecases.BalanceInquiryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BalanceController {

    private final BalanceInquiryUseCase balanceInquiryUseCase;

    public BalanceController(BalanceInquiryUseCase balanceInquiryUseCase) {
        this.balanceInquiryUseCase = balanceInquiryUseCase;
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        return balanceInquiryUseCase.execute(accountId);
    }
}
