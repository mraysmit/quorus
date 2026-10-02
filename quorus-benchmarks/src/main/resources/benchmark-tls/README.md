# Benchmark TLS material

Test-only certificates, copied from `quorus-core/src/test/resources/security/`. Not for any deployment.

| File | Contents | Used as |
|---|---|---|
| `node-cert.pem`, `node-key.pem` | Self-signed `CN=localhost` with SAN `localhost` and `127.0.0.1` (RSA, PKCS#8) | Every controller's HTTP and Raft identity, and the Raft and client trust anchor |
| `gateway-cert.pem`, `gateway-key.pem` | Self-signed `CN=quorus-client` (RSA, PKCS#8) | The benchmark client's certificate, configured as the controllers' trusted gateway subject |
