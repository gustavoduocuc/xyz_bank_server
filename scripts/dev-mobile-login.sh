#!/usr/bin/env bash
# Runs bff-mobile's OAuth 2.0 login (authorization code + PKCE via auth-server) with curl,
# the way a native app's embedded browser would, and prints bff-mobile's session JSON:
#   {"sessionToken": "...", "refreshToken": "...", "refreshTokenExpiry": "..."}
#
# Usage: ./scripts/dev-mobile-login.sh [deviceId]     (default deviceId: demo-phone-1)
# Env:   AUTH_DEMO_USERNAME (default demo), AUTH_DEMO_PASSWORD (default demo-password)
#
# Requires the docker compose stack to be up. Dev use only.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_ID="${1:-demo-phone-1}"
USERNAME="${AUTH_DEMO_USERNAME:-demo}"
PASSWORD="${AUTH_DEMO_PASSWORD:-demo-password}"
BFF_MOBILE="${BFF_MOBILE_PUBLIC_URL:-https://localhost:8082}"
AUTH_SERVER="${AUTH_PUBLIC_ISSUER:-https://localhost:9000}"

JAR="$(mktemp)"
trap 'rm -f "${JAR}"' EXIT
CURL=(curl -sS --cacert "${ROOT_DIR}/dev/certs/ca.crt" -b "${JAR}" -c "${JAR}" -H "Accept: text/html")

redirect_of() {
  "${CURL[@]}" -o /dev/null -w '%{redirect_url}' "$@"
}

fail() {
  echo "dev-mobile-login: $*" >&2
  exit 1
}

# 1. bff-mobile starts the login (remembering the device) and sends us to auth-server
authorize_url="$(redirect_of "${BFF_MOBILE}/oauth2/authorization/oidc?deviceId=${DEVICE_ID}")"
[[ "${authorize_url}" == "${AUTH_SERVER}/oauth2/authorize"* ]] || fail "bff-mobile did not redirect to auth-server (got '${authorize_url}')"

# 2. auth-server asks us to log in
login_url="$(redirect_of "${authorize_url}")"
[[ "${login_url}" == "${AUTH_SERVER}/login" ]] || fail "auth-server did not ask for a login (got '${login_url}')"

# 3. Log in with the demo customer, echoing the login form's CSRF token
csrf="$("${CURL[@]}" "${login_url}" | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p' | head -1)"
[[ -n "${csrf}" ]] || fail "no CSRF token on the login page"
after_login="$(redirect_of -X POST "${login_url}" \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  --data-urlencode "_csrf=${csrf}")"
[[ "${after_login}" != *"/login?error"* ]] || fail "login rejected for user '${USERNAME}'"

# 4. Back at the authorization endpoint, auth-server issues the code to bff-mobile's callback
callback_url="$(redirect_of "${after_login}")"
[[ "${callback_url}" == "${BFF_MOBILE}/login/oauth2/code/oidc?code="* ]] || fail "no authorization code (got '${callback_url}')"

# 5. bff-mobile exchanges the code (PKCE) and answers with its own device-bound session
"${CURL[@]}" -H "Accept: application/json" "${callback_url}"
echo
