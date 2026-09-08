package cl.duoc.xyzbank.coreservice.shared.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The core-service demo fixture")
class DemoDataIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Loads the documented demo customer by the README UUID
     */

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("loads the documented demo customer")
    void loadsTheDocumentedDemoCustomer() {
        assertTrue(customerRepository
                .findById(Id.create("11111111-1111-1111-1111-111111111111"))
                .isPresent());
    }
}
