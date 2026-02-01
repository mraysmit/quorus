# Protocol Server Testing Guide

Real protocol servers (FTP, SFTP, SMB) running in Docker containers for integration testing of Quorus file transfer protocols.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Architecture Overview](#architecture-overview)
3. [Server Configuration](#server-configuration)
4. [Connection URLs](#connection-urls)
5. [Running Integration Tests](#running-integration-tests)
6. [Manual Testing](#manual-connection-testing)
7. [Troubleshooting](#troubleshooting)
8. [Test Progress](#test-progress)

---

## Quick Start

### 1. Start Protocol Servers (30 seconds)

```powershell
cd docker\compose
docker-compose -f docker-compose-protocol-servers.yml up -d
```

### 2. Verify Servers Running

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

### 3. Run Connectivity Tests

```powershell
cd quorus-core
mvn test -Dtest=ProtocolServersLifecycleIT
```

### 4. Stop Servers (when done)

```powershell
cd docker\compose
docker-compose -f docker-compose-protocol-servers.yml down -v
```

### Quick Reference

| Protocol | Host Port | Credentials | Test Directory |
|----------|-----------|-------------|----------------|
| FTP      | 21        | testuser/testpass | `/` |
| SFTP     | 2222      | testuser/testpass | `/upload` |
| SMB      | 4445      | testuser/testpass | `testshare` |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          HOST MACHINE                                │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │     JUnit Integration Tests (ProtocolServersLifecycleIT)      │   │
│  │        Uses: Apache Commons Net, JSch, jCIFS-ng              │   │
│  └─────────────────────┬────────────────────────────────────────┘   │
│                        │ (connects via localhost + mapped ports)     │
├────────────────────────┼────────────────────────────────────────────┤
│                        ▼                                             │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │               DOCKER NETWORK (protocol-test)                 │    │
│  │   ┌─────────────┐  ┌────────────┐  ┌──────────────────┐     │    │
│  │   │   FTP       │  │   SFTP     │  │       SMB        │     │    │
│  │   │ pure-ftpd   │  │ atmoz/sftp │  │  dperson/samba   │     │    │
│  │   │ Port 21     │  │ Port 22    │  │  Port 445        │     │    │
│  │   │ PASV 30000+ │  │            │  │                  │     │    │
│  │   └─────────────┘  └────────────┘  └──────────────────┘     │    │
│  │        │                 │                   │               │    │
│  │   [ftp-data]        [sftp-data]         [smb-data]          │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                       │
│  Port Mappings (Host → Container):                                   │
│    21    → 21       (FTP control)                                    │
│    2222  → 22       (SFTP - avoids SSH conflict)                     │
│    4445  → 445      (SMB - avoids Windows SMB conflict)              │
│    30000-30009      (FTP passive data ports)                         │
└─────────────────────────────────────────────────────────────────────┘
```

### Why Real Servers?

| Benefit | Description |
|---------|-------------|
| **Realistic Testing** | Actual FTP/SFTP/SMB protocol implementations |
| **No Mocks** | Catches real protocol edge cases and timing issues |
| **Dual Access** | Host tests (Maven) + container tests (Docker agents) |
| **Persistent Data** | Named volumes survive container restarts |

---

## Server Configuration

### Docker Compose File

**Location:** `docker/compose/docker-compose-protocol-servers.yml`

### FTP Server (Pure-FTPd)

```yaml
ftp:
  image: stilliard/pure-ftpd:latest
  container_name: quorus-ftp-test
  ports:
    - "21:21"
    - "30000-30009:30000-30009"  # Passive mode
  environment:
    PUBLICHOST: "localhost"
    FTP_USER_NAME: testuser
    FTP_USER_PASS: testpass
```

**Key configuration:** Passive mode is required for Docker networking.

### SFTP Server (OpenSSH)

```yaml
sftp:
  image: atmoz/sftp:alpine
  container_name: quorus-sftp-test
  ports:
    - "2222:22"
  command: testuser:testpass:1001:100:upload
```

**Key configuration:** User home is `/home/testuser/upload`.

### SMB Server (Samba)

```yaml
smb:
  image: dperson/samba:latest
  container_name: quorus-smb-test
  ports:
    - "4445:445"  # Non-standard port to avoid Windows conflict
  environment:
    USER: "testuser;testpass"
    SHARE: "testshare;/share;yes;no;no;testuser;testuser;testuser"
```

**Key configuration:** Port 4445 avoids conflict with Windows built-in SMB.

---

## Connection URLs

### From Host Machine (Maven tests, manual testing)

```
ftp://testuser:testpass@localhost:21/path/to/file.txt
sftp://testuser:testpass@localhost:2222/upload/path/to/file.txt
smb://testuser:testpass@localhost:4445/testshare/path/to/file.txt
```

### From Docker Containers (Quorus agents)

```
ftp://testuser:testpass@ftp:21/path/to/file.txt
sftp://testuser:testpass@sftp:22/upload/path/to/file.txt
smb://testuser:testpass@smb:445/testshare/path/to/file.txt
```

> **Note:** Inside Docker, use service names (`ftp`, `sftp`, `smb`) and internal ports.

### Java Test Code Examples

```java
// FTP (Apache Commons Net)
FTPClient ftp = new FTPClient();
ftp.connect("localhost", 21);
ftp.login("testuser", "testpass");
ftp.enterLocalPassiveMode();  // Required for Docker

// SFTP (JSch)
JSch jsch = new JSch();
Session session = jsch.getSession("testuser", "localhost", 2222);
session.setPassword("testpass");
session.setConfig("StrictHostKeyChecking", "no");
session.connect();

// SMB (jCIFS-ng)
String smbUrl = "smb://localhost:4445/testshare/";
CIFSContext ctx = new BaseContext(new PropertyConfiguration(props))
    .withCredentials(new NtlmPasswordAuthenticator(null, "testuser", "testpass"));
SmbFile share = new SmbFile(smbUrl, ctx);
```

---

## Running Integration Tests

### Prerequisites

1. Docker Desktop running
2. Maven dependencies resolved: `mvn dependency:resolve -pl quorus-core`
3. Protocol servers started (see Quick Start)

### Run Connectivity Validation

```powershell
cd quorus-core
mvn test -Dtest=ProtocolServersLifecycleIT
```

**Expected result:** 5 tests pass in ~10 seconds.

### Test Class Details

**File:** `quorus-core/src/test/java/dev/mars/quorus/protocol/integration/ProtocolServersLifecycleIT.java`

| Test | Description |
|------|-------------|
| `testFtpServerReachable` | Verify FTP host configured |
| `testFtpConnection` | Full FTP lifecycle: connect → auth → PASV → list |
| `testSftpConnection` | Full SFTP lifecycle: SSH session → SFTP channel → directory ops |
| `testSmbConnection` | Full SMB lifecycle: NTLM auth → share access → enumeration |
| `testAllServersOperational` | Summary validation |

### Client Libraries Used

| Protocol | Library | Maven Artifact |
|----------|---------|----------------|
| FTP | Apache Commons Net | `commons-net:commons-net` |
| SFTP | JSch | `com.jcraft:jsch` |
| SMB | jCIFS-ng | `eu.agno3.jcifs:jcifs-ng` |

---

## Manual Connection Testing

### FTP Server

**FileZilla:**
- Protocol: FTP
- Host: localhost
- Port: 21
- User: testuser
- Password: testpass
- Transfer Mode: Passive

**PowerShell:**
```powershell
curl ftp://testuser:testpass@localhost:21/ --list-only
```

### SFTP Server

**WinSCP:**
- Protocol: SFTP
- Host: localhost
- Port: 2222
- User: testuser
- Password: testpass

**PowerShell (requires OpenSSH):**
```powershell
sftp -P 2222 testuser@localhost
```

### SMB Server

**Windows Explorer:**
- Cannot use `\\localhost\testshare` (port 4445 not supported in UNC)
- Use programmatic access only

**PowerShell (mount as drive):**
```powershell
# Note: Windows net use doesn't support custom ports
# Use Java/jCIFS-ng for testing on port 4445
```

---

## Troubleshooting

### Common Issues

#### Docker Desktop Not Running

**Error:** `Cannot connect to the Docker daemon`

**Fix:** Start Docker Desktop.

#### Port Already in Use

**Error:** `Bind for 0.0.0.0:21 failed: port is already allocated`

**Fix:**
```powershell
netstat -ano | findstr :21
# Kill conflicting process or change port in compose file
```

### FTP Issues

#### Connection Timeout on Directory Listing

**Cause:** Passive mode ports blocked.

**Fix:** Ensure ports 30000-30009 are accessible. Check Docker port mappings:
```powershell
docker port quorus-ftp-test
```

#### "425 Can't open data connection"

**Cause:** `PUBLICHOST` environment variable incorrect.

**Fix:** For host access, ensure `PUBLICHOST: "localhost"` in compose file.

### SFTP Issues

#### Permission Denied

**Cause:** User doesn't have write permissions.

**Fix:** Check container logs:
```powershell
docker logs quorus-sftp-test
```

### SMB Issues

#### Network Path Not Found

**Cause:** Port 4445 not standard, Windows Explorer won't connect.

**Fix:** Use programmatic access with jCIFS-ng. Standard Windows UNC paths don't support custom ports.

#### Access Denied

**Cause:** Credential format incorrect.

**Fix:** Use username `testuser` (not `WORKGROUP\testuser`).

### Container Won't Start

**Fix:**
```powershell
# Check logs
docker logs quorus-ftp-test
docker logs quorus-sftp-test
docker logs quorus-smb-test

# Remove and recreate
docker-compose -f docker-compose-protocol-servers.yml down -v
docker-compose -f docker-compose-protocol-servers.yml up -d
```

### View Real-Time Logs

```powershell
docker-compose -f docker-compose-protocol-servers.yml logs -f
```

---

## Test Progress

### Phase Summary

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 0 | Prerequisites & Setup | ✅ Complete |
| Phase 1 | Connectivity Validation | ✅ Complete |
| Phase 2 | Test File Upload Utilities | 🔜 Next |
| Phase 3 | First SFTP Integration Test | ⏳ Pending |
| Phase 4 | Extend to FTP and SMB | ⏳ Pending |

### Phase 1 Results

**Completion Date:** 2026-01-20  
**Result:** 5/5 tests passed ✅

| Test | Result | Details |
|------|--------|---------|
| FTP Reachable | ✅ | Host validation successful |
| FTP Connection | ✅ | Connect, auth, PASV, list all passed |
| SFTP Connection | ✅ | SSH session, SFTP channel, directory ops passed |
| SMB Connection | ✅ | NTLM auth, share access, enumeration passed |
| All Operational | ✅ | Infrastructure validated |

### Validated Capabilities

- ✅ Apache Commons Net FTPClient working
- ✅ JSch Session and ChannelSftp working
- ✅ jCIFS-ng SmbFile with NtlmPasswordAuthenticator working
- ✅ FTP passive mode configured (required for Docker)
- ✅ SFTP host key verification disabled (test environment)
- ✅ SMB 2.02-3.11 protocol negotiation successful
- ✅ Docker Compose stack stable
- ✅ All port mappings working (21, 2222, 4445)
