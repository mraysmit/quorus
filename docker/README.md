<img src="../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Docker Guide

**Version:** 3.0  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Scope:** Every Docker and Compose asset in the repository: building images, starting each topology, verifying it, and the helper scripts

This is the single guide to the Docker assets. It replaces the former Cluster Startup Guide, Docker Testing README and controller Docker build note.

## Security posture

The controller's production profile requires TLS 1.3 mutual authentication on HTTP and Raft, trusted identity resolution, authorization and audit. Every topology below except the TLS example explicitly selects the **insecure development profile**: request security and HTTP/Raft TLS are disabled with the required insecure-development opt-in. They are development and test assets, not production deployment templates. See [Architecture Specification §3](../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md#3-capability-status) and the [Security Deployment Guide](../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md).

## Building images

Images package jars built on the host; nothing is compiled inside Docker. The Dockerfiles are single-stage, based on `amazoncorretto:27.0.0-alpine3.24`, and copy `quorus-controller/target/quorus-controller-*.jar` (and, for the agent, `quorus-agent/target/lib/`). No `m2cache` build context, `M2_REPO` variable or Maven install is needed.

Build the controller and agent jars with JDK 27, then start a topology with `--build` so its image picks up the jars you just built:

```powershell
./docker/build-runtime.ps1
docker compose -f docker/compose/docker-compose-single-controller.yml up -d --build
```

```bash
sh docker/build-runtime.sh
docker compose -f docker/compose/docker-compose-single-controller.yml up -d --build
```

Without a prior build the image build fails at its `COPY` step; without `--build` Compose may reuse a stale image.

## Compose topologies

All files are in `docker/compose/`. Host ports are bound to `127.0.0.1`.

