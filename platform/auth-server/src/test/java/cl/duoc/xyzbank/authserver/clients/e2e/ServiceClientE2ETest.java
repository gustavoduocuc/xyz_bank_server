package cl.duoc.xyzbank.authserver.clients.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.text.ParseException;
import java.util.Set;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_PASSWORD;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_USERNAME;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.authorizationRequest;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.newCodeVerifier;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.queryParam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The service clients")
class ServiceClientE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Each service client obtains an access token with its secret, and no refresh or ID token
     * 2. A service client with a wrong or missing secret is rejected
     * 3. interests-service cannot obtain any scope but interests:write
     * 4. bff-atm cannot obtain interests:write
     * 5. A service client cannot start a customer login
     * 6. The login clients cannot use client_credentials
     */

    private static final String ATM_SECRET = "bff-atm-dev-secret";
    private static final String INTERESTS_SECRET = "interests-service-dev-secret";
    private static final String MISSING_SECRET = "";

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @ParameterizedTest
    @CsvSource({
            "bff-atm, bff-atm-dev-secret",
            "interests-service, interests-service-dev-secret",
            "customers-admin, customers-admin-dev-secret",
            "accounts-admin, accounts-admin-dev-secret",
            "payments-admin, payments-admin-dev-secret",
            "payments-service, payments-service-dev-secret"})
    @DisplayName("issues an access token, and no refresh or ID token, to a service client with its secret")
    void issuesAnAccessTokenToAServiceClientWithItsSecret(String clientId, String secret) {
        Response response = clientCredentials(clientId, secret, null);

        assertEquals(200, response.statusCode(), response.asString());
        assertNotNull(response.jsonPath().getString("access_token"));
        assertNull(response.jsonPath().getString("refresh_token"));
        assertNull(response.jsonPath().getString("id_token"));
        assertEquals(expectedScopes(clientId), scopesOf(response.jsonPath().getString("access_token")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-the-secret", MISSING_SECRET})
    @DisplayName("rejects a service client with a wrong or missing secret")
    void rejectsAServiceClientWithAWrongOrMissingSecret(String secret) {
        Response response = clientCredentials("bff-atm", secret, null);

        assertEquals(401, response.statusCode(), response.asString());
        assertNull(response.asString().isBlank() ? null : response.jsonPath().getString("access_token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"web:accounts:read", "mobile:accounts:read", "atm:withdraw"})
    @DisplayName("keeps interests-service from obtaining any scope but interests:write")
    void keepsInterestsServiceFromObtainingAnyScopeButInterestsWrite(String scope) {
        Response response = clientCredentials("interests-service", INTERESTS_SECRET, scope);

        assertEquals("invalid_scope", response.jsonPath().getString("error"), response.asString());
        assertNull(response.jsonPath().getString("access_token"));
    }

    @Test
    @DisplayName("keeps bff-atm from obtaining interests:write")
    void keepsBffAtmFromObtainingInterestsWrite() {
        Response response = clientCredentials("bff-atm", ATM_SECRET, "interests:write");

        assertEquals("invalid_scope", response.jsonPath().getString("error"), response.asString());
        assertNull(response.jsonPath().getString("access_token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bff-atm", "interests-service"})
    @DisplayName("refuses to start a customer login for a service client")
    void refusesToStartACustomerLoginForAServiceClient(String clientId) {
        String suppliedRedirect = "https://evil.example/callback";

        Response response = flow.authorize(
                authorizationRequest(clientId, suppliedRedirect, "openid", newCodeVerifier()),
                DEMO_USERNAME, DEMO_PASSWORD);

        String location = response.getHeader("Location");
        assertNull(queryParam(location, "code"), "a code was issued: " + location);
        assertFalse(location != null && location.startsWith(suppliedRedirect), "redirected to " + location);
    }

    @ParameterizedTest
    @CsvSource({"bff-web, bff-web-dev-secret", "bff-mobile, bff-mobile-dev-secret"})
    @DisplayName("refuses client_credentials to the login clients")
    void refusesClientCredentialsToTheLoginClients(String clientId, String secret) {
        Response response = clientCredentials(clientId, secret, null);

        assertEquals(400, response.statusCode(), response.asString());
        assertEquals("unauthorized_client", response.jsonPath().getString("error"));
    }

    private static Set<String> expectedScopes(String clientId) {
        return switch (clientId) {
            case "bff-atm" -> Set.of("atm:read-balance", "atm:withdraw");
            case "customers-admin" -> Set.of("customers:read", "customers:write");
            case "accounts-admin" -> Set.of("accounts:write", "customers:read");
            case "payments-admin" -> Set.of("payments:write", "payments:read");
            case "payments-service" -> Set.of("postings:write");
            default -> Set.of("interests:write");
        };
    }

    private static Set<String> scopesOf(String accessToken) {
        try {
            return Set.copyOf(SignedJWT.parse(accessToken).getJWTClaimsSet().getStringListClaim("scope"));
        } catch (ParseException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Response clientCredentials(String clientId, String secret, String scope) {
        var request = flow.request()
                .accept(ContentType.JSON)
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "client_credentials");
        // A missing secret means presenting the client id alone, as a public client would
        request = MISSING_SECRET.equals(secret)
                ? request.formParam("client_id", clientId)
                : request.auth().preemptive().basic(clientId, secret);
        if (scope != null) {
            request = request.formParam("scope", scope);
        }
        return request.post("/oauth2/token");
    }
}
