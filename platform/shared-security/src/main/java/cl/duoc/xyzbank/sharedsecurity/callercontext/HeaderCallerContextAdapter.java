package cl.duoc.xyzbank.sharedsecurity.callercontext;

import java.util.Optional;
import java.util.Set;

/**
 * Phase-1-only placeholder for real channel authentication. Resolves caller identity
 * purely from request headers, with no authentication or authorization guarantee.
 * It is meant to be replaced by real channel authentication (OAuth2/OIDC, device-bound
 * JWT, or mTLS + PIN) in a future change.
 */
public final class HeaderCallerContextAdapter implements CallerContext {

    private final String customerId;
    private final Channel channel;
    private final String terminalId;

    private HeaderCallerContextAdapter(String customerId, Channel channel, String terminalId) {
        this.customerId = customerId;
        this.channel = channel;
        this.terminalId = terminalId;
    }

    public static CallerContext resolve(String customerIdHeader, String channelHeader, String terminalIdHeader) {
        if (customerIdHeader == null || customerIdHeader.isBlank()) {
            throw CallerIdentityException.invalid("Customer id is required");
        }
        Channel channel = parseChannel(channelHeader);
        if (channel == Channel.ATM && (terminalIdHeader == null || terminalIdHeader.isBlank())) {
            throw CallerIdentityException.invalid("Terminal id is required for the atm channel");
        }
        return new HeaderCallerContextAdapter(customerIdHeader, channel, terminalIdHeader);
    }

    private static Channel parseChannel(String channelHeader) {
        if (channelHeader == null || channelHeader.isBlank()) {
            throw CallerIdentityException.invalid("Channel is required");
        }
        try {
            return Channel.valueOf(channelHeader.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CallerIdentityException.invalid("Unrecognized channel: " + channelHeader);
        }
    }

    @Override
    public String customerId() {
        return customerId;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public Set<String> scopes() {
        return channel.scopes();
    }

    @Override
    public Optional<String> terminalId() {
        return Optional.ofNullable(terminalId);
    }
}
