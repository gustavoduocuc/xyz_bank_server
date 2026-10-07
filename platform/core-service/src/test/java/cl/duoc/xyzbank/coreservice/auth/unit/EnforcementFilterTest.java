package cl.duoc.xyzbank.coreservice.auth.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AccessTokenVerifier;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters.AccessTokenDecoders;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters.JwtAccessTokenVerifier;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.EnforcementFilter;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import com.nimbusds.jose.JOSEException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The EnforcementFilter")
class EnforcementFilterTest {

    /*
     * Cases:
     * 1. When disabled, every request passes through unchanged, even with no credentials
     * 2. When enabled, a valid service credential is let through on a domain endpoint with a sufficiently scoped user token
     * 3. When enabled, a valid service credential is let through on a pre-auth endpoint (no user token needed)
     * 4. When enabled, a missing service credential is rejected before reaching the chain
     * 5. When enabled, an unrecognized service credential is rejected before reaching the chain
     * 6. When enabled, a domain endpoint with a valid service credential but no user token is rejected
     * 7. When enabled, a domain endpoint with an expired user token is rejected
     * 8. When enabled, a domain endpoint with a user token lacking the required scope is rejected
     */

    // The caller's "service credential" is now the access token itself (its azp names the
    // client); tokens are signed like auth-server's and verified with its public key.
    private static final Map<String, Channel> CHANNELS_BY_CLIENT = Map.of(
            "bff-web", Channel.WEB, "bff-mobile", Channel.MOBILE,
            "bff-atm", Channel.ATM, "interests-service", Channel.INTERESTS);
    private static final String OWNING_CUSTOMER_ID = "customer-1";
    private static final String ATM_SESSION_OF_OWNER = "atm-session-1";

    private final AccessTokenVerifier verifier = new JwtAccessTokenVerifier(decoder(), CHANNELS_BY_CLIENT);
    private final AtmSessionLookup atmSessions = sessionId -> ATM_SESSION_OF_OWNER.equals(sessionId)
            ? Optional.of(OWNING_CUSTOMER_ID)
            : Optional.empty();
    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();

    @BeforeEach
    void seedOwnedResources() {
        Account account = Account.create(
                Id.create("account-1"), AccountNumber.create("1234567890"), Id.create(OWNING_CUSTOMER_ID),
                Money.create(new BigDecimal("100.00"), "USD"));
        accountRepository.save(account);
        transactionRepository.save(Transaction.create(
                Id.create("transaction-1"), account.getId(), TransactionType.DEBIT,
                Money.create(new BigDecimal("10.00"), "USD"), LocalDate.now(), null));
    }

    private EnforcementFilter filter(boolean enabled) {
        return new EnforcementFilter(enabled, verifier, atmSessions, accountRepository, transactionRepository);
    }

