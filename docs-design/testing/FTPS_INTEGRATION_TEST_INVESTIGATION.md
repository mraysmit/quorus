# FTPS Integration Test Investigation Notes

**Date:** 2026-02-12  
**Status:** In Progress — container starts but connections fail  
**Author:** Copilot session notes for Mark

---

## 1. What Was Done This Session

### Testcontainers Version Fix
- **Problem:** `quorus-core/pom.xml` hardcoded Testcontainers `1.19.3`, while parent POM defined `<testcontainers.version>2.0.3</testcontainers.version>`. TC 1.19.3 couldn't connect to Docker Desktop 29.x (HTTP 400 error).
- **Fix:** Changed `quorus-core/pom.xml` to use `${testcontainers.version}` (lines ~97-109).
- **TC 2.x breaking change:** Artifact `junit-jupiter` renamed to `testcontainers-junit-jupiter` (all TC 2.x modules are prefixed with `testcontainers-`). Fixed in quorus-core pom.xml. Note: `quorus-controller` already had the correct name.
- **Result:** Docker connectivity now works. All 77 unit tests pass.

### FtpTransferProtocol Null Safety
- **Problem:** `readResponse()` returned null when server closed connection (EOF), causing NPE on `response.startsWith(...)`.
- **Fix:** `readResponse()` now throws `IOException("FTP server closed connection unexpectedly (EOF)")` when `readLine()` returns null.
- **File:** `quorus-core/src/main/java/dev/mars/quorus/protocol/FtpTransferProtocol.java` (~line 884)

### Docker Compose for FTPS
- **File:** `quorus-core/src/test/resources/docker-compose-ftps-test.yml`
- Uses `stilliard/pure-ftpd:latest` with TLS mode 1 (optional TLS)
- Fixed port mappings: `2121:21` (control) and `30000:30000` (passive data)
- Auto-generates self-signed cert via `TLS_CN`, `TLS_ORG`, `TLS_C` env vars
- Credentials: `testuser` / `testpass`
- Healthcheck: `bash -c '</dev/tcp/localhost/21'` with 60s start_period

### SharedTestContainers FTPS Support
- **File:** `quorus-core/src/test/java/dev/mars/quorus/protocol/SharedTestContainers.java`
- Fixed ports: `FTPS_CONTROL_PORT = 2121`, `FTPS_DATA_PORT = 30000`
- No `withExposedService()` — bypasses Testcontainers socat proxy (see Section 3 for why)
- `getFtpsHost()` returns `"localhost"` directly
- `getFtpsPort()` returns `2121`
- `waitForFtpReady()` polls for FTP 220 welcome message

### FtpsUploadIntegrationTest Simplifications
- **File:** `quorus-core/src/test/java/dev/mars/quorus/protocol/FtpsUploadIntegrationTest.java`
- Connectivity test: Uses `syst()` command (control-only) instead of `listNames()` (needs data channel)
- `seedFileOnServer()`: Uses our `FtpTransferProtocol` instead of commons-net `FTPSClient` to avoid TLS session reuse issues

---

## 2. Current Problem: Container Connections Fail

### Symptom
The FTPS Docker container starts, reports healthy, and `docker logs` shows Pure-FTPd launched successfully:
```
Starting Pure-FTPd:
  pure-ftpd -l puredb:/etc/pure-ftpd/pureftpd.pdb -E -j -R -P 127.0.0.1 --tls=1 -p 30000:30000 -c 5 -C 5
```

But **all TCP connections from the Windows host get an empty banner then connection reset**, even after 90 seconds of clean wait (no polling interference).

### Evidence
1. PowerShell `TcpClient` to `localhost:2121` → connects, `ReadLine()` returns empty string, then "connection aborted by software in your host machine"
2. `waitForFtpReady()` during test runs: Gets empty responses for ~10 attempts, then sometimes gets a 220 banner on one attempt, but subsequent test connections all fail with EOF
3. Testing from **inside** the container via `docker exec ... bash -c '</dev/tcp/localhost/21'` also returned empty output
4. Docker healthcheck (which runs inside the container using the same TCP check) reports "healthy" — but this only checks TCP connectivity, not FTP protocol readiness

### What Was Ruled Out
- **Rate limiting / anti-abuse:** Connections fail even with a 90-second clean wait and no prior polling. Not a rate-limit issue.
- **DH parameter timing:** 90 seconds is more than enough. Logs show DH generation completes before "Starting Pure-FTPd" line.
- **Port mapping:** `docker ps` shows correct mapping: `0.0.0.0:2121->21/tcp` and `0.0.0.0:30000->30000/tcp`
- **Container crash:** Container stays running and Docker reports it "healthy"

