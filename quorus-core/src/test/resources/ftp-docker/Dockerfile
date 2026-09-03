FROM delfer/alpine-ftp-server

# Configure vsftpd for upload testing (chroot with write permissions)
RUN printf '\n\
# === Plain FTP configuration for Quorus integration testing ===\n\
# Allow writing into chroot directory (required for upload tests)\n\
allow_writeable_chroot=YES\n\
# Enable chroot so user is jailed to home dir (matches FTPS config)\n\
chroot_local_user=YES\n\
# Keep vsftpd as PID 1. The upstream background/pidproxy path can bind to a\n\
# short-lived session process and exit after the first readiness probe.\n\
background=NO\n' >> /etc/vsftpd/vsftpd.conf
