package cl.duoc.xyzbank.authserver.sessions.application.usecases;

import cl.duoc.xyzbank.authserver.sessions.application.dto.RefreshTokenVerdict;
import cl.duoc.xyzbank.authserver.sessions.application.ports.CurrentRefreshTokens;
import cl.duoc.xyzbank.authserver.sessions.application.ports.LoginRevoker;
import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;

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

    public DetectRefreshTokenReuseUseCase(
            CurrentRefreshTokens currentTokens, RotatedRefreshTokenRepository rotatedTokens, LoginRevoker loginRevoker) {
        this.currentTokens = currentTokens;
        this.rotatedTokens = rotatedTokens;
        this.loginRevoker = loginRevoker;
    }

    public RefreshTokenVerdict execute(String refreshToken) {
        if (currentTokens.isCurrent(refreshToken)) {
            return RefreshTokenVerdict.ACCEPTED;
        }
        return rotatedTokens.authorizationIdOf(refreshToken)
                .map(authorizationId -> {
                    loginRevoker.revoke(authorizationId);
                    return RefreshTokenVerdict.REUSED;
                })
                .orElse(RefreshTokenVerdict.UNKNOWN);
    }
}
