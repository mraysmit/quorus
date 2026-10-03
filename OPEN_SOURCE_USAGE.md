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

The complete list of third-party runtime dependencies, with versions and licenses as Maven resolves them, is the generated [THIRD-PARTY.txt](./THIRD-PARTY.txt). Do not maintain it by hand. Regenerate it after any dependency change and commit it with that change:

```powershell
./scripts/generate-third-party-inventory.ps1          # regenerate
./scripts/generate-third-party-inventory.ps1 -Check   # fail if it is out of date
```

The [NOTICE](./NOTICE) file carries the attribution notices that distribution requires; THIRD-PARTY.txt is the full inventory.

Points the generated list does not show:

- **Vert.x** is used only by `quorus-controller` until plan item `RT-06`, and by the profile-only `quorus-benchmarks` module.
- **javax.annotation API** (CDDL 1.1 / GPL 2.0 with Classpath Exception) is used by the generated gRPC code in `quorus-controller`.
- **The container base image**, Amazon Corretto 27 (`amazoncorretto:27.0.0-alpine3.24`, GPL 2.0 with Classpath Exception), is not a Maven dependency.
- The FTP/FTPS, SMB and NFS adapters use no third-party protocol library at runtime; Commons Net and jCIFS-ng are test dependencies.

### Test Dependencies

#### Testing Frameworks
- **JUnit Jupiter** (5.14.3) - Eclipse Public License 2.0
- **Vert.x JUnit5** (5.0.8) - Apache License 2.0 / EPL 2.0
- **AssertJ Core** (3.27.7) - Apache License 2.0
- **Awaitility** (4.3.0) - Apache License 2.0

#### Integration Testing
- **TestContainers** (2.0.3) - MIT License
- **TestContainers JUnit Jupiter** (2.0.3) - MIT License
- **Commons Net** (3.12.0) - Apache License 2.0; FTP and FTPS test clients
- **jCIFS-ng** (2.1.10) - LGPL 2.1; SMB test utilities

## License Compatibility Matrix

| License | Compatible with Apache 2.0 | Notes |
|---------|----------------------------|-------|
| Apache 2.0 | Yes | Same license |
| MIT | Yes | Permissive, compatible |
| BSD 2-Clause | Yes | Permissive, compatible |
| BSD 3-Clause | Yes | Permissive, compatible |
| EPL 2.0 | Yes | Compatible with Apache 2.0 |
| CDDL 1.1 | Yes, as a binary dependency | Weak copyleft on the CDDL-licensed files themselves |
| LGPL 2.1 | Conditional | Dynamic linking only; currently test scope only (jCIFS-ng) |
| GPL 2.0 with Classpath Exception | Yes, for the JDK runtime | Applies to the container base image, not to Quorus code |

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

The build does not run a license plugin. If one is added, it needs a header template file (for example a new `LICENSE-HEADER.txt` holding the header shown above, which does not exist in the repository today):

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
