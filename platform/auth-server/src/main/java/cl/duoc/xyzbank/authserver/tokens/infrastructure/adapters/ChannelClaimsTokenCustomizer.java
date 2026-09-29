package cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.tokens.application.dto.TokenClaims;
import cl.duoc.xyzbank.authserver.tokens.application.usecases.IssueTokenClaimsUseCase;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;
import java.util.Optional;

/**
 * Stamps the identity claims on every access token and ID token (adopt-oauth2-tokens-between-
 * services design.md Decision 1): "sub" (customer or service), "channel" (from the client,
 * overwriting anything already present), and on access tokens also "azp", "aud" and, for
 * mobile logins, "device_id". ID tokens keep the OIDC audience (the client id).
 */
public class ChannelClaimsTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    static final String CHANNEL_CLAIM = "channel";
    static final String AUTHORIZED_PARTY_CLAIM = "azp";
    static final String DEVICE_ID_CLAIM = "device_id";
    public static final String DEVICE_ID_PARAMETER = "device_id";

    private final IssueTokenClaimsUseCase issueTokenClaimsUseCase;

    public ChannelClaimsTokenCustomizer(IssueTokenClaimsUseCase issueTokenClaimsUseCase) {
        this.issueTokenClaimsUseCase = issueTokenClaimsUseCase;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }
        TokenClaims tokenClaims = claimsFor(context);
        context.getClaims()
                .subject(tokenClaims.subject())
                .claim(CHANNEL_CLAIM, tokenClaims.channel().name());
        if (accessToken) {
            context.getClaims()
                    .claim(AUTHORIZED_PARTY_CLAIM, tokenClaims.authorizedParty())
                    .audience(List.copyOf(tokenClaims.audiences()));
            tokenClaims.deviceId().ifPresent(deviceId -> context.getClaims().claim(DEVICE_ID_CLAIM, deviceId));
        }
    }

    private TokenClaims claimsFor(JwtEncodingContext context) {
        String clientId = context.getRegisteredClient().getClientId();
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            return issueTokenClaimsUseCase.forService(clientId);
        }
        return issueTokenClaimsUseCase.forCustomer(
                clientId, context.getPrincipal().getName(), deviceIdOf(context.getAuthorization()));
    }

    private static Optional<String> deviceIdOf(OAuth2Authorization authorization) {
        return Optional.ofNullable(authorization)
                .map(existing -> existing.<OAuth2AuthorizationRequest>getAttribute(OAuth2AuthorizationRequest.class.getName()))
                .map(request -> request.getAdditionalParameters().get(DEVICE_ID_PARAMETER))
                .map(Object::toString);
    }
}
