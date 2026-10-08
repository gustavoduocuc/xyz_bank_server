package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** The client id each channel's access tokens must be issued to. */
@ConfigurationProperties(prefix = "auth")
public record ChannelClientProperties(Map<Channel, String> channelClients) {
}
