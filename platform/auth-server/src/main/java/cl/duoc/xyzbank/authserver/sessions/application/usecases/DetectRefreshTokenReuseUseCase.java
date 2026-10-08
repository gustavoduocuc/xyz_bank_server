package cl.duoc.xyzbank.authserver.sessions.application.usecases;

import cl.duoc.xyzbank.authserver.sessions.application.dto.RefreshTokenVerdict;
import cl.duoc.xyzbank.authserver.sessions.application.ports.CurrentRefreshTokens;
import cl.duoc.xyzbank.authserver.sessions.application.ports.LoginRevoker;
import cl.duoc.xyzbank.authserver.sessions.application.ports.SecurityAlertPublisher;
import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;
import cl.duoc.xyzbank.authserver.tokens.application.ports.CustomerIdLookup;

/**
 * Classifies a presented refresh token. A token that was already rotated out means someone is
 * replaying an old token (or retrying a refresh that already succeeded): the whole login is
 * revoked, so neither the replayed token nor the one that replaced it works any more -- the
 * rule core-service enforced with its refresh-token chains.
 */
public class DetectRefreshTokenReuseUseCase {

    private final CurrentRefreshTokens currentTokens;
    private final RotatedRefreshTokenRepository rotatedTokens;
    private final LoginRevoker loginRevoker;
    private final CustomerIdLookup customerIds;
    private final SecurityAlertPublisher alerts;

    public DetectRefreshTokenReuseUseCase(
            CurrentRefreshTokens currentTokens,
            RotatedRefreshTokenRepository rotatedTokens,
            LoginRevoker loginRevoker,
            CustomerIdLookup customerIds,
            SecurityAlertPublisher alerts) {
        this.currentTokens = currentTokens;
        this.rotatedTokens = rotatedTokens;
        this.loginRevoker = loginRevoker;
        this.customerIds = customerIds;
        this.alerts = alerts;
    }

    public RefreshTokenVerdict execute(String refreshToken) {
        if (currentTokens.isCurrent(refreshToken)) {
            return RefreshTokenVerdict.ACCEPTED;
        }
        return rotatedTokens.authorizationIdOf(refreshToken)
                .map(authorizationId -> {
                    loginRevoker.revoke(authorizationId)
                            .flatMap(customerIds::customerIdOf)
                            .ifPresent(alerts::refreshTokenReuse);
                    return RefreshTokenVerdict.REUSED;
                })
                .orElse(RefreshTokenVerdict.UNKNOWN);
    }
}