    private static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestAccessTokens.publicKey().toRSAPublicKey()).build();
            decoder.setJwtValidator(AccessTokenDecoders.validator(TestAccessTokens.ISSUER, TestAccessTokens.AUDIENCE));
            return decoder;
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * A token of the given channel acting for the owning customer: web and mobile tokens carry
     * the customer as subject; the ATM service token reaches the customer through the ATM
     * session core-service recorded at PIN verification; the interests token acts for no customer.
     */
    private static void authorizeAsOwner(MockHttpServletRequest request, Channel channel) {
        switch (channel) {
            case WEB -> request.addHeader("Authorization", "Bearer " + TestAccessTokens.web(OWNING_CUSTOMER_ID));
            case MOBILE -> request.addHeader("Authorization", "Bearer " + TestAccessTokens.mobile(OWNING_CUSTOMER_ID, "D1"));
            case ATM -> {
                request.addHeader("Authorization", "Bearer " + TestAccessTokens.atm());
                request.addHeader("X-Atm-Session", ATM_SESSION_OF_OWNER);
            }
            case INTERESTS -> request.addHeader("Authorization", "Bearer " + TestAccessTokens.interests());
        }
    }

    @Test
    @DisplayName("when disabled, passes every request through unchanged, even with no credentials")
    void whenDisabledPassesEveryRequestThroughUnchanged() throws Exception {
        EnforcementFilter filter = filter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/any/balance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("when enabled, a valid credential and a sufficiently scoped token are let through on a domain endpoint")
    void whenEnabledValidCredentialAndScopedTokenLetThroughOnDomainEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/account-1/balance");
        authorizeAsOwner(request, Channel.WEB);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("when enabled, a valid service credential is let through on a pre-auth endpoint")
    void whenEnabledValidCredentialLetThroughOnPreAuthEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/auth/atm/pin-verifications");
        request.addHeader("Authorization", "Bearer " + TestAccessTokens.atm());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("when enabled, a missing service credential is rejected before reaching the chain")
    void whenEnabledMissingCredentialRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/any/balance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("when enabled, an unrecognized service credential is rejected before reaching the chain")
    void whenEnabledUnrecognizedCredentialRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/any/balance");
        request.addHeader("Authorization", "Bearer "
                + TestAccessTokens.token("bff-web", Channel.WEB).subject(OWNING_CUSTOMER_ID).signedWithForeignKey().sign());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("when enabled, a domain endpoint with no user token is rejected")
    void whenEnabledDomainEndpointWithNoUserTokenRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/any/balance");
        request.addHeader("Authorization", "Bearer " + TestAccessTokens.atm());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertFalse(chainCalled.get());
    }

    @Test
    @DisplayName("when enabled, a domain endpoint with an expired user token is rejected")
    void whenEnabledDomainEndpointWithExpiredUserTokenRejected() throws Exception {
        String expiredToken = TestAccessTokens.token("bff-web", Channel.WEB)
                .subject("customer-1").expiredAt(Instant.parse("2020-01-01T00:00:00Z")).sign();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/any/balance");
        request.addHeader("Authorization", "Bearer " + expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertFalse(chainCalled.get());
    }

    @Test
    @DisplayName("when enabled, a domain endpoint with a user token lacking the required scope is rejected")
    void whenEnabledDomainEndpointWithInsufficientScopeRejected() throws Exception {
        // /internal/customers/{id}/accounts requires web:accounts:read; a mobile token never has it
        String mobileToken = TestAccessTokens.mobile("customer-1", "D1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/customers/any/accounts");
        request.addHeader("Authorization", "Bearer " + mobileToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertFalse(chainCalled.get());
    }

    // The seven domain-endpoint table rows from channel-auth's spec, one case per row,
    // each proving the exact channel set allowed to call it and no others.
    static Stream<Arguments> domainEndpointRows() {
        return Stream.of(
                Arguments.of("GET", "/internal/customers/customer-1/accounts", EnumSet.of(Channel.WEB)),
                Arguments.of(
                        "GET",
                        "/internal/accounts/account-1/balance",
                        EnumSet.of(Channel.WEB, Channel.MOBILE, Channel.ATM, Channel.INTERESTS)),
                Arguments.of(
                        "GET",
                        "/internal/accounts/account-1/transactions",
                        EnumSet.of(Channel.WEB, Channel.MOBILE)),
                Arguments.of("GET", "/internal/accounts/account-1/interest-summary", EnumSet.of(Channel.WEB)),
                Arguments.of("GET", "/internal/transactions/transaction-1", EnumSet.of(Channel.WEB, Channel.MOBILE)),
                Arguments.of("POST", "/internal/accounts/account-1/withdrawals", EnumSet.of(Channel.ATM)),
                Arguments.of(
                        "POST", "/internal/accounts/account-1/interest-credits", EnumSet.of(Channel.INTERESTS)));
    }

    @ParameterizedTest(name = "{0} {1} allows only {2}")
    @MethodSource("domainEndpointRows")
    @DisplayName("enforces the exact required scope for each domain-endpoint table row")
    void enforcesExactRequiredScopePerDomainEndpoint(String method, String path, Set<Channel> allowedChannels)
            throws Exception {
        for (Channel channel : Channel.values()) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, path);
            authorizeAsOwner(request, channel);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainCalled = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainCalled.set(true);

            filter(true).doFilter(request, response, chain);

            boolean expectedAllowed = allowedChannels.contains(channel);
            assertEquals(
                    expectedAllowed,
                    chainCalled.get(),
                    "channel " + channel + " on " + method + " " + path);
        }
    }

    @Test
    @DisplayName("interests channel balance read skips customer ownership")
    void interestsChannelBalanceReadSkipsOwnership() throws Exception {
        String interestsToken = TestAccessTokens.interests();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/account-1/balance");
        request.addHeader("Authorization", "Bearer " + interestsToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("interest credit rejects an unrecognized service credential")
    void interestCreditRejectsUnrecognizedServiceCredential() throws Exception {
        String interestsToken = TestAccessTokens.token("interests-service", Channel.INTERESTS)
                .subject("interests-service").signedWithForeignKey().sign();
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/accounts/account-1/interest-credits");
        request.addHeader("Authorization", "Bearer " + interestsToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter(true).doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(401, response.getStatus());
    }
}
