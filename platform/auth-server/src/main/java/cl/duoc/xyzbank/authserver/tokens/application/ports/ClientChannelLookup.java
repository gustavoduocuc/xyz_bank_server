package cl.duoc.xyzbank.authserver.tokens.application.ports;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Optional;

public interface ClientChannelLookup {

    Optional<Channel> channelOf(String clientId);
}
