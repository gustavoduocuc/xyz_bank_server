package cl.duoc.xyzbank.bffweb.shared.application;

import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;

/**
 * Resolves the caller behind a web session token. Implemented by the auth slice, which alone
 * knows how the token is verified (caller-context spec: only the channel's
 * credential-validating adapter reads the raw credential).
 */
public interface CallerContextResolver {

    /**
     * @throws CallerIdentityException INVALID when the token cannot be trusted, FORBIDDEN when
     *     it is trustworthy but was not issued for the web channel
     */
    CallerContext resolve(String sessionToken);
}