### Likely Root Cause: TLS Mode Interaction

The server is started with `--tls=1` which means **TLS optional** — clients can connect plain or upgrade via AUTH TLS. However, the observed behavior (TCP connects but immediately gets reset/empty) is consistent with a **TLS-only expectation at the TCP level**.

Possible issues:
1. **`--tls=1` might behave differently than expected** in this pure-ftpd version. Mode `1` should allow plain FTP, but maybe something in the Docker image's startup script overrides this.
2. **The `-R` flag** (no anonymous connections) combined with `-E` (only anonymous allowed... wait, `-E` means "no anonymous") — these should be fine together, but worth double-checking.
3. **The self-signed cert might not be loading correctly**, causing pure-ftpd to reject connections. The PEM file at `/etc/ssl/private/pure-ftpd.pem` is auto-generated — it should work but could have permission issues.

### Unexplored Diagnosis Steps
- [ ] Check pure-ftpd version inside the container: `docker exec resources-ftps-1 pure-ftpd --help` or check `/var/log/` for detailed logs
- [ ] Try `--tls=0` (TLS disabled entirely) to see if plain FTP connections work — this isolates whether TLS is the problem
- [ ] Try `--tls=2` (TLS mandatory) with an actual TLS client to see if the server responds at all
- [ ] Check the generated PEM file: `docker exec resources-ftps-1 ls -la /etc/ssl/private/pure-ftpd.pem`
- [ ] Try connecting with `openssl s_client` to see if implicit TLS (connect-time TLS) is somehow enabled
- [ ] Check if pure-ftpd is actually running: `docker exec resources-ftps-1 ps aux | grep pure-ftpd`
- [ ] Try a different pure-ftpd image tag (e.g., `stilliard/pure-ftpd:hardened` or a specific version)
- [ ] Try an entirely different FTPS-capable FTP server image (e.g., vsftpd with ssl_enable=YES)

---

## 3. Key Architectural Decisions & Why

### Why Fixed Ports Instead of Socat Proxy
Testcontainers' `ComposeContainer.withExposedService("ftps", 21)` creates a socat (Alpine Linux) TCP proxy. This works fine for simple TCP protocols but **breaks FTP passive mode** because:

1. FTP passive mode opens a **second TCP connection** (data channel) to a different port
2. `withExposedService` only proxies the one port you specify (21)
3. FTPS adds another layer: **TLS session reuse** — the data channel must reuse the TLS session from the control channel. When the control channel goes through socat (random high port) but data channel connects directly (port 30000), the TLS endpoints don't match, and session reuse fails.

**Solution:** Use fixed port mappings (`2121:21`, `30000:30000`) with no socat proxy. The host connects directly to the container's mapped ports.

### Why `PUBLICHOST: "127.0.0.1"`
FTP passive mode: server responds to PASV command with the IP:port for the data channel. `PUBLICHOST` tells pure-ftpd what IP to advertise. Since tests run on the Docker host and connect to `localhost`, the server must advertise `127.0.0.1`.

### Why `waitForFtpReady` Instead of `Wait.forListeningPort()`
pure-ftpd's port 21 starts listening **before** TLS initialization (DH parameter generation) completes. A TCP port check passes, but the FTP daemon isn't ready to serve clients. `waitForFtpReady` verifies the actual FTP 220 welcome banner.

---

## 4. Files Modified This Session

| File | Change |
|------|--------|
| `quorus-core/pom.xml` | TC version → `${testcontainers.version}` (2.0.3); artifact → `testcontainers-junit-jupiter` |
| `quorus-core/src/test/resources/docker-compose-ftps-test.yml` | Fixed ports `2121:21` + `30000:30000` |
| `quorus-core/src/main/java/.../FtpTransferProtocol.java` | `readResponse()` null → IOException |
| `quorus-core/src/test/java/.../SharedTestContainers.java` | FTPS: fixed ports, no socat, `waitForFtpReady()` |
| `quorus-core/src/test/java/.../FtpsUploadIntegrationTest.java` | Simplified connectivity test, rewrote `seedFileOnServer()` |

---

## 5. Test Status

### Unit Tests: ALL PASS ✅
```
Tests run: 77, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
Breakdown:
- `FtpsTransferProtocolTest` — 21 tests (mock-based FTPS protocol tests)
- `InMemoryFtpsProtocolSimulatorTest` — 35 tests (in-memory simulator)
- `FtpTransferProtocolTest` — 21 tests (existing FTP tests)

### Integration Tests: FAILING ❌
```
Tests run: 9, Failures: 0, Errors: 8, Skipped: 0
```
- 8 tests fail with `IOException: FTP server closed connection unexpectedly (EOF)`
- 1 test passes (error handling test for non-existent file — doesn't need actual server connection)

### Run Commands
```bash
# Unit tests only
mvn test -pl quorus-core

