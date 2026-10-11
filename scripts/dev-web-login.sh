#!/usr/bin/env bash
# Runs bff-web's OAuth 2.0 login (authorization code + PKCE via auth-server) with curl, the way
# a browser would, and prints the cookies bff-web sets as JSON:
#   {"session": "...", "refreshToken": "...", "xsrfToken": "..."}
# "session" is the auth-server access token: send it as the bff-web "session" cookie, or as a
# Bearer token to the platform services behind api-gateway.
#
# Usage: ./scripts/dev-web-login.sh
# Env:   AUTH_DEMO_USERNAME (default demo), AUTH_DEMO_PASSWORD (default demo-password)
#
# Requires the docker compose stack to be up. Dev use only.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
USERNAME="${AUTH_DEMO_USERNAME:-demo}"
PASSWORD="${AUTH_DEMO_PASSWORD:-demo-password}"
BFF_WEB="${BFF_WEB_PUBLIC_URL:-https://localhost:8081}"
AUTH_SERVER="${AUTH_PUBLIC_ISSUER:-https://localhost:9000}"

JAR="$(mktemp)"
trap 'rm -f "${JAR}"' EXIT
CURL=(curl -sS --cacert "${ROOT_DIR}/dev/certs/ca.crt" -b "${JAR}" -c "${JAR}" -H "Accept: text/html")

redirect_of() {
  "${CURL[@]}" -o /dev/null -w '%{redirect_url}' "$@"
}

fail() {
  echo "dev-web-login: $*" >&2
  exit 1
}

cookie() {
  awk -v name="$1" -F '\t' '$6 == name { value = $7 } END { print value }' "${JAR}"
}

# 1. bff-web starts the login and sends us to auth-server
authorize_url="$(redirect_of "${BFF_WEB}/oauth2/authorization/oidc")"
[[ "${authorize_url}" == "${AUTH_SERVER}/oauth2/authorize"* ]] || fail "bff-web did not redirect to auth-server (got '${authorize_url}')"

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

# 4. Back at the authorization endpoint, auth-server issues the code to bff-web's callback
callback_url="$(redirect_of "${after_login}")"
[[ "${callback_url}" == "${BFF_WEB}/login/oauth2/code/oidc?code="* ]] || fail "no authorization code (got '${callback_url}')"

# 5. bff-web exchanges the code (PKCE) and answers with its session cookies
"${CURL[@]}" -o /dev/null "${callback_url}"
session="$(cookie session)"
[[ -n "${session}" ]] || fail "bff-web did not set the session cookie"

jq -n --arg session "${session}" --arg refresh "$(cookie refresh_token)" --arg xsrf "$(cookie XSRF-TOKEN)" \
  '{session: $session, refreshToken: $refresh, xsrfToken: $xsrf}'
