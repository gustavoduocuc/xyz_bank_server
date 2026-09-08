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
     * 1. Web channel grants exactly web:*
     * 2. Mobile channel grants exactly mobile:*
     * 3. ATM channel grants exactly atm:read-balance and atm:withdraw
     */

    @Test
    @DisplayName("web channel grants exactly web:*")
    void webChannelGrantsExactlyWebStar() {
        assertEquals(Set.of("web:*"), Channel.WEB.scopes());
    }

    @Test
    @DisplayName("mobile channel grants exactly mobile:*")
    void mobileChannelGrantsExactlyMobileStar() {
        assertEquals(Set.of("mobile:*"), Channel.MOBILE.scopes());
    }

    @Test
    @DisplayName("atm channel grants exactly atm:read-balance and atm:withdraw")
    void atmChannelGrantsExactlyReadBalanceAndWithdraw() {
        assertEquals(Set.of("atm:read-balance", "atm:withdraw"), Channel.ATM.scopes());
    }
}
