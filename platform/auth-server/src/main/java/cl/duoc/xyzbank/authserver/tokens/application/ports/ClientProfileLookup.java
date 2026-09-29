package cl.duoc.xyzbank.authserver.tokens.application.ports;

import cl.duoc.xyzbank.authserver.tokens.application.dto.ClientProfile;

import java.util.Optional;

public interface ClientProfileLookup {

    Optional<ClientProfile> profileOf(String clientId);
}
