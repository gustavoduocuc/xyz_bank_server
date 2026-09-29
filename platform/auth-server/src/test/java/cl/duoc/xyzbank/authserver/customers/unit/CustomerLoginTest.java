package cl.duoc.xyzbank.authserver.customers.unit;

import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The CustomerLogin")
class CustomerLoginTest {

    /*
     * Cases:
     * 1. Links a username and its password hash to the customer it identifies
     * 2. Rejects a blank username
     * 3. Rejects a blank password hash
     * 4. Is the same login as another with the same username
     * 5. Is a different login from one with another username
     */

    private static final CustomerId SEED_CUSTOMER = CustomerId.create("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("links a username and its password hash to the customer it identifies")
    void linksAUsernameAndItsPasswordHashToTheCustomerItIdentifies() {
        CustomerLogin login = CustomerLogin.create("demo", "{bcrypt}hash", SEED_CUSTOMER);

        assertEquals("demo", login.username());
        assertEquals("{bcrypt}hash", login.passwordHash());
        assertEquals(SEED_CUSTOMER, login.customerId());
    }

    @Test
    @DisplayName("rejects a blank username")
    void rejectsABlankUsername() {
        DomainException exception =
                assertThrows(DomainException.class, () -> CustomerLogin.create(" ", "{bcrypt}hash", SEED_CUSTOMER));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a blank password hash")
    void rejectsABlankPasswordHash() {
        DomainException exception =
                assertThrows(DomainException.class, () -> CustomerLogin.create("demo", "", SEED_CUSTOMER));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("is the same login as another with the same username")
    void isTheSameLoginAsAnotherWithTheSameUsername() {
        CustomerLogin login = CustomerLogin.create("demo", "{bcrypt}hash", SEED_CUSTOMER);
        CustomerLogin sameLogin = CustomerLogin.create("demo", "{bcrypt}other-hash", SEED_CUSTOMER);

        assertEquals(login, sameLogin);
        assertEquals(login.hashCode(), sameLogin.hashCode());
    }

    @Test
    @DisplayName("is a different login from one with another username")
    void isADifferentLoginFromOneWithAnotherUsername() {
        CustomerLogin login = CustomerLogin.create("demo", "{bcrypt}hash", SEED_CUSTOMER);
        CustomerLogin otherLogin = CustomerLogin.create("other", "{bcrypt}hash", SEED_CUSTOMER);

        assertNotEquals(login, otherLogin);
    }
}
