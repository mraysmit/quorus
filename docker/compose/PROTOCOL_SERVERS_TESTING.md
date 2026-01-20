# Protocol Server Testing Guide

## Quick Start

Start all protocol servers:
```powershell
cd docker\compose
docker-compose -f docker-compose-protocol-servers.yml up -d
```

Check server health:
```powershell
docker-compose -f docker-compose-protocol-servers.yml ps
```

View logs:
```powershell
docker-compose -f docker-compose-protocol-servers.yml logs -f
```

Stop servers:
```powershell
docker-compose -f docker-compose-protocol-servers.yml down -v
```

## Manual Connection Testing

### FTP Server
**Credentials:**
- Host: `localhost`
- Port: `21`
- Username: `testuser`
- Password: `testpass`

**FileZilla Configuration:**
- Protocol: FTP
- Host: localhost
- Port: 21
- User: testuser
- Pass: testpass
- Transfer Mode: Passive

**PowerShell Test:**
```powershell
# Using Windows FTP client (limited)
ftp localhost

# Or use curl
curl ftp://testuser:testpass@localhost:21/ --list-only
```

### SFTP Server
**Credentials:**
- Host: `localhost`
- Port: `2222`
- Username: `testuser`
- Password: `testpass`
- Root Directory: `/upload`

**WinSCP Configuration:**
- Protocol: SFTP
- Host: localhost
- Port: 2222
- User: testuser
- Pass: testpass

**PowerShell Test:**
```powershell
# Using sftp command (if OpenSSH client installed)
sftp -P 2222 testuser@localhost

# Or use curl
curl sftp://testuser:testpass@localhost:2222/upload/ --list-only
```

### SMB Server
**Credentials:**
- Host: `localhost`
- Share: `testshare`
- Username: `testuser`
- Password: `testpass`

**Windows Explorer:**
1. Open File Explorer
2. Address bar: `\\localhost\testshare`
3. When prompted, enter:
   - Username: `testuser`
   - Password: `testpass`

**PowerShell Test:**
```powershell
# Map network drive
net use Z: \\localhost\testshare /user:testuser testpass

# List files
dir Z:\

# Disconnect
net use Z: /delete
```

## Troubleshooting

### FTP: Connection Timeout
**Issue:** Client can connect but hangs during directory listing or file transfer.

**Cause:** Passive mode port range not accessible.

**Solution:**
- Verify ports 30000-30009 are not blocked by firewall
- Check Docker port mappings: `docker port quorus-ftp-test`
- Try Active mode instead (less common)

### FTP: "425 Can't open data connection"
**Issue:** FTP control connection works but data transfer fails.

**Cause:** `PUBLICHOST` environment variable incorrect.

**Solution:**
- For host access: Set `PUBLICHOST: "localhost"` or your IP
- For container access: Set `PUBLICHOST: "ftp"` (service name)
- Restart container after changing

### SFTP: "Permission Denied"
**Issue:** Can connect but cannot upload/download files.

**Cause:** User doesn't have write permissions on `/upload` directory.

**Solution:**
- Check container logs: `docker logs quorus-sftp-test`
- Verify directory ownership inside container:
  ```powershell
  docker exec quorus-sftp-test ls -la /home/testuser/
  ```

### SMB: "Network Path Not Found"
**Issue:** Cannot connect to `\\localhost\testshare`.

**Cause:** SMB ports blocked or SMB client not enabled on Windows.

**Solution:**
- Verify SMB server is running:
  ```powershell
  docker logs quorus-smb-test
  ```
- Check Windows Features:
  - Control Panel → Programs → Turn Windows features on/off
  - Enable "SMB 1.0/CIFS File Sharing Support" (if needed)
- Try using IP address instead:
  ```powershell
  \\127.0.0.1\testshare
  ```

### SMB: "Access Denied"
**Issue:** Connection succeeds but authentication fails.

**Cause:** Incorrect credentials or domain settings.

**Solution:**
- Ensure using correct format: `testuser` (not `WORKGROUP\testuser`)
- Check Samba logs for authentication errors
- Try from command line first before GUI tools

### Container Won't Start
**Issue:** Docker container exits immediately.

**Cause:** Port conflict or missing dependencies.

**Solution:**
```powershell
# Check if port already in use
netstat -an | findstr ":21 "
netstat -an | findstr ":2222 "
netstat -an | findstr ":445 "

# Check container logs
docker logs quorus-ftp-test
docker logs quorus-sftp-test
docker logs quorus-smb-test

# Remove and recreate
docker-compose -f docker-compose-protocol-servers.yml down -v
docker-compose -f docker-compose-protocol-servers.yml up -d
```

## Connection Strings for Testing

### From Host (Java test code on your machine):
```java
// FTP
String ftpUrl = "ftp://testuser:testpass@localhost:21/testfile.txt";

// SFTP
String sftpUrl = "sftp://testuser:testpass@localhost:2222/upload/testfile.txt";

// SMB
String smbUrl = "smb://testuser:testpass@localhost:445/testshare/testfile.txt";
```

### From Container (Quorus agents running in Docker):
```java
// FTP - use service name 'ftp', not 'localhost'
String ftpUrl = "ftp://testuser:testpass@ftp:21/testfile.txt";

// SFTP - use service name 'sftp'
String sftpUrl = "sftp://testuser:testpass@sftp:22/upload/testfile.txt";

// SMB - use service name 'smb'
String smbUrl = "smb://testuser:testpass@smb:445/testshare/testfile.txt";
```

## Next Steps

Once you've verified manual connections work:

1. **Phase 1**: Create integration test base class with Testcontainers
2. **Phase 2**: Implement test file upload utilities
3. **Phase 3**: Write first SFTP integration test
4. **Phase 4**: Extend to FTP and SMB

See main implementation plan for detailed breakdown.
