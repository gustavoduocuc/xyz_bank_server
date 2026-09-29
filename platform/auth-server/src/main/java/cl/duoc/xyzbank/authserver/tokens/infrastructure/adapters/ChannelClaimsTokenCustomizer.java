package cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.tokens.application.dto.TokenClaims;
import cl.duoc.xyzbank.authserver.tokens.application.usecases.IssueTokenClaimsUseCase;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * Stamps "sub" (the customer id, not the login username) and "channel" (from the client the
 * token is issued to) on every access token and ID token (design.md Decision 4). Any
 * "channel" value already present is overwritten, so no request input can choose it.
 */
public class ChannelClaimsTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    static final String CHANNEL_CLAIM = "channel";

    private final IssueTokenClaimsUseCase issueTokenClaimsUseCase;

    public ChannelClaimsTokenCustomizer(IssueTokenClaimsUseCase issueTokenClaimsUseCase) {
        this.issueTokenClaimsUseCase = issueTokenClaimsUseCase;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!carriesCustomerIdentity(context.getTokenType())) {
            return;
        }
        TokenClaims tokenClaims = issueTokenClaimsUseCase.execute(
                context.getRegisteredClient().getClientId(), context.getPrincipal().getName());
        context.getClaims()
                .subject(tokenClaims.customerId())
                .claim(CHANNEL_CLAIM, tokenClaims.channel().name());
    }

    private static boolean carriesCustomerIdentity(OAuth2TokenType tokenType) {
        return OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)
                || OidcParameterNames.ID_TOKEN.equals(tokenType.getValue());
    }
}
