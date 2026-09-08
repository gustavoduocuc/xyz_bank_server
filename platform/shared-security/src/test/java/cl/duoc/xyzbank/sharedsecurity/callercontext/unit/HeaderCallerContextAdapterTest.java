package cl.duoc.xyzbank.sharedsecurity.callercontext.unit;

import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.HeaderCallerContextAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The HeaderCallerContextAdapter")
class HeaderCallerContextAdapterTest {

    /*
     * Cases:
     * 1. Resolves a web caller from customer id and channel headers
     * 2. Resolves a mobile caller from customer id and channel headers
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
}