| File | Topology | Host ports | Notes |
|---|---|---|---|
| `docker-compose-single-controller.yml` | One controller | 8080 | The quick start, and the target of the [HTTPie runbook](../scripts/httpie/RUNBOOK.txt) |
| `docker-compose-controller-first.yml` | Three controllers behind an nginx load balancer | 8080 (nginx), 8081–8083 (controllers) | nginx answers its own `GET /health`; every other path, including `/health/live` and `/health/ready`, goes to one controller |
| `docker-compose-cluster.yml` | Three controllers | 8081–8083 | |
| `docker-compose.yml` | Five controllers | 8081–8085 | Static membership; do not scale it as a membership change |
| `docker-compose-5node.yml` | Five controllers | 8081–8085 | |
| `docker-compose-network-test.yml` | Five controllers for network-partition experiments | 8081–8085 | |
| `docker-compose-full-network.yml` | Three controllers, three agents, FTP, SFTP and HTTP file servers, a test-file generator | 8081–8083, 21, 30000–30009, 2222, 8090 | No SMB server. See [Full network](#full-network) |
| `docker-compose-tls-example.yml` | One controller with generated certificates and production HTTP and Raft mutual TLS | 8443 | Local demonstration PKI only; see the root [README](../README.md#local-mutual-tls-example) |
| `docker-compose-protocol-servers.yml` | FTP, SFTP and SMB servers for protocol tests | 21, 30000–30009, 2222, 4445 | The `quorus-core` integration tests start their own stacks; use this for manual testing |
| `docker-compose-observability.yml` | OTel Collector, Tempo, Prometheus, Loki, Grafana | 4317, 4318, 8888, 13133, 3200, 9095, 9090, 3100, 3000 | |
| `docker-compose-observability-cluster.yml` | The observability stack plus three controllers | as above, plus 8081–8083 and 9464–9466 | |
| `docker-compose-loki.yml` | Standalone Loki, Promtail, Prometheus and Grafana | 3110 (Loki), 3010 (Grafana), 9091 (Prometheus) | Ports chosen not to clash with the observability stacks |
| `docker-compose-elk.yml` | Elasticsearch, Logstash, Filebeat, Kibana | 9200, 5601, 5044, 12201 | |
| `docker-compose-fluentd.yml` | Fluentd, Elasticsearch, Kibana | 24224, 9200, 5601 | |

Validate a file without starting it with `docker compose -f <file> config --quiet`.

## Verifying a controller

For the single controller:

```powershell
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
curl http://localhost:8080/raft/status
curl http://localhost:8080/api/v1/info
curl http://localhost:8080/metrics
```

In a multi-controller topology, check every controller on its own port. Exactly one should report `"isLeader": true` in `/raft/status`, and all should report the same `leaderId`:

```powershell
8081..8083 | ForEach-Object { curl "http://localhost:$_/health/ready"; curl "http://localhost:$_/raft/status" }
```

Only the leader accepts writes; a follower answers a write with `503` and code `NOT_LEADER`.

Controller startup brings up Raft storage, the Raft node, the gRPC server and the HTTP API. It starts no route trigger evaluator and no assignment scheduler: a submitted transfer runs only after a caller assigns it with `POST /api/v1/assignments`.

## Agents

An agent needs a tenant and a controller URL; it refuses to start without a tenant:

```text
QUORUS_AGENT_TENANT_ID=development
QUORUS_AGENT_CONTROLLER_URL=http://controller1:8080/api/v1
```

The legacy names `AGENT_TENANT_ID` and `CONTROLLER_URL` are still read, but the `QUORUS_AGENT_*` names win. The agent image's entrypoint waits up to 60 seconds for the controller's `/health/live` before starting the agent, and presents the agent's client certificate when the controller URL is `https`. The agent stops if its first registration fails, so it must reach the leader.

## Full network

`docker-compose-full-network.yml` runs a larger development network. The agents register with `controller1` only; if `controller1` is not the leader, their registration is rejected and they stop and restart until it is. Start it with the helper, which builds the jars first when given `-Build`:

```powershell
cd docker
.\scripts\start-full-network.ps1 -Build
.\scripts\test-transfers.ps1
docker compose -f compose/docker-compose-full-network.yml logs -f
docker compose -f compose/docker-compose-full-network.yml down
```

`test-transfers.ps1` finds the leader, lists the `development` tenant's agents, submits HTTP transfers whose source is the in-network HTTP server, assigns each transfer to an agent explicitly, and polls until the transfers finish. FTP and SFTP transfers need a governed service connection with an external secret reference; credentials are never embedded in transfer URIs.

| Service | URL | Credentials |
|---|---|---|
| Controllers 1–3 | http://localhost:8081 – 8083 | — |
| HTTP file server | http://localhost:8090 (inside the network: `http://http-server`) | — |
| FTP server | ftp://localhost:21 | testuser / testpass |
| SFTP server | sftp://localhost:2222 | testuser / testpass |

## Logging stack

```powershell
cd docker/scripts
powershell -ExecutionPolicy Bypass -File setup-logging.ps1
```

The standalone stack serves Grafana on http://localhost:3010 (admin/admin), Loki on http://localhost:3110 and Prometheus on http://localhost:9091. Check collection with `docker logs quorus-logging-promtail`, and query Loki directly:

```bash
curl "http://localhost:3110/loki/api/v1/query_range?query={container_name=\"quorus-controller1\"}"
```

The demonstration scripts `demo-logging.ps1`, `log-extraction-demo.ps1` and `simple-log-demo.ps1` assume a controller on port 8081 and this stack.

## Directory contents

```
docker/
├── build-runtime.ps1, build-runtime.sh   # build the controller and agent jars on the host
├── start.ps1, start-quick.ps1            # older launchers for the single, cluster and controller-first
├── start-observability.ps1               # topologies and the observability stack; they do not build the
│                                         # jars first or pass --build, so run build-runtime and prefer
│                                         # the docker compose commands above
├── compose/                              # the Compose files above, with Grafana, Prometheus,
│                                         # Loki, Tempo, OTel Collector and nginx configuration
├── logging/                              # Loki, Promtail, Grafana and Prometheus configuration
├── scripts/                              # start-full-network, test-transfers, logging setup and demos
└── test-data/                            # sample registration and heartbeat payloads, check-agents.ps1,
                                          # send-heartbeat.ps1, and the full network's HTTP server nginx.conf
```

`test-data/check-agents.ps1` and `test-data/send-heartbeat.ps1` default to the single controller on port 8080; pass `-BaseUrl` for another controller. A heartbeat is a write, so send it to the leader.

## Docker-tagged tests

The controller's Docker test suites (tags `docker` and `slow`) are excluded from a default build. They build a test image from the host-built controller jar, so build the jars first and do not run `clean` in the same command:

```powershell
./docker/build-runtime.ps1
mvn verify '-Dtest.excludedGroups='
```

The [testing guide](../docs-design/testing/QUORUS_TESTING_README.md) describes the tags and lanes.

## Troubleshooting

- **Port conflicts:** check that the host ports in the table above are free.
- **Image builds fail at `COPY`:** run `docker/build-runtime` first.
- **An agent keeps restarting:** check that its tenant is set and that it can reach the leader.
- **Writes return `503 NOT_LEADER`:** send them to the leader shown by `/raft/status`.
- **Resources:** the full network and observability stacks need several GB of memory.
