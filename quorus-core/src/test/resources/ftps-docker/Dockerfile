FROM delfer/alpine-ftp-server

# Add openssl for self-signed certificate generation at container startup
RUN apk add --no-cache openssl

# Append FTPS/TLS settings to vsftpd.conf.
# We do NOT set TLS_CERT/TLS_KEY env vars (which would trigger the base image's
# hardcoded force_local_data_ssl=YES / force_local_logins_ssl=YES).
# Instead we configure SSL directly in vsftpd.conf with TLS-optional mode
# (matching pure-ftpd --tls=1 behavior: clients can upgrade via AUTH TLS).
RUN printf '\n\
# === FTPS/TLS configuration for Quorus integration testing ===\n\
ssl_enable=YES\n\
rsa_cert_file=/etc/ssl/certs/vsftpd.crt\n\
rsa_private_key_file=/etc/ssl/private/vsftpd.key\n\
allow_anon_ssl=NO\n\
# TLS optional: clients CAN upgrade via AUTH TLS but are not forced to\n\
force_local_data_ssl=NO\n\
force_local_logins_ssl=NO\n\
# Disable TLS session reuse requirement — needed because control (port 21)\n\
# and passive data (port 30000) present as different endpoints to the client\n\
require_ssl_reuse=NO\n\
# Protocol versions: disable old/insecure, keep TLS 1.2+\n\
ssl_tlsv1=NO\n\
ssl_sslv2=NO\n\
ssl_sslv3=NO\n\
ssl_ciphers=HIGH\n\
# Allow writing into chroot directory (required for upload tests)\n\
allow_writeable_chroot=YES\n\
# Enable chroot so user is jailed to home dir (safe for testing)\n\
chroot_local_user=YES\n' >> /etc/vsftpd/vsftpd.conf

# Custom entrypoint: generate cert then delegate to original start script
COPY ftps-entrypoint.sh /bin/ftps-entrypoint.sh
RUN chmod +x /bin/ftps-entrypoint.sh

ENTRYPOINT ["/sbin/tini", "--", "/bin/ftps-entrypoint.sh"]
