package cl.duoc.xyzbank.sharedsecurity.callercontext;

import java.util.Set;

public enum Channel {

    WEB(Set.of("web:accounts:read", "web:customers:read", "web:transactions:read", "web:interests:read")),
    MOBILE(Set.of("mobile:accounts:read", "mobile:transactions:read")),
    ATM(Set.of("atm:read-balance", "atm:withdraw")),
    INTERESTS(Set.of("interests:write"));

    private final Set<String> scopes;

    Channel(Set<String> scopes) {
        this.scopes = scopes;
    }

    public Set<String> scopes() {
        return scopes;
    }
}
