package cl.duoc.xyzbank.coreservice.accounts.integration;

import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectory;
import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectoryUnavailableException;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The customers-service directory")
class CustomersServiceDirectoryIT extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. An existing customer (200) exists
     * 2. An unknown customer (404) does not exist
     * 3. A reset connection or a 503 makes the directory unavailable
     * 4. An answer slower than the read timeout makes the directory unavailable
     * 5. An open circuit makes the directory unavailable without calling customers-service
     */

    private static final String CUSTOMER = "11111111-1111-1111-1111-111111111111";
    private static final String PATH = "/internal/customers/" + CUSTOMER;
    private static final WireMockServer CUSTOMERS_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CUSTOMERS_SERVICE.start();
    }

    @DynamicPropertySource
    static void customersService(DynamicPropertyRegistry registry) {
        registry.add("customers-service.base-url", CUSTOMERS_SERVICE::baseUrl);
        registry.add("customers-service.read-timeout-ms", () -> "300");
    }

    @Autowired
    private CustomerDirectory customers;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void reset() {
        CUSTOMERS_SERVICE.resetAll();
        circuitBreakers.circuitBreaker("customersService").reset();
    }

    @AfterAll
    static void stop() {
        CUSTOMERS_SERVICE.stop();
    }

    @Test
    @DisplayName("an existing customer exists")
    void anExistingCustomerExists() {
        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"id\":\"" + CUSTOMER + "\"}")));

        assertTrue(customers.exists(CUSTOMER));
    }

    @Test
    @DisplayName("an unknown customer does not exist")
    void anUnknownCustomerDoesNotExist() {
        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        assertFalse(customers.exists(CUSTOMER));
    }

    @Test
    @DisplayName("a reset connection or a 503 makes the directory unavailable")
    void aResetConnectionOrA503MakesTheDirectoryUnavailable() {
        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThrows(CustomerDirectoryUnavailableException.class, () -> customers.exists(CUSTOMER));

        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));
        assertThrows(CustomerDirectoryUnavailableException.class, () -> customers.exists(CUSTOMER));
    }

    @Test
    @DisplayName("an answer slower than the read timeout makes the directory unavailable")
    void anAnswerSlowerThanTheReadTimeoutMakesTheDirectoryUnavailable() {
        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        assertThrows(CustomerDirectoryUnavailableException.class, () -> customers.exists(CUSTOMER));
    }

    @Test
    @DisplayName("an open circuit makes the directory unavailable without calling customers-service")
    void anOpenCircuitMakesTheDirectoryUnavailableWithoutCallingCustomersService() {
        circuitBreakers.circuitBreaker("customersService").transitionToOpenState();

        assertThrows(CustomerDirectoryUnavailableException.class, () -> customers.exists(CUSTOMER));
        CUSTOMERS_SERVICE.verify(0, anyRequestedFor(anyUrl()));
        assertTrue(circuitBreakers.circuitBreaker("customersService").getState() == CircuitBreaker.State.OPEN);
    }
}
