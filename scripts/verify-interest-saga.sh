#!/usr/bin/env bash
# Verifies the interest-credit saga against the running local stack, end to end:
#   interests-service -> interests.calculated -> core-service -> interests.credit-results
# and the failure path: a record that cannot be processed ends on the dead-letter topic without
# blocking the partition. Needs: curl, jq, docker, and the stack up (docker compose up -d).
#
# Usage: scripts/verify-interest-saga.sh [--help]
# Environment (all optional):
#   ACCOUNT_ID                      account to credit            (default: the seeded demo account)
#   INTERESTS_SERVICE_CLIENT_SECRET client secret of interests-service (default: dev secret)
#   KAFKA_CONTAINER                 broker container name        (default: xyz-bank-kafka)
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
fi

cd "$(dirname "$0")/.."

ACCOUNT_ID="${ACCOUNT_ID:-22222222-2222-2222-2222-222222222222}"
CLIENT_SECRET="${INTERESTS_SERVICE_CLIENT_SECRET:-interests-service-dev-secret}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-xyz-bank-kafka}"
CA_CERT="dev/certs/ca.crt"
INTERESTS_URL="http://localhost:8084"
CORE_URL="http://localhost:8080"
TOKEN_URL="https://localhost:9000/oauth2/token"
TOPICS=(interests.calculated interests.credit-results transactions.confirmed)
KAFKA_BIN=/opt/kafka/bin

step() { printf '\n== %s\n' "$1"; }
ok() { printf '   ok: %s\n' "$1"; }
fail() { printf '   FAILED: %s\n' "$1" >&2; exit 1; }

kafka() { docker exec "$KAFKA_CONTAINER" "$KAFKA_BIN/$1" --bootstrap-server localhost:9092 "${@:2}"; }

for tool in curl jq docker; do
  command -v "$tool" >/dev/null || fail "$tool is required"
done
[[ -f "$CA_CERT" ]] || fail "$CA_CERT not found; run ./scripts/generate-dev-tls-certs.sh"
docker inspect "$KAFKA_CONTAINER" >/dev/null 2>&1 || fail "container $KAFKA_CONTAINER is not running; start the stack first"

access_token() {
  curl -sS --fail --cacert "$CA_CERT" -u "interests-service:$CLIENT_SECRET" \
    -d 'grant_type=client_credentials' "$TOKEN_URL" | jq -r .access_token
}

balance() {
  curl -sS --fail -H "Authorization: Bearer $TOKEN" "$CORE_URL/internal/accounts/$ACCOUNT_ID/balance" | jq -r .balance
}

# interest_applied <year>: prints the interest amount the application calculated
apply_interest() {
  curl -sS --fail -X POST -H "Authorization: Bearer $TOKEN" \
    "$INTERESTS_URL/accounts/$ACCOUNT_ID/interest-applications?year=$1" | jq -r .interestAmount
}

# wait_for_balance <expected> <seconds>
wait_for_balance() {
  local expected="$1" deadline=$((SECONDS + $2)) current
  while (( SECONDS < deadline )); do
    current="$(balance)"
    if jq -ne --argjson a "$current" --argjson b "$expected" '(($a - $b) | fabs) < 0.005' >/dev/null; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# wait_for_record <topic> <pattern> <seconds>: waits until a record whose key/value matches appears
wait_for_record() {
  local topic="$1" pattern="$2" deadline=$((SECONDS + $3)) records
  while (( SECONDS < deadline )); do
    # Read into a variable first: with pipefail, grep -q closing the pipe early would fail the pipeline
    records="$(kafka kafka-console-consumer.sh --topic "$topic" --from-beginning --timeout-ms 5000 \
      --property print.key=true --property print.headers=true 2>/dev/null || true)"
    if grep -q -- "$pattern" <<<"$records"; then
      return 0
    fi
  done
  return 1
}

step "Topics and dead-letter topics have 3 partitions"
for topic in "${TOPICS[@]}"; do
  for name in "$topic" "$topic.DLT"; do
    count="$(kafka kafka-topics.sh --describe --topic "$name" | grep -o 'PartitionCount: *[0-9]*' | grep -o '[0-9]*$' || true)"
    [[ "$count" == "3" ]] || fail "$name has ${count:-no} partitions, expected 3"
  done
done
ok "6 topics, 3 partitions each"

step "Token for interests-service"
TOKEN="$(access_token)"
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] || fail "no access token"
ok "client_credentials token obtained"

# A year no earlier run used (the credit is once per account and year)
YEAR=$((4000 + $(date +%s) % 5000))
SECOND_YEAR=$((YEAR + 1))

step "Interest is calculated, credited and reported (year $YEAR)"
before="$(balance)"
amount="$(apply_interest "$YEAR")"
expected="$(jq -n --argjson b "$before" --argjson a "$amount" '$b + $a')"
wait_for_balance "$expected" 60 || fail "balance did not rise from $before by $amount"
ok "balance $before -> $expected"
wait_for_record interests.credit-results "interest:$ACCOUNT_ID:$YEAR" 30 \
  || fail "no credit result on interests.credit-results: the saga path is not active (is FEATURE_INTEREST_CREDIT_VIA_KAFKA true?)"
ok "credit result published for interest:$ACCOUNT_ID:$YEAR"

step "Applying the same interest again credits nothing more"
apply_interest "$YEAR" >/dev/null
sleep 8
wait_for_balance "$expected" 5 || fail "balance moved on a repeated application"
ok "balance still $expected"

step "A record that cannot be processed is dead-lettered and does not block the partition"
echo "$ACCOUNT_ID:this is not an InterestCalculated" | docker exec -i "$KAFKA_CONTAINER" "$KAFKA_BIN/kafka-console-producer.sh" \
  --bootstrap-server localhost:9092 --topic interests.calculated \
  --property parse.key=true --property key.separator=: >/dev/null
wait_for_record interests.calculated.DLT "this is not an InterestCalculated" 90 \
  || fail "the invalid record never reached interests.calculated.DLT"
ok "invalid record on interests.calculated.DLT"
second_amount="$(apply_interest "$SECOND_YEAR")"
second_expected="$(jq -n --argjson b "$expected" --argjson a "$second_amount" '$b + $a')"
wait_for_balance "$second_expected" 60 || fail "the account was not credited after the invalid record: partition blocked"
ok "next event credited: balance $expected -> $second_expected"

printf '\nThe interest saga works end to end on this stack.\n'
