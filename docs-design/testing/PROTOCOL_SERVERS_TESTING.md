# Protocol Server Testing Guide

This document consolidates all protocol server testing phases, including setup, validation status, and troubleshooting guides.

---

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

---

## Phase 0: Prerequisites & Setup

### What Was Configured

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

### Setup Commands

#### 1. Update Maven Dependencies
```powershell
cd C:\Users\mraysmit\dev\idea-projects\quorus\quorus-core
mvn dependency:resolve
```

**Expected:** All dependencies download successfully.

#### 2. Start Protocol Servers
```powershell
cd C:\Users\mraysmit\dev\idea-projects\quorus\docker\compose
docker-compose -f docker-compose-protocol-servers.yml up -d
```

**Expected:** All 3 containers start and show healthy status.

#### 3. Verify Server Health
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

### Phase 0 Completion Checklist

- [x] Maven dependencies resolved without errors
- [x] All 3 Docker containers running and healthy
- [x] FTP manual connection successful
- [x] SFTP manual connection successful
- [x] SMB manual connection successful
- [x] Can upload a test file to each server manually
- [x] Can download the test file from each server manually

---

## Phase 1: Connectivity Validation ✅ COMPLETE

**Completion Date:** 2026-01-20  
**Duration:** 10.15 seconds  
**Result:** 5/5 tests passed ✅

### Test Results Summary

```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

### Individual Test Results

1. **✅ Phase 1.1: Verify FTP server is reachable**
   - Status: PASSED
   - Result: FTP host validation successful

2. **✅ Phase 1.2: Connect to FTP using ftp://testuser:testpass@ftp:21**
   - Status: PASSED
   - Connection: localhost:21
   - Authentication: Successful (testuser/testpass)
   - Directory listing: 0 files
   - Container URL: `ftp://testuser:testpass@ftp:21/path`

3. **✅ Phase 1.3: Connect to SFTP using sftp://testuser:testpass@sftp:22**
   - Status: PASSED
   - Connection: localhost:2222
   - Authentication: Successful (testuser/testpass)
   - Directory listing: 2 entries (. and ..)
   - Container URL: `sftp://testuser:testpass@sftp:22/upload/path`

4. **✅ Phase 1.4: Connect to SMB using smb://testuser:testpass@smb:445**
   - Status: PASSED
   - Connection: localhost:4445
   - Authentication: Successful (testuser/testpass)
   - Share access: testshare
   - Directory listing: 0 entries
   - Container URL: `smb://testuser:testpass@smb:445/testshare/path`

5. **✅ Phase 1.5: Verify all protocol servers operational**
   - Status: PASSED
   - All infrastructure validated

### Technical Achievements

#### ✅ Connection Libraries Validated
- **Apache Commons Net** (FTP): FTPClient successfully connected
- **JSch** (SFTP): Session and ChannelSftp working
- **jCIFS-ng** (SMB): SmbFile with NtlmPasswordAuthenticator functional

#### ✅ Protocol-Specific Validations
- **FTP**: Passive mode configured (required for Docker)
- **SFTP**: Host key verification disabled (test environment)
- **SMB**: SMB 2.02-3.11 protocol negotiation successful

#### ✅ Infrastructure Capabilities
- Docker Compose stack stable
- Port mappings working (21, 2222, 4445)
- Authentication working for all protocols
- Directory/share enumeration functional

### Phase 1 Test Execution Details

**Test Class:** `ProtocolServersLifecycleIT.java`  
**Location:** `quorus-core/src/test/java/dev/mars/quorus/protocol/integration/`  
**Execution:** Manual docker-compose management  
**Test Order:** Sequential (1-5)  

**Command to run:**
```bash
cd quorus-core
mvn test -Dtest=ProtocolServersLifecycleIT
```

**Prerequisites:**
```bash
cd docker/compose
docker-compose -f docker-compose-protocol-servers.yml up -d
```

**Cleanup:**
```bash
cd docker/compose
docker-compose -f docker-compose-protocol-servers.yml down -v
```

---

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

---

## Troubleshooting

### Common Issues

#### Docker Desktop Not Running
**Error:** `Cannot connect to the Docker daemon`

**Fix:** Start Docker Desktop on Windows.

#### Port Already in Use
**Error:** `Bind for 0.0.0.0:21 failed: port is already allocated`

**Fix:** 
```powershell
# Find what's using the port
netstat -ano | findstr :21

# Stop conflicting service or change port in compose file
```

### FTP Issues

#### FTP: Connection Timeout
**Issue:** Client can connect but hangs during directory listing or file transfer.

**Cause:** Passive mode port range not accessible.

**Solution:**
- Verify ports 30000-30009 are not blocked by firewall
- Check Docker port mappings: `docker port quorus-ftp-test`
- Try Active mode instead (less common)

#### FTP: "425 Can't open data connection"
**Issue:** FTP control connection works but data transfer fails.

**Cause:** `PUBLICHOST` environment variable incorrect.

**Solution:**
- For host access: Set `PUBLICHOST: "localhost"` or your IP
- For container access: Set `PUBLICHOST: "ftp"` (service name)
- Restart container after changing

### SFTP Issues

#### SFTP: "Permission Denied"
**Issue:** Can connect but cannot upload/download files.

**Cause:** User doesn't have write permissions on `/upload` directory.

**Solution:**
- Check container logs: `docker logs quorus-sftp-test`
- Verify directory ownership inside container:
  ```powershell
  docker exec quorus-sftp-test ls -la /home/testuser/
  ```

### SMB Issues

#### SMB: "Network Path Not Found"
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

#### SMB: "Access Denied"
**Issue:** Connection succeeds but authentication fails.

**Cause:** Incorrect credentials or domain settings.

**Solution:**
- Ensure using correct format: `testuser` (not `WORKGROUP\testuser`)
- Check Samba logs for authentication errors
- Try from command line first before GUI tools

### Container Issues

#### Container Won't Start
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

---

## Connection Strings for Testing

### Validated Connection URLs

From **host machine** (for manual testing):
- FTP: `ftp://testuser:testpass@localhost:21/path`
- SFTP: `sftp://testuser:testpass@localhost:2222/upload/path`
- SMB: `smb://testuser:testpass@localhost:4445/testshare/path`

From **container network** (for integration tests):
- FTP: `ftp://testuser:testpass@ftp:21/path`
- SFTP: `sftp://testuser:testpass@sftp:22/upload/path`
- SMB: `smb://testuser:testpass@smb:445/testshare/path`

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

---

## Next Steps: Phase 2

**Goal:** Test file upload utilities for each protocol

**Deliverables:**
1. `FtpTestUploader.java` - Upload test files via FTP
2. `SftpTestUploader.java` - Upload test files via SFTP
3. `SmbTestUploader.java` - Upload test files via SMB
4. `ProtocolTestUploaderIT.java` - Integration tests for all uploaders

**Use Cases:**
- Create test data files in containers
- Validate protocol transfer tests can read files
- Prove bidirectional communication (read + write)

**Estimated Effort:** 2-3 days

---

## Overall Progress

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 0 | Prerequisites & Setup | ✅ Complete |
| Phase 1 | Connectivity Validation | ✅ Complete |
| Phase 2 | Test File Upload Utilities | 🔜 Next |
| Phase 3 | First SFTP Integration Test | ⏳ Pending |
| Phase 4 | Extend to FTP and SMB | ⏳ Pending |

**Overall Progress:** 15% (Phase 1 of 7 complete)
