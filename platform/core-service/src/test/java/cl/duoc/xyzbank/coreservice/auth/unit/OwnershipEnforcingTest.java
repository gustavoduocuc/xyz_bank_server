package cl.duoc.xyzbank.coreservice.auth.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.DateRange;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The EnforcementFilter's ownership check")
class OwnershipEnforcingTest {

    /*
     * Cases:
     * 1. The owning customer's token is let through to a customer endpoint identifying themself
     * 2. The owning customer's token is let through to an account endpoint identifying their own account
     * 3. The owning customer's token is let through to a transaction endpoint identifying a transaction
     *    on their own account
     * 4. A non-owner's token is rejected as not-found for someone else's account
     * 5. A non-owner's token is rejected as not-found for someone else's customer profile
     * 6. A non-owner's token is rejected as not-found for a transaction whose account belongs
     *    to a different customer, resolved through the transaction's account, not any direct
     *    field on the transaction itself
     * 7. A URL-encoded blank identifier (what a browser/HTTP client sends for a literal blank
     *    path segment) is treated as malformed, not as an unowned resource: the request is let
     *    through for the controller's own 422, never a 500 from an unguarded UUID parse
     */

    private static final Map<String, Channel> CHANNELS_BY_CLIENT = Map.of(
            "bff-web", Channel.WEB, "bff-mobile", Channel.MOBILE,
            "bff-atm", Channel.ATM, "interests-service", Channel.INTERESTS);
    // core-service's ATM sessions, as the filter sees them: "atm-session:<customer>" is an active
    // session opened by that customer's verified PIN
    private static final String ATM_SESSION_PREFIX = "atm-session:";

    private final AccessTokenVerifier verifier = new JwtAccessTokenVerifier(decoder(), CHANNELS_BY_CLIENT);
    private final AtmSessionLookup atmSessions = sessionId -> sessionId.startsWith(ATM_SESSION_PREFIX)
            ? Optional.of(sessionId.substring(ATM_SESSION_PREFIX.length()))
            : Optional.empty();
    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();

    private EnforcementFilter filter() {
        return new EnforcementFilter(true, verifier, atmSessions, accountRepository, transactionRepository);
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

    private Account anAccountOwnedBy(Id customerId) {
        Account account = Account.create(
                Id.generate(), AccountNumber.create(randomAccountNumber()), customerId,
                Money.create(new BigDecimal("100.00"), "USD"));
        accountRepository.save(account);
        return account;
    }

    private String randomAccountNumber() {
        return String.valueOf(1000000000L + Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 1000000000L));
    }

    @Test
    @DisplayName("lets an owner through to their own customer endpoint")
    void letsAnOwnerThroughToTheirOwnCustomerEndpoint() throws Exception {
        Id customerId = Id.generate();
        String token = TestAccessTokens.web(customerId.getValue());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/customers/" + customerId.getValue());
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("lets an owner through to their own account endpoint")
    void letsAnOwnerThroughToTheirOwnAccountEndpoint() throws Exception {
        Id customerId = Id.generate();
        Account account = anAccountOwnedBy(customerId);
        String token = TestAccessTokens.web(customerId.getValue());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/accounts/" + account.getId().getValue() + "/balance");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("lets an owner through to a transaction on their own account")
    void letsAnOwnerThroughToATransactionOnTheirOwnAccount() throws Exception {
        Id customerId = Id.generate();
        Account account = anAccountOwnedBy(customerId);
        Transaction transaction = Transaction.create(
                Id.generate(), account.getId(), TransactionType.DEBIT,
                Money.create(new BigDecimal("10.00"), "USD"), LocalDate.now(), null);
        transactionRepository.save(transaction);
        String token = TestAccessTokens.web(customerId.getValue());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/transactions/" + transaction.getId().getValue());
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("rejects a non-owner's request for someone else's account as not-found")
    void rejectsANonOwnersRequestForSomeoneElsesAccountAsNotFound() throws Exception {
        Id ownerId = Id.generate();
        Account account = anAccountOwnedBy(ownerId);
        Id nonOwnerId = Id.generate();
        String token = TestAccessTokens.web(nonOwnerId.getValue());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/accounts/" + account.getId().getValue() + "/balance");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(404, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
    }

    @Test
    @DisplayName("rejects a request for a well-formed but genuinely nonexistent account id as not-found")
    void rejectsARequestForAWellFormedButNonexistentAccountIdAsNotFound() throws Exception {
        String token = TestAccessTokens.web(Id.generate().getValue());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/accounts/" + Id.generate().getValue() + "/balance");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(404, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
    }

    @Test
    @DisplayName("rejects a non-owner's request for someone else's customer profile as not-found")
    void rejectsANonOwnersRequestForSomeoneElsesCustomerProfileAsNotFound() throws Exception {
        Id ownerId = Id.generate();
        Id nonOwnerId = Id.generate();
        String token = TestAccessTokens.web(nonOwnerId.getValue());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/customers/" + ownerId.getValue());
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(404, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
    }

    @Test
    @DisplayName("rejects a non-owner's request for a transaction on someone else's account as not-found, "
            + "resolved through the transaction's account")
    void rejectsANonOwnersRequestForATransactionOnSomeoneElsesAccountAsNotFound() throws Exception {
        Id ownerId = Id.generate();
        Account account = anAccountOwnedBy(ownerId);
        Transaction transaction = Transaction.create(
                Id.generate(), account.getId(), TransactionType.DEBIT,
                Money.create(new BigDecimal("10.00"), "USD"), LocalDate.now(), null);
        transactionRepository.save(transaction);
        Id nonOwnerId = Id.generate();
        String token = TestAccessTokens.web(nonOwnerId.getValue());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/transactions/" + transaction.getId().getValue());
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(404, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
    }

    @Test
    @DisplayName("lets a URL-encoded blank account id through as malformed, not as an unowned resource")
    void letsAUrlEncodedBlankAccountIdThroughAsMalformed() throws Exception {
        String token = TestAccessTokens.web(Id.generate().getValue());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/accounts/%20%20%20/balance");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertTrue(chainCalled.get(), "a malformed identifier is the controller's 422 concern, not a 500 or 404");
    }

    @Test
    @DisplayName("rejects a non-owner's withdrawal against someone else's account before it ever executes, "
            + "leaving the balance and transaction history untouched")
    void rejectsANonOwnersWithdrawalBeforeItEverExecutes() throws Exception {
        Id ownerId = Id.generate();
        Account account = anAccountOwnedBy(ownerId);
        Id nonOwnerId = Id.generate();
        String atmToken = TestAccessTokens.atm();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/accounts/" + account.getId().getValue() + "/withdrawals");
        request.addHeader("Authorization", "Bearer " + atmToken);
        request.addHeader("X-Atm-Session", ATM_SESSION_PREFIX + nonOwnerId.getValue());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter().doFilter(request, response, chain);

        assertFalse(chainCalled.get(), "the withdrawal use case must never be dispatched to");
        assertEquals(404, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals(
                new BigDecimal("100.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
        assertTrue(
                transactionRepository.findByAccountId(
                                account.getId(), DateRange.create(Optional.empty(), Optional.empty()),
                                Optional.empty(), Optional.empty(), 10)
                        .getItems()
                        .isEmpty(),
                "no withdrawal transaction must be recorded");
    }
}
