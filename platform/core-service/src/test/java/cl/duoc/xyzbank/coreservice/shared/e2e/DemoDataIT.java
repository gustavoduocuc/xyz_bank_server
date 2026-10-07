package cl.duoc.xyzbank.coreservice.shared.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@DisplayName("The core-service demo fixture")
class DemoDataIT extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Loads the accounts of the documented demo customer (the customer itself lives in customers-service)
     */

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("loads the accounts of the documented demo customer")
    void loadsTheAccountsOfTheDocumentedDemoCustomer() {
        assertFalse(accountRepository
                .findByCustomerId(Id.create("11111111-1111-1111-1111-111111111111"))
                .isEmpty());
    }
}
