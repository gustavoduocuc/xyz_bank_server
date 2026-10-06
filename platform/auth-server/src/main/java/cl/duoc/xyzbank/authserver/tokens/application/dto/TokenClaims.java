package cl.duoc.xyzbank.authserver.tokens.application.dto;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Optional;
import java.util.Set;

/**
 * The identity claims an access token carries: "sub" (the customer, or the service itself for
 * service tokens), "channel", "azp" (the client it was issued to), "aud", and for mobile
 * logins "device_id".
 */
public record TokenClaims(
        String subject, Channel channel, String authorizedParty, Set<String> audiences, Optional<String> deviceId) {
}
