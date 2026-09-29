package cl.duoc.xyzbank.authserver.tokens.application.ports;

import java.util.Optional;

public interface CustomerIdLookup {

    Optional<String> customerIdOf(String username);
}
