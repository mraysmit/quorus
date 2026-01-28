# Task: Remove Hard-Coded Configurations from Quorus Modules

**Created:** 2026-01-28  
**Priority:** High  
**Status:** ✅ COMPLETED

## Objective

Audit all Quorus modules to identify and externalize hard-coded configuration values into properties files with sensible defaults.

---

## Quorus Module Overview

| Module | Description | Config Status |
|--------|-------------|---------------|
| `quorus-core` | Core domain models, transfer engine interfaces, configuration classes | ✅ Done |
| `quorus-controller` | Raft consensus, cluster coordination, HTTP API, gRPC transport | ✅ Done |
| `quorus-agent` | Transfer agent, job polling, heartbeat, telemetry | ✅ Done |
| `quorus-api` | REST API (Vert.x + Weld), CLI client | ✅ Done |
| `quorus-workflow` | Workflow engine, YAML parser, execution management | ✅ Clean |
| `quorus-tenant` | Multi-tenancy, quotas, tenant management | ✅ Clean |
| `quorus-integration-examples` | Example code and demos | 🚫 Exempt |

---

## Detailed Module Audit

### 1. ✅ quorus-controller (COMPLETED)

**Status:** Done - `AppConfig` class created with `quorus-controller.properties`

**Completed:**
- Node identity (derived from hostname)
- HTTP port (8080)
- Raft port (9080)
- Cluster nodes configuration
- Telemetry settings (OTLP endpoint, Prometheus port)
- Job assignment timers

---

### 2. ✅ quorus-agent (COMPLETED)

**Status:** Done - `AgentConfig` class created with `quorus-agent.properties`

**Files Modified:**
- Created: `quorus-agent/src/main/resources/quorus-agent.properties`
- Created: `quorus-agent/src/main/java/dev/mars/quorus/agent/config/AgentConfig.java`
- Updated: `AgentTelemetryConfig.java` - uses AgentConfig for ports/endpoints
- Updated: `QuorusAgent.java` - uses AgentConfig for job polling timers
- Created: `quorus-agent/src/test/java/dev/mars/quorus/agent/config/AgentConfigTest.java`

**Externalized Values:**
- Agent ID (derived from hostname)
- Controller URL
- Heartbeat interval
- Job polling intervals (initial delay, interval)
- Prometheus port
- OTLP endpoint
- Max concurrent transfers
- Region and datacenter

---

### 3. ✅ quorus-core (COMPLETED)

**Status:** Done - `QuorusConfiguration` already existed, added `quorus.properties` file

**Files:**
- Existing: `QuorusConfiguration.java` - comprehensive config class with builder pattern
- Created: `quorus-core/src/main/resources/quorus.properties`
- Existing: `QuorusConfigurationTest.java` - comprehensive tests

**Notes:**
- QuorusConfiguration already had good design with properties file support
- Added actual properties file with documented defaults
- Supports multiple config file locations (classpath, file system, home dir, /etc/)

---

### 4. ✅ quorus-api (COMPLETED)

**Status:** Done - `ApiConfig` class created with `quorus-api.properties`

**Files Modified:**
- Created: `quorus-api/src/main/resources/quorus-api.properties` (replaced old Quarkus config)
- Created: `quorus-api/src/main/java/dev/mars/quorus/api/config/ApiConfig.java`
- Updated: `QuorusApiApplication.java` - uses ApiConfig for HTTP port/host
- Updated: `HeartbeatProcessor.java` - uses ApiConfig for agent timeout/failure check
- Created: `quorus-api/src/test/java/dev/mars/quorus/api/config/ApiConfigTest.java`

**Externalized Values:**
- HTTP port and host
- API name and version
- Agent heartbeat interval, timeout, failure check interval
- Transfer settings (max concurrent, retries, timeout)
- Controller discovery settings
- Telemetry settings
- CLI default URL

---

### 5. ✅ quorus-workflow (CLEAN - No Action Needed)

**Status:** Clean - No hard-coded config values found

