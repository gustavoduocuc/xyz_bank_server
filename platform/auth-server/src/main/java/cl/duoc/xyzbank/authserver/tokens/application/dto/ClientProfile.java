package cl.duoc.xyzbank.authserver.tokens.application.dto;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Set;

/**
 * What a token needs to know about the client it is issued to: its channel and the services
 * its tokens are meant for.
 */
public record ClientProfile(Channel channel, Set<String> audiences) {
}
