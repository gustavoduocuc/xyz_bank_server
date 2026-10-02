package cl.duoc.xyzbank.bffmobile.shared.unit;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.AccessTokenCallerContextAdapter;
import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffmobile.auth.testsupport.TestSessions;
import cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest.CallerContextInterceptor;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The CallerContextInterceptor")
class CallerContextInterceptorTest {

    /*
     * Cases:
     * 1. A valid mobile token whose device_id matches X-Device-Id is let through
     * 2. Missing bearer token is rejected
     * 3. A web token is rejected
     * 4. A token whose device_id does not match X-Device-Id is rejected
     * 5. A valid token presented with no X-Device-Id header at all is rejected
     */

    private static final MockOidcProvider AUTHORIZATION_SERVER = new MockOidcProvider();

    @BeforeAll
    static void startAuthorizationServer() {
        AUTHORIZATION_SERVER.start();
    }

    @AfterAll
    static void stopAuthorizationServer() {
        AUTHORIZATION_SERVER.stop();
    }

    private final CallerContextInterceptor interceptor = new CallerContextInterceptor(
            new AccessTokenCallerContextAdapter(
                    AccessTokenCallerContextAdapter.decoderFor(
                            "http://localhost:9999/mock-oidc/jwks", MockOidcProvider.ISSUER, new RestTemplate()),
                    MockOidcProvider.CLIENT_ID));

    private HandlerMethod aHandlerMethod() throws NoSuchMethodException {
        return new HandlerMethod(new DummyController(), "handle");
    }

    @Test
    @DisplayName("lets a valid mobile token through when its device_id matches X-Device-Id")
    void letsAValidMobileTokenThroughWhenItsDeviceIdMatches() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + MockOidcProvider.mobileAccessTokenFor("customer-1", "device-1"));
        request.addHeader("X-Device-Id", "device-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, aHandlerMethod()));
    }

    @Test
    @DisplayName("rejects a request with no bearer token")
    void rejectsARequestWithNoBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Id", "device-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CallerIdentityException exception = assertThrows(
                CallerIdentityException.class, () -> interceptor.preHandle(request, response, aHandlerMethod()));
        assertEquals(CallerIdentityException.Type.INVALID, exception.getType());
    }

    @Test
    @DisplayName("rejects a web token")
    void rejectsAWebToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TestSessions.webSessionFor("customer-1"));
        request.addHeader("X-Device-Id", "device-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CallerIdentityException exception = assertThrows(
                CallerIdentityException.class, () -> interceptor.preHandle(request, response, aHandlerMethod()));
        assertEquals(CallerIdentityException.Type.FORBIDDEN, exception.getType());
    }

    @Test
    @DisplayName("rejects a token whose device_id does not match X-Device-Id")
    void rejectsATokenWhoseDeviceIdDoesNotMatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + MockOidcProvider.mobileAccessTokenFor("customer-1", "device-1"));
        request.addHeader("X-Device-Id", "device-2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CallerIdentityException exception = assertThrows(
                CallerIdentityException.class, () -> interceptor.preHandle(request, response, aHandlerMethod()));
        assertEquals(CallerIdentityException.Type.INVALID, exception.getType());
    }

    @Test
    @DisplayName("rejects a valid token presented with no X-Device-Id header at all")
    void rejectsAValidTokenPresentedWithNoDeviceIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + MockOidcProvider.mobileAccessTokenFor("customer-1", "device-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                CallerIdentityException.class, () -> interceptor.preHandle(request, response, aHandlerMethod()));
    }

    static class DummyController {
        public void handle() {
        }
    }
}
