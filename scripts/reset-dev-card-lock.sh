#!/usr/bin/env bash
# Resets the seeded demo card's failure count and lock state directly in the local
# PostgreSQL container, for local development after locking the demo card out by
# repeatedly failing its PIN through bff-atm.
#
# Dev/test use only: there is no customer-facing unlock path (see design.md).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POSTGRES_SERVICE="postgres"
POSTGRES_DB="core_service"
POSTGRES_USER="core_service"
DEMO_CARD_ID="77777777-7777-7777-7777-777777777777"

echo "== Resetting the demo card's failure count and lock state =="
docker compose -f "${ROOT_DIR}/docker-compose.yaml" exec -T "${POSTGRES_SERVICE}" \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 -c \
  "UPDATE cards SET consecutive_failures = 0, locked = FALSE, version = version + 1 WHERE id = '${DEMO_CARD_ID}';"

echo "Done. The demo card (PIN 1234) can be used again."
