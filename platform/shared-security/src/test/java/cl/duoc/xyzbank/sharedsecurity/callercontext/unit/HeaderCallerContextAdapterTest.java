package cl.duoc.xyzbank.sharedsecurity.callercontext.unit;

import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.HeaderCallerContextAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The HeaderCallerContextAdapter")
class HeaderCallerContextAdapterTest {

    /*
     * Cases:
     * 1. Resolves a web caller from customer id and channel headers
     * 2. Resolves a mobile caller from customer id and channel headers
     * 3. Resolves an atm caller including its terminal id
     * 4. Rejects a missing customer id
     * 5. Rejects a missing channel
     * 6. Rejects an unrecognized channel
     * 7. Rejects an atm channel missing its terminal id
     */

    @Test
    @DisplayName("resolves a web caller from customer id and channel headers")
    void resolvesAWebCallerFromCustomerIdAndChannelHeaders() {
        CallerContext callerContext = HeaderCallerContextAdapter.resolve("customer-1", "web", null);

        assertEquals("customer-1", callerContext.customerId());
        assertEquals(Channel.WEB, callerContext.channel());
        assertEquals(Set.of("web:*"), callerContext.scopes());
    }

    @Test
    @DisplayName("resolves a mobile caller from customer id and channel headers")
    void resolvesAMobileCallerFromCustomerIdAndChannelHeaders() {
        CallerContext callerContext = HeaderCallerContextAdapter.resolve("customer-2", "mobile", null);

        assertEquals("customer-2", callerContext.customerId());
        assertEquals(Channel.MOBILE, callerContext.channel());
        assertEquals(Set.of("mobile:*"), callerContext.scopes());
    }

    @Test
    @DisplayName("resolves an atm caller including its terminal id")
    void resolvesAnAtmCallerIncludingItsTerminalId() {
        CallerContext callerContext = HeaderCallerContextAdapter.resolve("customer-3", "atm", "terminal-9");

        assertEquals("customer-3", callerContext.customerId());
        assertEquals(Channel.ATM, callerContext.channel());
        assertEquals(Set.of("atm:read-balance", "atm:withdraw"), callerContext.scopes());
        assertEquals("terminal-9", callerContext.terminalId().orElseThrow());
    }

    @Test
    @DisplayName("rejects a missing customer id")
    void rejectsAMissingCustomerId() {
        CallerIdentityException exception = assertThrows(
                CallerIdentityException.class,
                () -> HeaderCallerContextAdapter.resolve(null, "web", null));

        assertEquals(CallerIdentityException.Type.INVALID, exception.getType());
    }

    @Test
    @DisplayName("rejects a missing channel")
    void rejectsAMissingChannel() {
        CallerIdentityException exception = assertThrows(
                CallerIdentityException.class,
                () -> HeaderCallerContextAdapter.resolve("customer-1", null, null));

        assertEquals(CallerIdentityException.Type.INVALID, exception.getType());
    }

    @Test
    @DisplayName("rejects an unrecognized channel")
    void rejectsAnUnrecognizedChannel() {
        CallerIdentityException exception = assertThrows(
                CallerIdentityException.class,
                () -> HeaderCallerContextAdapter.resolve("customer-1", "desktop", null));

        assertEquals(CallerIdentityException.Type.INVALID, exception.getType());
    }

    @Test
    @DisplayName("rejects an atm channel missing its terminal id")
    void rejectsAnAtmChannelMissingItsTerminalId() {
        CallerIdentityException exception = assertThrows(
                CallerIdentityException.class,
                () -> HeaderCallerContextAdapter.resolve("customer-1", "atm", null));

        assertEquals(CallerIdentityException.Type.INVALID, exception.getType());
    }
}
