#!/usr/bin/env bash
# Issues auth-server's development key material from the EXISTING dev CA:
# - a TLS server certificate valid for both the browser-facing host name (localhost)
#   and the Docker-network host name (auth-server), and
# - a separate RSA keystore used only to sign OAuth2/OIDC tokens (RS256).
#
# Unlike generate-dev-tls-certs.sh, this never regenerates the CA, so browsers that
# already trust dev/certs/ca.crt and every other committed keystore stay valid.
#
# Dev/test use only. Never use this CA or these keys in a real deployment.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERTS_DIR="${ROOT_DIR}/dev/certs"
AUTH_DIR="${CERTS_DIR}/auth-server"
PASS="xyzbank-dev"
VALIDITY_DAYS=3650
TLS_ALIAS="auth-server"
SIGNING_ALIAS="auth-server-signing"

if [ ! -f "${CERTS_DIR}/ca.p12" ] || [ ! -f "${CERTS_DIR}/ca.crt" ] || [ ! -f "${CERTS_DIR}/truststore.p12" ]; then
  echo "Dev CA not found under ${CERTS_DIR}. Run ./scripts/generate-dev-tls-certs.sh first." >&2
  exit 1
fi

rm -rf "${AUTH_DIR}"
mkdir -p "${AUTH_DIR}"

echo "== Generating auth-server TLS keypair =="
keytool -genkeypair \
  -alias "${TLS_ALIAS}" \
  -keyalg RSA -keysize 2048 -validity "${VALIDITY_DAYS}" \
  -keystore "${AUTH_DIR}/keystore.p12" -storetype PKCS12 -storepass "${PASS}" -keypass "${PASS}" \
  -dname "CN=auth-server,OU=dev,O=xyzbank" \
  -ext ku:c=digitalSignature,keyEncipherment -ext eku:c=serverAuth \
  -ext "san=dns:auth-server,dns:localhost,ip:127.0.0.1"

keytool -certreq \
  -alias "${TLS_ALIAS}" -keystore "${AUTH_DIR}/keystore.p12" -storepass "${PASS}" \
  -file "${AUTH_DIR}/auth-server.csr" \
  -ext "san=dns:auth-server,dns:localhost,ip:127.0.0.1"

echo "== Signing auth-server's TLS certificate with the dev CA =="
keytool -gencert \
  -alias ca -keystore "${CERTS_DIR}/ca.p12" -storepass "${PASS}" \
  -infile "${AUTH_DIR}/auth-server.csr" -outfile "${AUTH_DIR}/auth-server.crt" \
  -validity "${VALIDITY_DAYS}" -rfc \
  -ext ku:c=digitalSignature,keyEncipherment -ext eku:c=serverAuth \
  -ext "san=dns:auth-server,dns:localhost,ip:127.0.0.1"

keytool -importcert -noprompt \
  -alias ca -file "${CERTS_DIR}/ca.crt" \
  -keystore "${AUTH_DIR}/keystore.p12" -storepass "${PASS}"
keytool -importcert -noprompt \
  -alias "${TLS_ALIAS}" -file "${AUTH_DIR}/auth-server.crt" \
  -keystore "${AUTH_DIR}/keystore.p12" -storepass "${PASS}"
rm -f "${AUTH_DIR}/auth-server.csr"

echo "== Generating auth-server token signing keystore (RSA, separate from TLS) =="
keytool -genkeypair \
  -alias "${SIGNING_ALIAS}" \
  -keyalg RSA -keysize 2048 -validity "${VALIDITY_DAYS}" \
  -keystore "${AUTH_DIR}/signing.p12" -storetype PKCS12 -storepass "${PASS}" -keypass "${PASS}" \
  -dname "CN=auth-server token signing,OU=dev,O=xyzbank"

echo "== Verifying auth-server's TLS certificate chains to the dev CA =="
keytool -list -v -keystore "${AUTH_DIR}/keystore.p12" -storepass "${PASS}" -alias "${TLS_ALIAS}" \
  | grep -E "Owner:|Issuer:|Alias name:"

echo "== Copying auth-server key material into its test resources =="
TEST_RESOURCES="${ROOT_DIR}/platform/auth-server/src/test/resources"
mkdir -p "${TEST_RESOURCES}/tls" "${TEST_RESOURCES}/signing"
cp "${AUTH_DIR}/keystore.p12" "${TEST_RESOURCES}/tls/keystore.p12"
cp "${CERTS_DIR}/truststore.p12" "${TEST_RESOURCES}/tls/truststore.p12"
cp "${AUTH_DIR}/signing.p12" "${TEST_RESOURCES}/signing/signing.p12"

# Test-only fixture: an EC (non-RSA) entry, to prove the server refuses to sign with it
rm -f "${TEST_RESOURCES}/signing/ec-signing.p12"
keytool -genkeypair \
  -alias "${SIGNING_ALIAS}" \
  -keyalg EC -groupname secp256r1 -validity "${VALIDITY_DAYS}" \
  -keystore "${TEST_RESOURCES}/signing/ec-signing.p12" -storetype PKCS12 -storepass "${PASS}" -keypass "${PASS}" \
  -dname "CN=auth-server EC test fixture,OU=dev,O=xyzbank"

echo "== Done. auth-server key material is under ${AUTH_DIR} (dev/test use only). =="
