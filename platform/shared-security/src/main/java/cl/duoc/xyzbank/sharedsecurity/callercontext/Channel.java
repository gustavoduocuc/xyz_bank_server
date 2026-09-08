package cl.duoc.xyzbank.sharedsecurity.callercontext;

import java.util.Set;

public enum Channel {

    WEB(Set.of("web:*")),
    MOBILE(Set.of("mobile:*")),
    ATM(Set.of("atm:read-balance", "atm:withdraw"));

    private final Set<String> scopes;

    Channel(Set<String> scopes) {
        this.scopes = scopes;
    }

    public Set<String> scopes() {
        return scopes;
    }
}
