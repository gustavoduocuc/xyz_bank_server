package cl.duoc.xyzbank.bffmobile.shared.application;

import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;

/**
 * Resolves the caller behind a mobile access token. Implemented by the auth slice, which alone
 * knows how the token is verified (caller-context spec: only the channel's
 * credential-validating adapter reads the raw credential).
 */
public interface CallerContextResolver {

    /**
     * @throws CallerIdentityException INVALID when the token cannot be trusted, FORBIDDEN when
     *     it is trustworthy but was not issued for the mobile channel
     */
    CallerContext resolve(String accessToken);
}
