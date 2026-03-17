# Open Source Usage and License Compliance Guide

## Overview

Quorus is an open source project licensed under the **Apache License 2.0**. This document outlines the open source components used, license requirements, and compliance guidelines.

## Project License

**License:** Apache License 2.0  
**Copyright:** 2025 Mark Andrew Ray-Smith Cityline Ltd  
**License File:** [LICENSE](./LICENSE)  
**Attribution File:** [NOTICE](./NOTICE)

### Apache License 2.0 Summary

**Permissions:**
- Commercial use
- Modification
- Distribution
- Patent use
- Private use

**Conditions:**
- License and copyright notice
- State changes
- Include NOTICE file

**Limitations:**
- Trademark use
- Liability
- Warranty

## Required License Headers

All Java source files must include the following license header:

```java
/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

## Third-Party Dependencies

### Runtime Dependencies

#### Reactive & Async Processing
- **Eclipse Vert.x Core** (5.0.8) - Apache License 2.0 / EPL 2.0
- **Vert.x Web** (5.0.8) - Apache License 2.0 / EPL 2.0
- **Vert.x Web Client** (5.0.8) - Apache License 2.0 / EPL 2.0
- **Vert.x PostgreSQL Client** (5.0.8) - Apache License 2.0 / EPL 2.0
- **Vert.x gRPC Server** (5.0.8) - Apache License 2.0 / EPL 2.0
- **Vert.x gRPC Client** (5.0.8) - Apache License 2.0 / EPL 2.0
- **Vert.x OpenTelemetry** (5.0.8) - Apache License 2.0 / EPL 2.0

#### JSON & YAML Processing
- **Jackson Databind** (2.19.4) - Apache License 2.0
- **Jackson Core** (2.19.4) - Apache License 2.0
- **Jackson Annotations** (2.19.4) - Apache License 2.0
- **Jackson Datatype JSR310** (2.19.4) - Apache License 2.0
- **Jackson Dataformat YAML** (2.19.4) - Apache License 2.0
- **SnakeYAML** (2.5) - Apache License 2.0

#### gRPC & Protocol Buffers
- **gRPC Protobuf** (1.68.1) - Apache License 2.0
- **gRPC Stub** (1.68.1) - Apache License 2.0
- **gRPC Netty** (1.68.1) - Apache License 2.0
- **Protocol Buffers Java** (3.25.5) - BSD 3-Clause License
- **Protocol Buffers Java Util** (3.25.5) - BSD 3-Clause License

#### Raft Consensus & Storage
- **RaftLog Core** (1.1.0) - Apache License 2.0
- **RocksDB JNI** (9.11.2) - Apache License 2.0

#### Observability
- **OpenTelemetry API** (1.59.0) - Apache License 2.0
- **OpenTelemetry SDK** (1.59.0) - Apache License 2.0
- **OpenTelemetry OTLP Exporter** (1.59.0) - Apache License 2.0
- **OpenTelemetry Prometheus Exporter** (1.59.0-alpha) - Apache License 2.0
- **OpenTelemetry Logback Appender** (2.14.0-alpha) - Apache License 2.0

#### Logging
- **SLF4J API** (2.0.17) - MIT License
- **SLF4J JUL Bridge** (2.0.17) - MIT License
- **Logback Classic** (1.5.32) - EPL 1.0 / LGPL 2.1

#### Protocol Adapters
- **JSch** (0.2.26) - BSD 2-Clause License
- **jCIFS-ng** (2.1.10) - LGPL 2.1
- **Commons Net** (3.12.0) - Apache License 2.0

#### Validation & Expression
- **Jakarta Validation API** (3.0.2) - Apache License 2.0
- **JSON Schema Validator** (1.5.9) - Apache License 2.0
- **Spring Expression Language** (6.2.16) - Apache License 2.0

#### Utilities
- **Google Guava** (33.5.0-jre) - Apache License 2.0

### Test Dependencies

#### Testing Frameworks
- **JUnit Jupiter** (5.14.3) - Eclipse Public License 2.0
- **Vert.x JUnit5** (5.0.8) - Apache License 2.0 / EPL 2.0
- **AssertJ Core** (3.27.7) - Apache License 2.0
- **Awaitility** (4.3.0) - Apache License 2.0

#### Integration Testing
- **TestContainers** (2.0.3) - MIT License
- **TestContainers JUnit Jupiter** (2.0.3) - MIT License

## License Compatibility Matrix

| License | Compatible with Apache 2.0 | Notes |
|---------|----------------------------|-------|
| Apache 2.0 | Yes | Same license |
| MIT | Yes | Permissive, compatible |
| BSD 2-Clause | Yes | Permissive, compatible |
| BSD 3-Clause | Yes | Permissive, compatible |
| EPL 2.0 | Yes | Compatible with Apache 2.0 |
| LGPL 2.1 | Conditional | Dynamic linking only |

## Compliance Requirements

### For Distribution

1. **Include License File:** Copy of Apache License 2.0
2. **Include NOTICE File:** Attribution notices for all dependencies
3. **Preserve Copyright Notices:** Keep all existing copyright headers
4. **Document Changes:** If you modify the code, document the changes

### For Commercial Use

**Allowed:**
- Use in commercial products
- Sell products containing Quorus
- Modify for commercial purposes
- Create proprietary derivatives

**Required:**
- Include license and copyright notices
- Include NOTICE file in distributions
- Don't use "Quorus" trademark without permission

### For Modification

**Allowed:**
- Modify source code
- Create derivative works
- Distribute modifications

**Required:**
- Mark modified files with change notices
- Include original license headers
- Include NOTICE file

## Attribution Requirements

When using Quorus in your project, include:

### In Documentation
```
This product includes Quorus (https://github.com/mraysmit/quorus)
Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
Licensed under the Apache License 2.0
```

### In Software
- Include the NOTICE file in your distribution
- Preserve all copyright headers in source code
- Include Apache License 2.0 text

## Automated Compliance

### Header Management Script

Use the provided script to ensure all files have proper headers:

```powershell
# Check current status
.\scripts\update-java-headers.ps1 -DryRun

# Update headers with license information
.\scripts\update-java-headers.ps1
```

### Maven License Plugin

Consider adding the Maven License Plugin to your build:

```xml
<plugin>
    <groupId>com.mycila</groupId>
    <artifactId>license-maven-plugin</artifactId>
    <version>4.2</version>
    <configuration>
        <header>LICENSE-HEADER.txt</header>
        <includes>
            <include>**/*.java</include>
        </includes>
    </configuration>
</plugin>
```

## Frequently Asked Questions

### Q: Can I use Quorus in my commercial product?
**A:** Yes, the Apache License 2.0 explicitly allows commercial use.

### Q: Do I need to open source my modifications?
**A:** No, Apache License 2.0 does not require derivative works to be open source.

### Q: Can I remove the license headers?
**A:** No, you must preserve all copyright and license notices.
