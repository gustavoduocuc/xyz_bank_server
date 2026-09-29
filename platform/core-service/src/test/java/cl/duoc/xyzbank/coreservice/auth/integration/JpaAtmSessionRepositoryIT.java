package cl.duoc.xyzbank.coreservice.auth.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.persistence.JpaAtmSessionRepository;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA ATM session repository")
class JpaAtmSessionRepositoryIT extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Saves an ATM session and finds it by id
     * 2. Finds nothing for an unknown or malformed session id
     * 3. The lookup the enforcement filter uses yields the customer of an active session only
     */

    @Autowired
    private JpaAtmSessionRepository atmSessionRepository;

    @Autowired
    private AtmSessionLookup atmSessionLookup;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CardRepository cardRepository;

    @Test
    @DisplayName("saves an ATM session and finds it by id")
    void savesAnAtmSessionAndFindsItById() {
        AtmSession session = AtmSession.open(aCustomer(), aCardOf(aCustomer()), Instant.now());

        atmSessionRepository.save(session);

        AtmSession found = atmSessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(session.getCustomerId(), found.getCustomerId());
        assertEquals(session.getExpiresAt().toEpochMilli(), found.getExpiresAt().toEpochMilli());
    }

    @Test
    @DisplayName("finds nothing for an unknown or malformed session id")
    void findsNothingForAnUnknownOrMalformedSessionId() {
        assertTrue(atmSessionRepository.findById(Id.generate()).isEmpty());
        assertTrue(atmSessionRepository.findById(Id.create("not-a-uuid")).isEmpty());
    }

    @Test
    @DisplayName("yields the customer of an active session only")
    void yieldsTheCustomerOfAnActiveSessionOnly() {
        Id customer = aCustomer();
        Id card = aCardOf(customer);
        AtmSession active = AtmSession.open(customer, card, Instant.now());
        AtmSession expired = AtmSession.open(customer, card, Instant.now().minusSeconds(121));
        atmSessionRepository.save(active);
        atmSessionRepository.save(expired);

        assertEquals(Optional.of(customer.getValue()), atmSessionLookup.activeCustomerOf(active.getId().getValue()));
        assertTrue(atmSessionLookup.activeCustomerOf(expired.getId().getValue()).isEmpty());
        assertTrue(atmSessionLookup.activeCustomerOf("not-a-uuid").isEmpty());
    }

    private Id aCustomer() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "ATM Customer", customerId.getValue() + "@xyzbank.cl"));
        return customerId;
    }

    private Id aCardOf(Id customerId) {
        Id cardId = Id.generate();
        cardRepository.save(Card.create(cardId, customerId, "{noop}pin", 0, false, 0L));
        return cardId;
    }
}
