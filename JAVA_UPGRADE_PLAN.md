# Java 21 LTS Upgrade Plan

## 1. Objective
Upgrade the Quorus application runtime and build environment to Java 21 LTS to leverage the latest language features, performance improvements, and long-term support.

## 2. Current State Analysis (Verification)
An audit of the codebase was performed on January 5, 2026, to determine the current Java version configuration.

### 2.1 Maven Configuration (`pom.xml`)
| Module | File | Property | Value | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Root** | `pom.xml` | `maven.compiler.source` | `21` | ✅ Updated |
| **Root** | `pom.xml` | `maven.compiler.target` | `21` | ✅ Updated |
| **Agent** | `docker/agents/pom.xml` | `maven.compiler.source` | `21` | ✅ Updated |
| **Agent** | `docker/agents/pom.xml` | `maven.compiler.target` | `21` | ✅ Updated |

### 2.2 Docker Configuration (`Dockerfile`)
| Component | File | Base Image | Status |
| :--- | :--- | :--- | :--- |
| **Controller** | `quorus-controller/Dockerfile` | `maven:3.9.5-eclipse-temurin-21` | ✅ Updated |
| **API** | `quorus-api/Dockerfile` | `maven:3.9.5-eclipse-temurin-21` | ✅ Updated |
| **Agent** | `docker/agents/Dockerfile` | `maven:3.9.6-eclipse-temurin-21` | ✅ Updated |

### 2.3 Documentation
| File | Section | Content | Status |
| :--- | :--- | :--- | :--- |
| `README.md` | Badges | `Java-21+` | ✅ Updated |

## 3. Execution Plan
Since all components are already configured for Java 21, no code changes are required.

### 3.1 Verification Steps
To ensure the environment is correctly set up, run the following commands:

1.  **Verify Local Maven Version**:
    ```bash
    mvn -v
    ```
    *Expected Output*: Should reference Java version 21.

2.  **Verify Docker Runtime**:
    ```bash
    docker run --rm -it eclipse-temurin:21-jre-alpine java -version
    ```
    *Expected Output*: `openjdk version "21..."`

## 4. Conclusion
The upgrade to Java 21 LTS is **COMPLETE**. The application is fully configured to build and run on Java 21.
