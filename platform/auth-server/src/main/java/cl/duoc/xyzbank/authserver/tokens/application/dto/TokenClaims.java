package cl.duoc.xyzbank.authserver.tokens.application.dto;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

/**
 * The identity claims every issued token carries: "sub" (the customer id) and "channel".
 */
public record TokenClaims(String customerId, Channel channel) {
}
