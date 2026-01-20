# Phase 0: Prerequisites & Setup - Quick Start

## What We Just Did

✅ **Added Testcontainers dependencies** to `quorus-core/pom.xml`:
- Testcontainers core + JUnit 5 integration
- Awaitility for async test assertions
- AssertJ for fluent assertions
- Apache Commons Net (FTP test utilities)
- jCIFS-ng (SMB test utilities)

✅ **Created protocol server Docker Compose stack** at `docker/compose/docker-compose-protocol-servers.yml`:
- FTP server (stilliard/pure-ftpd) with passive mode ports 30000-30009
- SFTP server (atmoz/sftp) on port 2222
- SMB server (dperson/samba) on port 445
- All servers configured with credentials: `testuser` / `testpass`

✅ **Created testing documentation** at `docker/compose/PROTOCOL_SERVERS_TESTING.md`

## Next Actions (Manual Validation Required)

### 1. Update Maven Dependencies
```powershell
cd C:\Users\mraysmit\dev\idea-projects\quorus\quorus-core
mvn dependency:resolve
```

**Expected:** All dependencies download successfully.

### 2. Start Protocol Servers
```powershell
cd C:\Users\mraysmit\dev\idea-projects\quorus\docker\compose
docker-compose -f docker-compose-protocol-servers.yml up -d
```

**Expected:** All 3 containers start and show healthy status.

### 3. Verify Server Health
```powershell
docker-compose -f docker-compose-protocol-servers.yml ps
```

**Expected output:**
```
NAME                  IMAGE                     STATUS
quorus-ftp-test       stilliard/pure-ftpd      Up (healthy)
quorus-sftp-test      atmoz/sftp               Up (healthy)
quorus-smb-test       dperson/samba            Up (healthy)
```

### 4. Test FTP Connection
**Using FileZilla or PowerShell:**
```powershell
# Test with curl (if installed)
curl ftp://testuser:testpass@localhost:21/ --list-only

# Or use native FTP client
ftp localhost
# Login: testuser
# Password: testpass
```

**Expected:** Successfully connect and see empty directory listing.

### 5. Test SFTP Connection
```powershell
# If OpenSSH client installed
sftp -P 2222 testuser@localhost
# Password: testpass

# Or use curl
curl sftp://testuser:testpass@localhost:2222/upload/ --list-only
```

**Expected:** Successfully connect and see `/upload` directory.

### 6. Test SMB Connection
**Windows Explorer:**
- Navigate to: `\\localhost\testshare`
- Enter credentials when prompted: `testuser` / `testpass`

**Or PowerShell:**
```powershell
net use Z: \\localhost\testshare /user:testuser testpass
dir Z:\
net use Z: /delete
```

**Expected:** Successfully mount share and see empty directory.

## Phase 0 Completion Checklist

- [ ] Maven dependencies resolved without errors
- [ ] All 3 Docker containers running and healthy
- [ ] FTP manual connection successful
- [ ] SFTP manual connection successful
- [ ] SMB manual connection successful
- [ ] Can upload a test file to each server manually
- [ ] Can download the test file from each server manually

## Common Issues

### Docker Desktop Not Running
**Error:** `Cannot connect to the Docker daemon`

**Fix:** Start Docker Desktop on Windows.

### Port Already in Use
**Error:** `Bind for 0.0.0.0:21 failed: port is already allocated`

**Fix:** 
```powershell
# Find what's using the port
netstat -ano | findstr :21

# Stop conflicting service or change port in compose file
```

### FTP Passive Mode Issues
**Symptom:** FTP connects but hangs on directory listing.

**Fix:** Verify passive ports 30000-30009 are exposed and not blocked by firewall.

### SMB Not Working on Windows
**Error:** Network path not found.

**Fix:** Enable SMB client in Windows Features:
- Control Panel → Programs → Turn Windows features on/off
- Check "SMB 1.0/CIFS File Sharing Support"

## Phase 1 Preview

Once Phase 0 validation is complete, we'll create:

1. **Integration test base class** with Testcontainers lifecycle management
2. **Test file upload utilities** for programmatic file operations
3. **First integration test** (SFTP) to validate the full stack

Estimated time for Phase 0 validation: **30-60 minutes**

## Need Help?

If manual testing fails, check the detailed troubleshooting guide in:
`docker/compose/PROTOCOL_SERVERS_TESTING.md`

Or view container logs:
```powershell
docker logs quorus-ftp-test
docker logs quorus-sftp-test
docker logs quorus-smb-test
```
