package cl.duoc.xyzbank.coreservice.auth.application.ports;

import cl.duoc.xyzbank.coreservice.auth.application.dto.VerifiedAccessToken;

public interface AccessTokenVerifier {

    /**
     * @throws InvalidAccessTokenException when the token is not a valid platform token for core-service
     */
    VerifiedAccessToken verify(String accessToken);
}
