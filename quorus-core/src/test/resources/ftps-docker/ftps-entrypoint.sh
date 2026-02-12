#!/bin/sh
# Entrypoint for FTPS test container.
# Generates a self-signed TLS certificate then delegates to the base image's
# start_vsftpd.sh (which handles user creation, passive ports, etc.).
#
# Environment variables (optional):
#   TLS_CN  - Common Name for the certificate (default: localhost)
#   TLS_ORG - Organization (default: Quorus Test)
#   TLS_C   - Country code (default: GB)

set -e

CERT_DIR="/etc/ssl/certs"
KEY_DIR="/etc/ssl/private"

mkdir -p "$CERT_DIR" "$KEY_DIR"

echo "Generating self-signed TLS certificate (CN=${TLS_CN:-localhost})..."
openssl req -x509 -newkey rsa:2048 \
    -keyout "$KEY_DIR/vsftpd.key" \
    -out "$CERT_DIR/vsftpd.crt" \
    -days 365 -nodes \
    -subj "/CN=${TLS_CN:-localhost}/O=${TLS_ORG:-Quorus Test}/C=${TLS_C:-GB}" \
    2>/dev/null

chmod 600 "$KEY_DIR/vsftpd.key"
echo "TLS certificate generated."

# Do NOT export TLS_CERT / TLS_KEY — our vsftpd.conf already references
# the cert paths directly. Exporting them would trigger the base image's
# hardcoded TLS_OPT with force_local_*_ssl=YES, overriding our config.

exec /bin/start_vsftpd.sh
