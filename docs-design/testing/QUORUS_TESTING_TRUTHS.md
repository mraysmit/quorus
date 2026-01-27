

# QUORUS TESTING TRUTHS

## The non-negotiables (baseline truth)

If you’re doing **real integration testing** in Java and you’re *not* using Testcontainers, you’re either:

* **lying** to yourself with mocks, or
* pushing **risks** into production.

Testcontainers is **not optional** for anything involving:

* databases
* message brokers
* filesystems
* SFTP/FTP
* Kafka / Pulsar
* anything stateful

---

## The questions people *should* be asking (but usually don’t)

### 1. **JUnit lifecycle: static vs per-test containers**

* `static @Container` (JUnit 5):

  * Faster
  * Shared state → you must reset explicitly
* Per-test containers:

  * Slower
  * Cleaner isolation

**Rule of thumb**

* DB + schema migration → static
* Anything with mutable business state → per-test or hard reset

---

### 2. **One container or Docker Compose?**

Blunt answer:

* **Single dependency** → plain `GenericContainer`
* **2–3 tightly coupled services** → `DockerComposeContainer`
* **More than that** → your test design is already suspect

Compose is fine for:

* SFTP + app
* Kafka + Schema Registry
* MinIO + app

Compose is **not** fine for:

* reproducing production topology
* hiding bad boundaries

---

### 3. **Networking between containers**

If you don’t do this properly, tests will flake.

Correct pattern:

```java
Network network = Network.newNetwork();

PostgreSQLContainer<?> pg =
  new PostgreSQLContainer<>("postgres:15")
    .withNetwork(network)
    .withNetworkAliases("db");

GenericContainer<?> app =
  new GenericContainer<>("my-app")
    .withNetwork(network)
    .withEnv("DB_HOST", "db");
```

If you’re using `localhost` inside containers, you’re already wrong.

---

### 4. **Schema migration & test determinism**

If Flyway/Liquibase is not:

* run **once per container lifecycle**
* versioned
* repeatable

Then your tests are non-deterministic garbage.

**Correct pattern**

* Container starts
* Migration runs
* Tests execute
* Data cleanup between tests (TRUNCATE, not DROP)

---

### 5. **Parallel test execution**

Yes, Testcontainers supports it.
No, your DB probably doesn’t unless you isolate properly.

Options:

* One container per test class
* One schema per test
* One database per test (Postgres supports this cheaply)

If you ignore this, CI will punish you.

---

### 6. **Performance myths**

> “Testcontainers are slow”

No.
**Cold Docker startup is slow. Bad test design is slower.**

Fixes:

* Reuse containers (`~/.testcontainers.properties`)
* Static containers
* Don’t restart containers per test
* Don’t use `@DirtiesContext` unless you enjoy pain

---

### 7. **When NOT to use Testcontainers**

Rare, but real:

* Pure algorithmic logic
* In-memory deterministic data structures
* Stateless transformations

The moment I/O appears, mocks stop being credible.

---

## The questions *you* might actually mean

Pick one and I’ll go deep, with code:

1. **JUnit 5 + Testcontainers best practice**
2. **PostgreSQL / Kafka / SFTP container setup**
3. **Docker Compose vs single containers**
4. **Vert.x / Quarkus / Spring Boot integration**
5. **CI (GitHub Actions / Jenkins) gotchas**
6. **Parallel tests & isolation strategy**
7. **Why mocks are actively harmful in integration layers**

Tell me which one.
