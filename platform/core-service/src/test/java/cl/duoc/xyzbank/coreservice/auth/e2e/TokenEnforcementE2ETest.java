package cl.duoc.xyzbank.coreservice.auth.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.AtmSessionRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;

/**
 * The acceptance criteria of adopt-oauth2-tokens-between-services for core-service, over the
 * full HTTP stack with tokens verified against a JWKS exactly like auth-server's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("core-service's token enforcement")
class TokenEnforcementE2ETest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. A call without a token is rejected with 401
     * 2. A formerly valid static service credential alone is rejected with 401
     * 3. A token with insufficient scope is rejected with 403
     * 4. A web token on the ATM withdrawal endpoint is rejected with 403
     * 5. A mobile token on a web-only endpoint is rejected with 403
     * 6. An expired, foreign-signed, wrong-issuer or wrong-audience token is rejected with 401
     * 7. A token whose client does not match its channel is rejected with 401
     * 8. An interests-service token without interests:write cannot credit interest (403)
     * 9. The bff-atm token with an active ATM session reaches that session's customer's account
     * 10. An unknown, expired or missing ATM session is rejected with 401
     * 11. An ATM session cannot reach another customer's account (404)
     * 12. PIN verification refuses a call without a token (401) and another channel's token (403)
     */

    private static final String ANY_ACCOUNT = UUID.randomUUID().toString();
    private static final String ANY_CUSTOMER = UUID.randomUUID().toString();
    private static final int PIN_VERIFICATION_TLS_PORT = 8453;

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private AtmSessionRepository atmSessionRepository;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("rejects a call without a token with 401")
    void rejectsACallWithoutATokenWith401() {
        given().get("/internal/accounts/{id}/balance", ANY_ACCOUNT).then().statusCode(401);
    }

    @Test
    @DisplayName("rejects a formerly valid static service credential alone with 401")
    void rejectsAFormerlyValidStaticServiceCredentialAloneWith401() {
        given().header("X-Service-Credential", "dev-service-credential-web")
                .get("/internal/accounts/{id}/balance", ANY_ACCOUNT)
                .then().statusCode(401);
    }

    @Test
    @DisplayName("rejects a token with insufficient scope with 403")
    void rejectsATokenWithInsufficientScopeWith403() {
        String webTokenWithoutCustomersScope = TestAccessTokens.token("bff-web", Channel.WEB)
                .subject(ANY_CUSTOMER).scopes(Set.of("web:accounts:read")).sign();

        bearer(webTokenWithoutCustomersScope).get("/internal/customers/{id}", ANY_CUSTOMER).then().statusCode(403);
    }

    @Test
    @DisplayName("rejects a web token on the ATM withdrawal endpoint with 403")
    void rejectsAWebTokenOnTheAtmWithdrawalEndpointWith403() {
        bearer(TestAccessTokens.web(ANY_CUSTOMER))
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body("{\"amount\":10.00,\"currency\":\"USD\"}")
                .post("/internal/accounts/{id}/withdrawals", ANY_ACCOUNT)
                .then().statusCode(403);
    }

    @Test
    @DisplayName("rejects a mobile token on a web-only endpoint with 403")
    void rejectsAMobileTokenOnAWebOnlyEndpointWith403() {
        bearer(TestAccessTokens.mobile(ANY_CUSTOMER, "D1"))
                .get("/internal/accounts/{id}/interest-summary?year=2025", ANY_ACCOUNT)
                .then().statusCode(403);
    }

    static Stream<String> invalidTokens() {
        return Stream.of(
                TestAccessTokens.token("bff-web", Channel.WEB).subject(ANY_CUSTOMER)
                        .expiredAt(Instant.parse("2020-01-01T00:00:00Z")).sign(),
                TestAccessTokens.token("bff-web", Channel.WEB).subject(ANY_CUSTOMER).signedWithForeignKey().sign(),
                TestAccessTokens.token("bff-web", Channel.WEB).subject(ANY_CUSTOMER).issuer("https://impostor.example").sign(),
                TestAccessTokens.token("bff-web", Channel.WEB).subject(ANY_CUSTOMER).audience("interests-service").sign());
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    @DisplayName("rejects an expired, foreign-signed, wrong-issuer or wrong-audience token with 401")
    void rejectsAnInvalidTokenWith401(String token) {
        bearer(token).get("/internal/accounts/{id}/balance", ANY_ACCOUNT).then().statusCode(401);
    }

    @Test
    @DisplayName("rejects a token whose client does not match its channel with 401")
    void rejectsATokenWhoseClientDoesNotMatchItsChannelWith401() {
        String webClientClaimingMobile = TestAccessTokens.token("bff-web", Channel.WEB)
                .subject(ANY_CUSTOMER).channel("MOBILE").sign();

        bearer(webClientClaimingMobile).get("/internal/accounts/{id}/balance", ANY_ACCOUNT).then().statusCode(401);
    }

    @Test
    @DisplayName("keeps an interests-service token without interests:write from crediting interest")
    void keepsAnInterestsServiceTokenWithoutInterestsWriteFromCreditingInterest() {
        String interestsTokenWithoutScope = TestAccessTokens.token("interests-service", Channel.INTERESTS)
                .subject("interests-service").scopes(Set.of()).sign();

        bearer(interestsTokenWithoutScope)
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/internal/accounts/{id}/interest-credits", ANY_ACCOUNT)
                .then().statusCode(403);
    }

    @Test
    @DisplayName("lets the bff-atm token with an active ATM session reach that session's customer's account")
    void letsTheBffAtmTokenWithAnActiveAtmSessionReachItsCustomersAccount() {
        Id customer = aCustomer();
        Id account = anAccountOf(customer);
        AtmSession session = anAtmSessionOf(customer, Instant.now());

        atm(session.getId().getValue())
                .get("/internal/accounts/{id}/balance", account.getValue())
                .then().statusCode(200);
    }

    @Test
    @DisplayName("rejects an unknown, expired or missing ATM session with 401")
    void rejectsAnUnknownExpiredOrMissingAtmSessionWith401() {
        Id customer = aCustomer();
        Id account = anAccountOf(customer);
        AtmSession expired = anAtmSessionOf(customer, Instant.now().minusSeconds(121));

        atm(UUID.randomUUID().toString()).get("/internal/accounts/{id}/balance", account.getValue())
                .then().statusCode(401);
        atm(expired.getId().getValue()).get("/internal/accounts/{id}/balance", account.getValue())
                .then().statusCode(401);
        bearer(TestAccessTokens.atm()).get("/internal/accounts/{id}/balance", account.getValue())
                .then().statusCode(401);
    }

    @Test
    @DisplayName("keeps an ATM session from reaching another customer's account")
    void keepsAnAtmSessionFromReachingAnotherCustomersAccount() {
        Id otherCustomersAccount = anAccountOf(aCustomer());
        AtmSession session = anAtmSessionOf(aCustomer(), Instant.now());

        atm(session.getId().getValue())
                .get("/internal/accounts/{id}/balance", otherCustomersAccount.getValue())
                .then().statusCode(404);
    }

    @Test
    @DisplayName("refuses PIN verification without a token or with another channel's token")
    void refusesPinVerificationWithoutATokenOrWithAnotherChannelsToken() {
        String body = "{\"cardNumber\":\"77777777-7777-7777-7777-777777777777\",\"pin\":\"1234\"}";

        // PIN verification is served only on core-service's TLS connector
        overPinConnector(given()).contentType(ContentType.JSON).body(body)
                .post("/internal/auth/atm/pin-verifications").then().statusCode(401);
        overPinConnector(bearer(TestAccessTokens.web(ANY_CUSTOMER))).contentType(ContentType.JSON).body(body)
                .post("/internal/auth/atm/pin-verifications").then().statusCode(403);
    }

    private static RequestSpecification overPinConnector(RequestSpecification request) {
        return request.baseUri("https://localhost").port(PIN_VERIFICATION_TLS_PORT).relaxedHTTPSValidation();
    }

    private static RequestSpecification atm(String atmSessionId) {
        return bearer(TestAccessTokens.atm()).header("X-Atm-Session", atmSessionId);
    }

    private Id aCustomer() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "ATM Customer", customerId.getValue() + "@xyzbank.cl"));
        return customerId;
    }

    private Id anAccountOf(Id customerId) {
        Account account = Account.create(
                Id.generate(), AccountNumber.create(String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L))),
                customerId, Money.create(new BigDecimal("500.00"), "USD"));
        accountRepository.save(account);
        return account.getId();
    }

    private AtmSession anAtmSessionOf(Id customerId, Instant openedAt) {
        Id cardId = Id.generate();
        cardRepository.save(Card.create(cardId, customerId, "{noop}pin", 0, false, 0L));
        AtmSession session = AtmSession.open(customerId, cardId, openedAt);
        atmSessionRepository.save(session);
        return session;
    }

    private static RequestSpecification bearer(String token) {
        return given().header("Authorization", "Bearer " + token);
    }
}