**Analysis:**
- Uses `GlobalOpenTelemetry.getMeter()` - configured externally
- Only hard-coded values are reasonable defaults (pool size, max execution time)
- No port numbers or environment-specific configuration

---

### 6. ✅ quorus-tenant (CLEAN - No Action Needed)

**Status:** Clean - No hard-coded config values found

**Analysis:**
- Uses `GlobalOpenTelemetry.getMeter()` - configured externally
- Service logic doesn't have environment-specific hardcoding
- Tenant quotas are model data, not environment config

---

### 7. 🚫 quorus-integration-examples (EXEMPT)

**Status:** Example code - hard-coded values are acceptable here for demonstration purposes.

**Note:** These are intentionally hard-coded for clarity in examples. No action needed.

---

## Summary of Changes

### Files Created
| File | Purpose |
|------|---------|
| `quorus-controller/src/main/resources/quorus-controller.properties` | Controller configuration |
| `quorus-controller/src/main/java/.../config/AppConfig.java` | Controller config loader |
| `quorus-agent/src/main/resources/quorus-agent.properties` | Agent configuration |
| `quorus-agent/src/main/java/.../config/AgentConfig.java` | Agent config loader |
| `quorus-agent/src/test/.../config/AgentConfigTest.java` | Agent config tests |
| `quorus-core/src/main/resources/quorus.properties` | Core library configuration || `quorus-api/src/main/resources/quorus-api.properties` | API configuration || `quorus-api/src/main/java/.../config/ApiConfig.java` | API config loader |
| `quorus-api/src/test/.../config/ApiConfigTest.java` | API config tests |

### Files Modified
| File | Change |
|------|--------|
| `quorus-controller/src/main/.../TelemetryConfig.java` | Uses AppConfig |
| `quorus-controller/src/main/.../QuorusControllerVerticle.java` | Uses AppConfig |
| `quorus-controller/src/main/.../QuorusControllerApplication.java` | Uses AppConfig |
| `quorus-controller/src/main/.../JobAssignmentService.java` | Uses AppConfig |
| `quorus-agent/src/main/.../AgentTelemetryConfig.java` | Uses AgentConfig |
| `quorus-agent/src/main/.../QuorusAgent.java` | Uses AgentConfig |
| `quorus-api/src/main/.../QuorusApiApplication.java` | Uses ApiConfig |
| `quorus-api/src/main/.../HeartbeatProcessor.java` | Uses ApiConfig |

---

## Implementation Pattern

Follow the pattern established in `quorus-controller`:

1. **Create module-specific properties file** in `src/main/resources/` (e.g., `quorus-agent.properties`)
2. **Create `XxxConfig.java`** singleton class:
   - Load properties from classpath
   - Support environment variable overrides (QUORUS_MODULE_PROPERTY format)
   - Log all configuration properties at startup
   - Provide typed getters with sensible defaults

```java
public final class AgentConfig {
    private static final AgentConfig INSTANCE = new AgentConfig();
    
    public static AgentConfig get() {
        return INSTANCE;
    }
    
    public String getString(String key, String defaultValue) {
        // 1. Check QUORUS_AGENT_XXX env var
        // 2. Check properties file
        // 3. Return default
    }
}
```

---

## Testing Considerations

- [x] Unit tests for each config class (AgentConfigTest, ApiConfigTest)
- [x] Test environment variable override behavior
- [x] Test missing properties file scenario (should use defaults)
- [ ] Integration tests with custom config
- [ ] AppConfigTest for quorus-controller (TODO)

---

## Backward Compatibility

All changes maintain backward compatibility:
- Environment variables override properties (QUORUS_MODULE_PROPERTY format)
- Missing config files fall back to sensible defaults
- No breaking changes to existing deployments

---

## Notes

- The `quorus-integration-examples` module is exempt (example code)
- Test code (`src/test/`) is exempt
- Focus on production `src/main/java` code
- Constants like `1024 * 1024` for byte conversions are acceptable
- Copyright year references are not configuration
