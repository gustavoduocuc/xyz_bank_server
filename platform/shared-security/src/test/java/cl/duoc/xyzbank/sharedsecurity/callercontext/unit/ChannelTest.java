package cl.duoc.xyzbank.sharedsecurity.callercontext.unit;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The Channel")
class ChannelTest {

    /*
     * Cases:
     * 1. Web channel grants exactly the four enumerated web scopes
     * 2. Mobile channel grants exactly the two enumerated mobile scopes
     * 3. ATM channel grants exactly atm:read-balance and atm:withdraw
     * 4. Interests channel grants exactly interests:write
     */

    @Test
    @DisplayName("web channel grants exactly the enumerated web scopes")
    void webChannelGrantsExactlyTheEnumeratedWebScopes() {
        assertEquals(
                Set.of("web:accounts:read", "web:customers:read", "web:transactions:read", "web:interests:read"),
                Channel.WEB.scopes());
    }

    @Test
    @DisplayName("mobile channel grants exactly the enumerated mobile scopes")
    void mobileChannelGrantsExactlyTheEnumeratedMobileScopes() {
        assertEquals(Set.of("mobile:accounts:read", "mobile:transactions:read"), Channel.MOBILE.scopes());
    }

    @Test
    @DisplayName("atm channel grants exactly atm:read-balance and atm:withdraw")
    void atmChannelGrantsExactlyReadBalanceAndWithdraw() {
        assertEquals(Set.of("atm:read-balance", "atm:withdraw"), Channel.ATM.scopes());
    }

    @Test
    @DisplayName("interests channel grants exactly interests:write")
    void interestsChannelGrantsExactlyInterestsWrite() {
        assertEquals(Set.of("interests:write"), Channel.INTERESTS.scopes());
    }
}