# FTPS integration tests only
mvn test -pl quorus-core "-Dtest=FtpsUploadIntegrationTest"

# Full FTPS test results to file (useful for debugging)
mvn test -pl quorus-core "-Dtest=FtpsUploadIntegrationTest" 2>&1 > quorus-core/target/ftps-result.txt
```

---

## 6. Suggested Next Steps (Priority Order)

### Step 1: Verify pure-ftpd process is actually running
```powershell
docker exec resources-ftps-1 ps aux
docker exec resources-ftps-1 ls -la /etc/ssl/private/pure-ftpd.pem
```

### Step 2: Try with TLS disabled to isolate the issue
Change `docker-compose-ftps-test.yml`:
```yaml
ADDED_FLAGS: "--tls=0 -p 30000:30000"    # was --tls=1
```
Then test: `docker compose -f ... up -d`, wait 90s, try PowerShell TcpClient connection. If plain FTP works, the issue is TLS-specific.

### Step 3: Try a different FTP server image
`vsftpd` (used for our plain FTP tests) supports TLS. Replace pure-ftpd with:
```yaml
services:
  ftps:
    image: fauria/vsftpd
    environment:
      FTP_USER: testuser
      FTP_PASS: testpass
      PASV_MIN_PORT: 30000
      PASV_MAX_PORT: 30000
      PASV_ADDRESS: 127.0.0.1
    # Add vsftpd.conf overrides for SSL:
    #   ssl_enable=YES
    #   allow_anon_ssl=NO
    #   force_local_data_ssl=NO
    #   force_local_logins_ssl=NO
    #   rsa_cert_file=/etc/ssl/certs/vsftpd.pem
```
This would need a custom vsftpd.conf mounted as a volume.

### Step 4: Try `openssl s_client` for Implicit FTPS test
```powershell
# Install OpenSSL or use Git's bundled one:
& "C:\Program Files\Git\usr\bin\openssl.exe" s_client -connect localhost:2121 -starttls ftp
```
This would tell us if the TLS handshake works and what the server certificate looks like.

### Step 5: Consider GenericContainer instead of ComposeContainer
If the port mapping / networking is the issue, try using Testcontainers `GenericContainer` directly instead of `ComposeContainer`:
```java
GenericContainer<?> ftps = new GenericContainer<>("stilliard/pure-ftpd:latest")
    .withEnv("FTP_USER_NAME", "testuser")
    .withEnv("FTP_USER_PASS", "testpass")
    .withEnv("ADDED_FLAGS", "--tls=1 -p 30000:30000")
    .withEnv("PUBLICHOST", "127.0.0.1")
    .withExposedPorts(21, 30000)
    .waitingFor(Wait.forLogMessage(".*Starting Pure-FTPd.*", 1))
    .withStartupTimeout(Duration.ofMinutes(3));
```
This avoids Docker Compose entirely and lets TC manage the container lifecycle directly. The socat proxy issue still applies for PASV ports, but we could use `withFixedExposedPort()` from the TC utilities module.

---

## 7. Environment Reference

| Component | Version |
|-----------|---------|
| Java | 21 |
| Vert.x | 5.0.2 |
| Testcontainers | 2.0.3 |
| commons-net | 3.11.1 |
| Docker Desktop | 29.2.0 (Windows, Linux containers) |
| Docker Engine pipe | `npipe:////./pipe/docker_engine` |
| OS | Windows |
| pure-ftpd image | `stilliard/pure-ftpd:latest` |

---

## 8. Quick-Start: Resume Debugging

```powershell
# 1. Start container
docker compose -f quorus-core/src/test/resources/docker-compose-ftps-test.yml up -d

# 2. Wait for healthy
docker ps --format "table {{.Names}}\t{{.Status}}"

# 3. Check logs
docker compose -f quorus-core/src/test/resources/docker-compose-ftps-test.yml logs ftps

# 4. Test connection (PowerShell)
$tcp = New-Object System.Net.Sockets.TcpClient
$tcp.Connect("localhost", 2121)
$stream = $tcp.GetStream()
$reader = New-Object System.IO.StreamReader($stream)
$banner = $reader.ReadLine()
Write-Host "Banner: $banner"
$tcp.Close()

# 5. Clean up
docker compose -f quorus-core/src/test/resources/docker-compose-ftps-test.yml down
```
