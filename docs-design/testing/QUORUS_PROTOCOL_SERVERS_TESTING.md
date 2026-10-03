# Protocol Server Testing Guide

Real FTP, FTPS, SFTP and SMB servers in Docker containers, used to test the Quorus protocol adapters.
Test lanes, tags and the full list of tests that need Docker are in the
[Testing Guide](QUORUS_TESTING_README.md).

There are two separate setups:

1. **Testcontainers fixtures** in `quorus-core/src/test/resources/`, started automatically by the
   upload integration tests in a default build (section 1).
2. **A standalone compose stack**, `docker/compose/docker-compose-protocol-servers.yml`, for manual
   testing and for `ProtocolServersLifecycleIntegrationTest`, which runs in the Docker lane (sections 2
   and 3).

---

## Table of Contents

1. [Testcontainers fixtures](#1-testcontainers-fixtures)
2. [Standalone compose stack](#2-standalone-compose-stack)
3. [ProtocolServersLifecycleIntegrationTest](#3-protocolserverslifecycleintegrationtest)
4. [Manual connection testing](#4-manual-connection-testing)
5. [Troubleshooting](#5-troubleshooting)

---

## 1. Testcontainers fixtures

`SharedTestContainers` (`quorus-core/src/test/java/dev/mars/quorus/protocol/SharedTestContainers.java`)
is a lazy singleton. Each container starts the first time a test asks for it, is shared by every test
class in the JVM, and is stopped by a JVM shutdown hook. Tests call
`assumeTrue(SharedTestContainers.isDockerAvailable())` first, so they are skipped, not failed, when
Docker is unavailable. No test class declares a `static @Container` field for these servers.

| Server | Compose file | Image | Credentials | Host ports |
|---|---|---|---|---|
| FTP | `docker-compose-ftp-test.yml` | Built from `ftp-docker/Dockerfile`: `delfer/alpine-ftp-server` (vsftpd) with a writable chroot and vsftpd in the foreground | `anonymous` / `anonymous@example.com` (the adapter's credential-free development fallback) | Control and one passive data port, chosen per run |
| FTPS | `docker-compose-ftps-test.yml` | Built from `ftps-docker/Dockerfile` (`withBuild(true)`): `delfer/alpine-ftp-server` plus OpenSSL; `ftps-entrypoint.sh` generates a self-signed certificate at start. Explicit FTPS (`AUTH TLS`), TLS optional, `require_ssl_reuse=NO` | `testuser` / `testpass` | Control and one passive data port, chosen per run |
| SFTP | `docker-compose-sftp-abort-test.yml` | `atmoz/sftp:alpine`, user directory `upload` | `testuser` / `testpass` | Dynamic, through the Testcontainers proxy |

- **FTP and FTPS use direct port mappings, not the Testcontainers proxy.** vsftpd rejects a passive
  data connection from a different source address than the control connection, and the proxy
  changes it. `SharedTestContainers` reserves two free host ports, passes them to compose as
  `FTP_CONTROL_PORT`/`FTP_DATA_PORT` (or the `FTPS_` equivalents) and advertises `127.0.0.1` in the
  passive reply. Without those variables the files default to 2100/21100 (FTP) and 2121/30000 (FTPS)
  for manual use.
- **Readiness:** FTP and FTPS are ready after three consecutive `220` greetings on the control port,
  not when Docker reports the container healthy. SFTP is ready when port 22 listens.
- **SMB:** there is no SMB fixture. No default-lane test starts an SMB server.

Tests that use the fixtures:

| Test | Servers |
|---|---|
| `FtpUploadIntegrationTest` | FTP |
| `FtpsUploadIntegrationTest` | FTPS |
| `SftpUploadIntegrationTest` | SFTP |
| `AdapterProgressAndStopTest` | FTP and SFTP |

Run one of them:

```bash
mvn test -pl quorus-core -Dtest=FtpsUploadIntegrationTest
```

---

## 2. Standalone compose stack

`docker/compose/docker-compose-protocol-servers.yml` defines three long-running servers on the network
`quorus-protocol-test`, with named volumes `quorus-ftp-test-data`, `quorus-sftp-test-data` and
`quorus-smb-test-data`.

| Service | Container | Image | Host port | Credentials | Path |
|---|---|---|---|---|---|
| `ftp` | `quorus-ftp-test` | `delfer/alpine-ftp-server` | `127.0.0.1:21`; passive `30000-30009` | `testuser` / `testpass` (`USERS: "testuser\|testpass"`) | `/` (home `/home/testuser`) |
| `sftp` | `quorus-sftp-test` | `atmoz/sftp:alpine` | `127.0.0.1:2222` | `testuser` / `testpass` | `/upload` |
| `smb` | `quorus-smb-test` | `ghcr.io/servercontainers/samba:latest` | `127.0.0.1:4445` | `testuser` / `testpass` | share `testshare` (`/share`) |

The control ports are bound to `127.0.0.1`. The FTP passive range `30000-30009` has no address in the
mapping, so Docker publishes it on all host interfaces. SMB uses host port 4445 so that it does not
collide with the Windows SMB service on 445.

Start, check and stop:

```bash
cd docker/compose
docker compose -f docker-compose-protocol-servers.yml up -d
docker compose -f docker-compose-protocol-servers.yml ps
docker compose -f docker-compose-protocol-servers.yml down -v
```

`down -v` also deletes the named volumes.

### Addresses

| Protocol | From the host | From a container on `quorus-protocol-test` |
|---|---|---|
| FTP | `localhost:21` | `ftp:21` (alias `ftp-server`) |
| SFTP | `localhost:2222` | `sftp:22` (alias `sftp-server`) |
| SMB | `localhost:4445` | `smb:445` (alias `smb-server`) |

Quorus transfer requests must not carry credentials in the URI: the controller rejects
credential-bearing source URIs (`CredentialBearingUriDetector`). Use `ftp://localhost:21/path` and
supply credentials through the agent's runtime configuration. The `user:password@host` form is only for
the manual clients in section 4.

---

## 3. ProtocolServersLifecycleIntegrationTest

`quorus-core/src/test/java/dev/mars/quorus/protocol/integration/ProtocolServersLifecycleIntegrationTest.java`
checks that the standalone stack is reachable with the same client libraries the adapters use. It is the
only test with an SMB server.

It is tagged `docker` and excluded from the default build, because the stack publishes fixed host ports
(decision DR-Q4 in the [register](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md)). It runs in the Docker
lane, or on its own:

```bash
mvn test -pl quorus-core -Dtest=ProtocolServersLifecycleIntegrationTest '-Dtest.excludedGroups='
```

The class manages its own stack, with the `docker compose` plugin:

- `@BeforeAll` runs `docker compose -p quorus-protocol-lifecycle-test -f docker-compose-protocol-servers.yml
  up -d --wait` in `docker/compose`, which returns when every service's health check passes.
- `@AfterAll` runs `down` for the same project, removing the containers and the network it started. It
  leaves the volumes: the compose file gives them fixed names, so they are shared with a stack started
  by hand, whose data `down -v` would delete.
- It cannot run beside a stack started by hand, because the container names and host ports are fixed.
  Stop that stack first; otherwise the test fails at start and changes nothing.
- It connects to `127.0.0.1`, not `localhost`: the ports are published on `127.0.0.1` only, and
  `localhost` can resolve to `::1`.

| Test | Checks |
|---|---|
| `testFtpServerReachable` | The FTP host is configured |
| `testFtpConnection` | Connect, log in, passive mode, list |
| `testSftpConnection` | SSH session, SFTP channel, directory operations |
| `testSmbConnection` | NTLM authentication, share access, directory operations |
| `testAllServersOperational` | Summary |

| Protocol | Client library | Maven artifact |
|---|---|---|
| FTP | Apache Commons Net | `commons-net:commons-net` |
| SFTP | JSch (mwiede fork) | `com.github.mwiede:jsch` |
| SMB | jCIFS-ng | `eu.agno3.jcifs:jcifs-ng` |

---

## 4. Manual connection testing

With the standalone stack running:

```bash
# FTP: list the home directory (passive mode)
curl --list-only ftp://testuser:testpass@localhost:21/

# SFTP (OpenSSH client)
sftp -P 2222 testuser@localhost
```

SMB on port 4445 cannot be reached through Windows Explorer or `net use`, which support only port 445.
Use jCIFS-ng (as `ProtocolServersLifecycleIntegrationTest` does) or `smbclient` with `-p 4445`.

---

## 5. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Cannot connect to the Docker daemon` | Start Docker. The core fixture tests are skipped without Docker; the IT fails |
| `port is already allocated` on 21, 2222 or 4445 | Another process or stack holds the port. Find it with `netstat -ano` (Windows) or `ss -ltnp` (Linux) |
| FTP listing times out (standalone stack) | The passive ports `30000-30009` are blocked. Check `docker port quorus-ftp-test` |
| `425 Security: Bad IP connecting` | A passive data connection arrived from a different address than the control connection, for example through a proxy. The fixtures avoid this with direct port mappings |
| The IT fails in `@BeforeAll` because `docker-compose` cannot be started | Install the standalone `docker-compose` command; the IT always calls it, even when the stack is already running |
| SMB access denied | Log in as `testuser`, not `WORKGROUP\testuser` |

Container logs:

```bash
docker logs quorus-ftp-test
docker logs quorus-sftp-test
docker logs quorus-smb-test
docker compose -f docker/compose/docker-compose-protocol-servers.yml logs -f
```
