package cl.duoc.xyzbank.coreservice.auth.application.dto;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Optional;
import java.util.Set;

/**
 * What a verified access token says about its caller: the client it was issued to, its
 * channel, the scopes it grants (already restricted to that channel), its subject (a customer
 * for web and mobile, the service itself for service tokens) and, for mobile, its device.
 */
public record VerifiedAccessToken(
        String clientId, Channel channel, Set<String> scopes, String subject, Optional<String> deviceId) {
}
