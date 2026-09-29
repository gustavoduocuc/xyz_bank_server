package cl.duoc.xyzbank.interestsservice.shared.infrastructure.security;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Set;

/**
 * A caller's access token after validation: the client it was issued to, its channel, the
 * scopes it grants (restricted to that channel) and its subject.
 */
public record ValidatedAccessToken(String clientId, Channel channel, Set<String> scopes, String subject) {
}
