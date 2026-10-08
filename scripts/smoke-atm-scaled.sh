#!/usr/bin/env bash
# Smoke test for a stack started with replicas:
#   docker compose up -d --scale core-service=2 --scale payments-service=2
# Asserts that Eureka lists two CORE-SERVICE instances, then runs the README's ATM flow
# (PIN verification, balance, withdrawal) through bff-atm. Dev use only; needs curl and jq.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

EUREKA_URL="${EUREKA_URL:-http://localhost:8761}"
BFF_ATM_URL="${BFF_ATM_URL:-https://localhost:8083}"
CA_CERT="dev/certs/ca.crt"
TERMINAL_CERT="dev/certs/atm-terminal/keystore.p12:xyzbank-dev"
CARD_ID="77777777-7777-7777-7777-777777777777"
ACCOUNT_ID="22222222-2222-2222-2222-222222222222"

fail() { printf 'FAILED: %s\n' "$1" >&2; exit 1; }
atm() { curl -sS --cacert "$CA_CERT" --cert-type P12 --cert "$TERMINAL_CERT" "$@"; }

echo "== Eureka lists two core-service instances"
instances=$(curl -sS -H 'Accept: application/json' "$EUREKA_URL/eureka/apps/CORE-SERVICE" \
  | jq '[.application.instance[] | select(.status == "UP")] | length')
[ "$instances" -eq 2 ] || fail "expected 2 UP CORE-SERVICE instances, found $instances"

echo "== ATM flow through bff-atm"
session=$(atm -X POST "$BFF_ATM_URL/pin-verifications" -H 'Content-Type: application/json' \
  -d "{\"cardNumber\":\"$CARD_ID\",\"pin\":\"1234\"}" | jq -r .sessionToken)
[ -n "$session" ] && [ "$session" != "null" ] || fail "PIN verification returned no session token"

status=$(atm -o /dev/null -w '%{http_code}' "$BFF_ATM_URL/accounts/$ACCOUNT_ID/balance" \
  -H "Authorization: Bearer $session")
[ "$status" = "200" ] || fail "balance returned $status"

status=$(atm -o /dev/null -w '%{http_code}' -X POST "$BFF_ATM_URL/accounts/$ACCOUNT_ID/withdrawals" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $session" \
  -H "Idempotency-Key: smoke-$(date +%s)" -d '{"amount":1.00,"currency":"USD"}')
case "$status" in 200|201) ;; *) fail "withdrawal returned $status" ;; esac

echo "OK: two core-service replicas serve the ATM flow"
