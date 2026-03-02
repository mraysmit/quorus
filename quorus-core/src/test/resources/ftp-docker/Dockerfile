FROM delfer/alpine-ftp-server

# Configure vsftpd for upload testing (chroot with write permissions)
RUN printf '\n\
# === Plain FTP configuration for Quorus integration testing ===\n\
# Allow writing into chroot directory (required for upload tests)\n\
allow_writeable_chroot=YES\n\
# Enable chroot so user is jailed to home dir (matches FTPS config)\n\
chroot_local_user=YES\n' >> /etc/vsftpd/vsftpd.conf
