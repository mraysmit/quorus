# Quorus User Guide

**Version:** 1.1  
**Date:** 2025-04-14  
**Author:** Mark Andrew Ray-Smith Cityline Ltd  
**Updated:** 2026-01-11  



## Table of Contents

### **Part I: User Guide (Non-Technical)**
1. [Introduction](#introduction)
2. [Key Features](#key-features)
3. [Getting Started](#getting-started)
4. [YAML Workflows](#yaml-workflows)
5. [Basic File Transfers](#basic-file-transfers)
6. [Multi-Tenant Operations](#multi-tenant-operations)
7. [REST API Usage](#rest-api-usage)
8. [Administration Guide](#administration-guide)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

### **Part II: Technical Reference (Developers)**
11. [System Architecture & Design](#system-architecture--design)
12. [Core Functional Concepts](#core-functional-concepts)
13. [Java API Reference](#java-api-reference)
14. [Advanced Configuration](#advanced-configuration)

## Introduction

Quorus is an enterprise-grade **route-based distributed file transfer system** designed for internal corporate networks. It uses predefined transfer routes as the primary configuration method, where the central controller manages route definitions between agents. Routes can be triggered automatically based on events, schedules, intervals, batch accumulation, or manual invocation. The system provides secure, reliable, and scalable file transfer capabilities with advanced workflow orchestration and multi-tenant support.

### Route-Based Architecture

At its core, Quorus operates on a **route configuration model**:

- **Routes** define source agent, destination agent, and transfer conditions
- **Controller** manages the central route repository and validates agent availability
- **Agents** monitor locations and execute transfers according to routes
- **Triggers** automatically initiate transfers when conditions are met (file events, schedules, thresholds)
- **Workflows** orchestrate complex multi-step operations across multiple routes

## Key Features

---

### **Secure File Transfers**
*Enterprise-Grade Protocol Support*

Quorus provides comprehensive support for multiple transfer protocols, each optimized for specific enterprise use cases. Understanding the strengths and limitations of each protocol helps organizations make informed decisions about their file transfer architecture.

#### **HTTP/HTTPS Protocol**
*Modern Web-Based Transfers*

**Capabilities**
- OAuth2, Bearer tokens, and custom header authentication
- Range requests for resume capability and partial downloads
- Automatic compression (gzip/deflate) for bandwidth optimization
- Corporate proxy integration with authentication passthrough
- RESTful API integration for programmatic access

**Advantages**
- **Firewall Friendly**: Uses standard ports (80/443) that are typically open
- **Modern Authentication**: Supports OAuth2, JWT tokens, and API keys
- **Cloud Integration**: Native support for AWS S3, Azure Blob, Google Cloud Storage
- **Resume Capability**: Automatic retry and resume for interrupted transfers
- **Compression**: Built-in compression reduces bandwidth usage
- **Monitoring**: Rich HTTP status codes and headers for detailed progress tracking

**Disadvantages**
- **Overhead**: HTTP headers add protocol overhead for small files
- **Stateless**: Each request is independent, requiring authentication per request
- **Limited Directory Operations**: Not optimized for complex directory synchronization
- **Caching Issues**: HTTP caching can interfere with real-time file updates

**Best For**
- API-based data exchanges and microservices integration
- Cloud storage integration (S3, Azure, GCS)
- Web application file uploads/downloads
- RESTful service integration

#### **FTP/FTPS Protocol**
*Traditional File Transfer with Security*

**Capabilities**
- Active and passive connection modes for firewall compatibility
- Automatic binary/ASCII mode detection and conversion
- SSL/TLS encryption (FTPS) for secure data transmission
- Directory synchronization with timestamp comparison
- Bandwidth throttling and connection pooling

**Advantages**
- **Mature Protocol**: Decades of proven reliability and compatibility
- **Efficient for Bulk Transfers**: Optimized for large file transfers
- **Directory Operations**: Native support for directory listing and synchronization
- **Wide Compatibility**: Supported by virtually all systems and devices
- **Simple Authentication**: Username/password or certificate-based
- **Bandwidth Control**: Built-in throttling and QoS capabilities

**Disadvantages**
- **Firewall Complexity**: Requires multiple ports (control + data channels)
- **Security Concerns**: Plain FTP transmits credentials in clear text
- **NAT/Proxy Issues**: Active mode can have problems with NAT and firewalls
- **Limited Metadata**: Basic file attributes only (size, timestamp)
- **No Atomic Operations**: File transfers are not transactional

**Best For**
- Legacy system integration where FTP is already established
- Bulk data transfers between known, trusted systems
- Automated batch processing and ETL operations
- Systems requiring simple, reliable file transfer

#### **SFTP Protocol**
*Secure File Transfer Over SSH*

**Capabilities**
- SSH key-based authentication with passphrase protection
- Built-in compression for bandwidth optimization
- Multiple concurrent streams for parallel transfers
- Unix file permissions and ownership preservation
- Atomic file operations and transactional transfers

**Advantages**
- **Strong Security**: Uses SSH encryption and authentication
- **Single Port**: Only requires port 22, firewall-friendly
- **File Integrity**: Built-in checksums and verification
- **Unix Integration**: Preserves permissions, ownership, and symbolic links
- **Atomic Operations**: Supports transactional file operations
- **Key Management**: Robust public/private key infrastructure

**Disadvantages**
- **SSH Overhead**: Encryption adds CPU overhead for large transfers
- **Complexity**: SSH key management can be complex in large environments
- **Limited Windows Support**: Not natively supported on older Windows systems
- **No Resume**: Standard SFTP doesn't support resume capability
- **Performance**: Can be slower than FTP for very large files

**Best For**
- Secure transfers to Linux/Unix systems
- Environments requiring strong authentication and encryption
- Automated scripts and batch processing
- Systems where SSH is already deployed

#### **SMB Protocol**
*Windows File Sharing*

**Capabilities**
- NTLM/Kerberos authentication with Active Directory integration
- Distributed File System (DFS) support for high availability
- Opportunistic locking and file caching for performance
- Windows file attributes and Access Control Lists (ACLs)
- Network browsing and service discovery

**Advantages**
- **Native Windows Integration**: Seamless integration with Windows environments
- **Active Directory**: Leverages existing AD authentication and authorization
- **File Locking**: Prevents concurrent access conflicts
- **Rich Metadata**: Full Windows file attributes and security descriptors
- **Network Browsing**: Automatic discovery of available shares
- **Performance**: Optimized for Windows-to-Windows transfers

**Disadvantages**
- **Windows-Centric**: Limited support on non-Windows systems
- **Network Chattiness**: Protocol generates significant network traffic
- **Security Complexity**: Requires careful configuration for security
- **Firewall Issues**: Uses multiple ports and can be blocked by firewalls
- **Version Compatibility**: Different SMB versions have compatibility issues

**Best For**
- Windows-based corporate environments
- Active Directory integrated networks
- File sharing between Windows servers and workstations
- Applications requiring Windows file attributes and permissions

#### **Protocol Selection Matrix**

| **Requirement** | **HTTP/HTTPS** | **FTP/FTPS** | **SFTP** | **SMB** |
|-----------------|----------------|--------------|----------|---------|
| **Cloud Integration** | ✅ Excellent | ❌ Limited | ❌ Limited | ❌ Poor |
| **Firewall Friendly** | ✅ Excellent | ⚠️ Moderate | ✅ Excellent | ❌ Poor |
| **Security** | ✅ Strong | ⚠️ Moderate | ✅ Excellent | ⚠️ Moderate |
| **Performance** | ⚠️ Moderate | ✅ Excellent | ⚠️ Moderate | ✅ Good |
| **Resume Capability** | ✅ Yes | ⚠️ Limited | ❌ No | ✅ Yes |
| **Directory Sync** | ❌ Limited | ✅ Excellent | ✅ Good | ✅ Excellent |
| **Cross-Platform** | ✅ Excellent | ✅ Excellent | ✅ Good | ❌ Poor |
| **Enterprise Auth** | ✅ OAuth2/SAML | ⚠️ Basic | ✅ SSH Keys | ✅ AD/Kerberos |

#### **Security Architecture**

Quorus implements a comprehensive security architecture based on industry best practices, military-grade encryption, and enterprise compliance requirements. Security is built into every layer of the system, from network protocols to data storage.

#### **Encryption Standards: Military-Grade Data Protection**

### TLS 1.3: Next-Generation Transport Security

Transport Layer Security 1.3 represents the latest evolution in secure communications, providing significant improvements over previous versions:

**Technical Improvements in TLS 1.3:**
- **Reduced Handshake**: 1-RTT (Round Trip Time) vs. 2-RTT in TLS 1.2
- **Perfect Forward Secrecy**: Mandatory for all cipher suites
- **Simplified Cipher Suites**: Removed vulnerable legacy algorithms
- **0-RTT Resumption**: Near-instant reconnection for repeat connections

**Cipher Suites Supported:**

**TLS 1.3 Cipher Suites:**
- **TLS_AES_256_GCM_SHA384** - Recommended for high security
- **TLS_CHACHA20_POLY1305_SHA256** - Optimized for mobile/IoT
- **TLS_AES_128_GCM_SHA256** - Balanced performance/security

**Performance Benefits:**
- **Faster Handshakes**: 33% reduction in connection establishment time
- **Lower Latency**: Reduced round trips improve user experience
- **Better Security**: Eliminates known vulnerabilities from TLS 1.2
- **Future-Proof**: Designed to resist quantum computing attacks

### AES-256: Advanced Encryption Standard

AES-256 provides symmetric encryption with 256-bit keys, offering exceptional security for data protection:

**Technical Specifications:**
- **Key Length**: 256 bits (2^256 possible keys)
- **Block Size**: 128 bits
- **Rounds**: 14 encryption rounds
- **Algorithm**: Rijndael cipher, NIST-approved

**Security Strength:**

**Brute Force Attack Resistance:**
- **Time to break with current technology**: > 10^70 years
- **Quantum resistance**: Requires ~2^128 operations (still secure)
- **Government approval**: NSA Suite B approved for TOP SECRET

**Implementation Modes:**

| **Mode** | **Use Case** | **Security Level** |
|----------|--------------|-------------------|
| **GCM** | Authenticated encryption | Highest (integrity + confidentiality) |
| **CBC** | Legacy compatibility | High (confidentiality only) |
| **CTR** | High-performance streaming | High (with separate authentication) |

### RSA/ECDSA: Public Key Cryptography

Quorus supports both RSA and Elliptic Curve Digital Signature Algorithm (ECDSA) for asymmetric cryptography:

**RSA Implementation:**
- **Key Sizes**: 2048-bit (minimum), 3072-bit (recommended), 4096-bit (high security)
- **Padding**: OAEP (Optimal Asymmetric Encryption Padding) for encryption
- **Signatures**: PSS (Probabilistic Signature Scheme) for digital signatures
- **Use Cases**: Legacy system compatibility, certificate authorities

**ECDSA Implementation:**
- **Curves**: P-256, P-384, P-521 (NIST curves)
- **Performance**: 10x faster than equivalent RSA
- **Key Size**: 256-bit ECDSA ≈ 3072-bit RSA security
- **Use Cases**: Modern systems, mobile devices, IoT

**Security Comparison:**

| **Algorithm** | **Key Size** | **Security Level** | **Performance** | **Quantum Resistance** |
|---------------|--------------|-------------------|-----------------|----------------------|
| **RSA-2048** | 2048 bits | 112-bit equivalent | Baseline | Vulnerable |
| **RSA-3072** | 3072 bits | 128-bit equivalent | 3x slower | Vulnerable |
| **ECDSA P-256** | 256 bits | 128-bit equivalent | 10x faster | Vulnerable |
| **ECDSA P-384** | 384 bits | 192-bit equivalent | 8x faster | Partially resistant |

**Perfect Forward Secrecy (PFS): Future-Proof Security**

Perfect Forward Secrecy ensures that session keys cannot be compromised even if long-term private keys are stolen:

**How PFS Works:**

**Traditional Key Exchange (Vulnerable):**
1. Client encrypts session key with server's public key
2. Server decrypts with private key
3. If private key is stolen later, all past sessions can be decrypted

**PFS Key Exchange (Secure):**
1. Client and server generate ephemeral key pairs
2. Perform Diffie-Hellman key exchange
3. Ephemeral keys are discarded after session
4. Even if long-term keys are stolen, past sessions remain secure

**PFS Algorithms Supported:**
- **ECDHE** (Elliptic Curve Diffie-Hellman Ephemeral): Recommended
- **DHE** (Diffie-Hellman Ephemeral): Legacy compatibility
- **X25519**: High-performance curve for modern systems

**Business Impact of PFS:**
- **Regulatory Compliance**: Required by many government standards
- **Data Breach Protection**: Limits damage from key compromise
- **Long-term Security**: Protects historical data indefinitely
- **Customer Trust**: Demonstrates commitment to security best practices

#### **Authentication Methods: Multi-Layered Identity Verification**

**Multi-Factor Authentication (MFA): Defense in Depth**

Multi-Factor Authentication adds additional security layers beyond simple username/password combinations:

**Supported MFA Protocols:**

**RADIUS (Remote Authentication Dial-In User Service)**
- **Use Case**: Centralized authentication for network access
- **Integration**: Corporate VPN, wireless networks, and dial-up systems
- **Features**: Accounting, authorization, and authentication (AAA)
- **Deployment**: Integrates with existing RADIUS infrastructure

**TACACS+ (Terminal Access Controller Access-Control System Plus)**
- **Use Case**: Device administration and privileged access
- **Advantages**: Separates authentication, authorization, and accounting
- **Security**: Encrypts entire authentication packet (vs. RADIUS password-only)
- **Cisco Integration**: Native support for Cisco network equipment

**Hardware Token Support:**

| **Token Type** | **Examples** | **Security Level** | **Use Cases** |
|----------------|--------------|-------------------|---------------|
| **RSA SecurID** | Software/hardware tokens | High | Executive access, financial systems |
| **YubiKey** | USB/NFC hardware keys | Very High | Developer access, admin operations |
| **Smart Cards** | PIV/CAC cards | Very High | Government, defense contractors |
| **Mobile Apps** | Google Authenticator, Authy | Medium-High | General enterprise use |

**MFA Flow Example:**

**1. User Authentication Request**
- Username/Password (Something you know)
- Hardware Token (Something you have)
- Biometric (Something you are)

**2. RADIUS/TACACS+ Verification**
- Primary credentials validated
- Secondary factor requested
- Token/biometric verified

**3. Access Granted**
- Session established
- Permissions applied
- Activity logged

**Certificate-Based Authentication: Machine Identity**

X.509 certificates provide strong authentication for both users and systems:

**Certificate Types Supported:**
- **User Certificates**: Personal authentication certificates
- **Machine Certificates**: Server and client system authentication
- **Code Signing**: Software integrity verification
- **Email Certificates**: S/MIME email encryption and signing

**Certificate Authority (CA) Integration:**

**Enterprise PKI Hierarchy:**
- **Root CA** (Offline, High Security)
  - **Intermediate CA** (Online, Policy Enforcement)
    - **User Certificates** (Employee authentication)
    - **Server Certificates** (System authentication)
    - **Application Certificates** (Service authentication)
  - **External CA Trust** (Third-party certificates)

**Certificate Lifecycle Management:**
- **Enrollment**: Automated certificate request and issuance
- **Renewal**: Automatic renewal before expiration
- **Revocation**: Real-time certificate revocation list (CRL) checking
- **Validation**: OCSP (Online Certificate Status Protocol) support

**API Keys: Programmatic Authentication**

Secure token-based authentication designed for automated systems and integrations:

**API Key Types:**

| **Type** | **Format** | **Security Features** | **Use Cases** |
|----------|------------|----------------------|---------------|
| **Bearer Tokens** | JWT (JSON Web Tokens) | Signed, time-limited | REST API access |
| **API Keys** | Random strings | Rate limiting, scoping | Service integration |
| **OAuth 2.0** | Access/refresh tokens | Delegated authorization | Third-party apps |
| **HMAC Signatures** | Cryptographic signatures | Request integrity | High-security APIs |

**JWT Token Structure:**
```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "api-service-account",
    "iss": "quorus-auth-server",
    "aud": "quorus-api",
    "exp": 1640995200,
    "iat": 1640908800,
    "scope": ["transfer:read", "workflow:execute"]
  },
  "signature": "cryptographic_signature"
}
```

**API Security Features:**
- **Rate Limiting**: Prevents abuse and DoS attacks
- **Scope Limitation**: Restricts access to specific resources
- **Time-based Expiration**: Automatic token invalidation
- **Request Signing**: HMAC verification for critical operations

**Biometric Integration: Advanced Identity Verification**

Modern biometric authentication for high-security environments:

**Supported Biometric Methods:**
- **Fingerprint Recognition**: USB fingerprint scanners, laptop sensors
- **Smart Card Integration**: PIV/CAC cards with biometric data
- **Windows Hello**: Native Windows biometric authentication
- **FIDO2/WebAuthn**: Modern web-based biometric authentication

**Biometric Security Advantages:**
- **Non-repudiation**: Difficult to forge or share
- **Convenience**: No passwords to remember or lose
- **Speed**: Faster than typing passwords
- **Audit Trail**: Clear identity verification for compliance

**Implementation Considerations:**
- **Privacy**: Biometric templates stored locally, not centrally
- **Fallback**: Alternative authentication methods for system failures
- **Accessibility**: Support for users unable to use biometric methods
- **Standards Compliance**: FIDO Alliance and W3C WebAuthn standards

---

#### **Compliance Features: Regulatory and Standards Adherence**

**FIPS 140-2: Federal Information Processing Standard**

FIPS 140-2 is a U.S. government standard for cryptographic modules used in security systems:

**FIPS 140-2 Security Levels:**

| **Level** | **Requirements** | **Quorus Implementation** | **Use Cases** |
|-----------|------------------|---------------------------|---------------|
| **Level 1** | Basic cryptographic security | Software cryptography | General business use |
| **Level 2** | Tamper-evident hardware | Hardware security modules | Financial services |
| **Level 3** | Tamper-resistant hardware | Certified HSM integration | Government agencies |
| **Level 4** | Tamper-responsive hardware | Military-grade HSMs | Defense contractors |

**FIPS 140-2 Validated Algorithms:**
- **AES**: Advanced Encryption Standard (all key sizes)
- **SHA**: Secure Hash Algorithm (SHA-256, SHA-384, SHA-512)
- **RSA**: Rivest-Shamir-Adleman (2048-bit minimum)
- **ECDSA**: Elliptic Curve Digital Signature Algorithm
- **HMAC**: Hash-based Message Authentication Code

**Compliance Benefits:**
- **Government Contracts**: Required for federal government systems
- **Regulatory Approval**: Satisfies many industry regulations
- **International Recognition**: Accepted globally for security validation
- **Risk Mitigation**: Reduces liability through validated cryptography

**Common Criteria: International Security Evaluation**

Common Criteria (ISO/IEC 15408) provides a framework for security evaluation of IT products:

**Evaluation Assurance Levels (EAL):**

**EAL1: Functionally Tested**
- Basic security testing
- Suitable for low-risk environments

**EAL2: Structurally Tested (Quorus Target)**
- Developer testing and documentation
- Independent vulnerability assessment
- Suitable for commercial environments

**EAL3: Methodically Tested and Checked**
- Methodical testing and checking
- Development environment controls
- Suitable for security-conscious environments

**EAL4+: Higher Assurance Levels**
- Methodically designed, tested, and reviewed
- Formal security policy model
- Suitable for high-risk environments

**Quorus Common Criteria Profile:**
- **Protection Profile**: Network Infrastructure Device
- **Security Targets**: File transfer and workflow management
- **Evaluated Configuration**: Hardened deployment guidelines
- **Vulnerability Assessment**: Independent security testing

**SOX Compliance: Financial Data Controls**

Sarbanes-Oxley Act compliance for financial data handling and audit requirements:

**SOX Section 404: Internal Controls**

**Financial Data Lifecycle:**

**1. Data Creation**
- User authentication and authorization
- Digital signatures for non-repudiation
- Timestamp validation

**2. Data Processing**
- Workflow approval processes
- Segregation of duties enforcement
- Change management controls

**3. Data Storage**
- Encryption at rest (AES-256)
- Access logging and monitoring
- Retention policy enforcement

**4. Data Archival**
- Immutable audit trails
- Long-term storage integrity
- Compliance reporting

**SOX Control Implementation:**

| **Control Type** | **Implementation** | **Audit Evidence** |
|------------------|-------------------|-------------------|
| **Access Controls** | Role-based permissions | User access reports |
| **Change Management** | Workflow versioning | Change approval logs |
| **Data Integrity** | Cryptographic checksums | Integrity verification reports |
| **Audit Trails** | Immutable logging | Complete activity logs |

**GDPR Compliance: Data Protection and Privacy**

General Data Protection Regulation compliance for European data protection:

**GDPR Principles Implementation:**
- **Lawfulness**: Documented legal basis for data processing
- **Purpose Limitation**: Data used only for specified purposes
- **Data Minimization**: Only necessary data is processed
- **Accuracy**: Data correction and update mechanisms
- **Storage Limitation**: Automated data retention policies
- **Security**: Appropriate technical and organizational measures

**Data Subject Rights Support:**

| **Right** | **Implementation** | **Response Time** |
|-----------|-------------------|-------------------|
| **Right to Access** | Data export functionality | Within 30 days |
| **Right to Rectification** | Data correction workflows | Without undue delay |
| **Right to Erasure** | Secure data deletion | Within 30 days |
| **Right to Portability** | Structured data export | Within 30 days |
| **Right to Object** | Processing restriction flags | Immediately |

**Privacy by Design Features:**
- **Data Encryption**: All personal data encrypted in transit and at rest
- **Pseudonymization**: Personal identifiers replaced with pseudonyms
- **Access Logging**: Complete audit trail of data access
- **Consent Management**: Granular consent tracking and withdrawal
- **Data Breach Notification**: Automated breach detection and reporting

**Additional Compliance Frameworks:**

**HIPAA (Healthcare)**
- **Administrative Safeguards**: Access management and training
- **Physical Safeguards**: Facility access controls and workstation security
- **Technical Safeguards**: Encryption, audit controls, and integrity controls

**PCI DSS (Payment Card Industry)**
- **Network Security**: Firewall configuration and secure transmission
- **Data Protection**: Encryption of cardholder data
- **Access Control**: Restricted access on business need-to-know basis
- **Monitoring**: Regular monitoring and testing of networks

**ISO 27001 (Information Security Management)**
- **Risk Assessment**: Systematic identification and treatment of risks
- **Security Controls**: Implementation of appropriate security measures
- **Continuous Improvement**: Regular review and improvement of security posture
- **Certification**: Third-party validation of security management system

### **YAML Workflows**
*Declarative Orchestration Engine*

Transform complex file transfer operations into maintainable, version-controlled workflow definitions using YAML as the configuration language.

#### **Why YAML? The Strategic Choice**

**Human-Readable Infrastructure as Code**

YAML (YAML Ain't Markup Language) was chosen as Quorus's configuration format after careful evaluation of alternatives including JSON, XML, and proprietary formats. This decision reflects modern DevOps principles and enterprise operational requirements.

**Comparison with Alternative Formats**

| **Aspect** | **YAML** | **JSON** | **XML** | **Proprietary** |
|------------|----------|----------|---------|-----------------|
| **Human Readability** | ✅ Excellent | ⚠️ Moderate | ❌ Poor | ❌ Varies |
| **Comments Support** | ✅ Native | ❌ None | ✅ Yes | ❌ Usually No |
| **Version Control** | ✅ Excellent | ⚠️ Moderate | ❌ Poor | ❌ Poor |
| **Learning Curve** | ✅ Low | ⚠️ Moderate | ❌ High | ❌ High |
| **Tool Ecosystem** | ✅ Rich | ✅ Rich | ⚠️ Moderate | ❌ Limited |
| **Multi-line Strings** | ✅ Native | ❌ Escaped | ⚠️ CDATA | ❌ Varies |
| **Data Types** | ✅ Rich | ⚠️ Limited | ⚠️ String-based | ❌ Varies |

**Key Advantages of YAML for Enterprise Workflows**

1. **Self-Documenting Code**
   ```yaml
   # This workflow processes daily sales reports
   # Runs every morning at 6 AM EST
   metadata:
     name: "Daily Sales Report Processing"
     description: "Automated processing of overnight sales data with validation and distribution"
   ```

2. **Minimal Syntax Overhead**
   - No closing tags (unlike XML)
   - No excessive brackets and quotes (unlike JSON)
   - Indentation-based structure mirrors logical hierarchy

3. **Native Multi-line Support**
   ```yaml
   description: |
     This workflow handles the complete ETL pipeline for customer data:
     1. Extract from multiple source systems
     2. Transform and validate data quality
     3. Load into the data warehouse
     4. Generate compliance reports
   ```

4. **Rich Data Type Support**
   ```yaml
   variables:
     timeout: 300              # Integer
     retry_enabled: true       # Boolean
     file_patterns:            # Array
       - "*.csv"
       - "*.xlsx"
     database_config:          # Object
       host: "db.company.com"
       port: 5432
   ```

**Enterprise Operational Benefits**

**Version Control Integration**
- **Meaningful Diffs**: YAML changes are easily readable in Git/SVN diffs
- **Merge Conflicts**: Indentation-based structure reduces merge conflicts
- **Code Reviews**: Business users can participate in workflow reviews
- **Branching Strategy**: Different environments can have separate workflow branches

**DevOps and GitOps Compatibility**
- **Infrastructure as Code**: Workflows are treated as code artifacts
- **CI/CD Integration**: Automated testing and deployment of workflow changes
- **Configuration Management**: Integration with Ansible, Kubernetes, and other YAML-based tools
- **Policy as Code**: Compliance rules can be embedded in workflow definitions

**Business User Accessibility**
- **No Programming Background Required**: Business analysts can create and modify workflows
- **Self-Service Operations**: Reduce dependency on IT for routine workflow changes
- **Documentation Integration**: Comments and descriptions are part of the configuration
- **Training Efficiency**: Faster onboarding for new team members

#### **YAML Best Practices in Quorus**

**Standardized Schema Validation**
```yaml
metadata:
  name: "Customer Data Pipeline"           # Required: Human-readable identifier
  version: "2.1.0"                        # Required: Semantic versioning
  description: "Processes customer data from multiple sources for business intelligence and analytics"   # Required: Business purpose (min 10 chars)
  type: "data-pipeline-workflow"           # Required: Workflow classification
  author: "data-team@company.com"          # Required: Ownership
  created: "2025-08-21"                    # Required: Creation date
  tags: ["customer", "etl", "daily", "business-intelligence"]       # Required: Classification tags
```

**Hierarchical Organization**
```yaml
spec:
  variables:                    # Global configuration
    source_db: "prod-db"

  execution:                    # Execution parameters
    parallelism: 3

  transferGroups:               # Workflow stages
    - name: extract-data        # Logical grouping
      transfers:                # Individual operations
        - name: extract-customers
```

**Environment-Specific Configuration**
```yaml
variables:
  # Environment-specific values
  database_host: "{{env.DB_HOST}}"
  api_endpoint: "{{env.API_URL}}"

  # Environment detection
  environment: "{{env.ENVIRONMENT | default('development')}}"
```

#### **Core Capabilities**

| **Feature** | **Description** | **Benefits** |
|-------------|-----------------|--------------|
| **Declarative Syntax** | Human-readable YAML definitions | No programming knowledge required |
| **Complex Dependencies** | Sophisticated dependency graphs with parallel execution | Automatic optimization and conditional logic |
| **Variable Substitution** | Templating with dates, environment variables, dynamic values | Workflow reuse across environments |
| **Error Handling** | Configurable strategies: fail-fast, continue-on-error, retry policies | Automatic notification and escalation |

#### **Execution Modes**

```yaml
execution:
  mode: normal        # Full execution with actual transfers
  # mode: dry-run     # Validation and planning only
  # mode: virtual     # Simulation for testing and training
```

**Business Value**
- Reduce operational complexity and improve reliability
- Enable self-service file transfer operations for business users
- Version-controlled, auditable workflow definitions
- Lower total cost of ownership through reduced training and maintenance

---

### **Multi-Tenant Architecture**
*Enterprise Isolation and Governance*

Comprehensive multi-tenancy designed for large organizations with complex hierarchical structures:

#### **Tenant Hierarchy**
```
Enterprise
├── Company A
│   ├── Finance Department
│   └── IT Department
└── Company B
    ├── Sales Team
    └── Marketing Team
```

#### **Isolation Strategies**

| **Strategy** | **Infrastructure** | **Use Cases** |
|--------------|-------------------|---------------|
| **Logical Isolation** | Shared with namespace separation | Standard business units |
| **Physical Isolation** | Dedicated infrastructure | Sensitive/regulated data |
| **Hybrid Isolation** | Mixed based on sensitivity | Flexible security requirements |

#### **Resource Management**

| **Resource Type** | **Quota Controls** | **Features** |
|-------------------|-------------------|--------------|
| **Concurrent Transfers** | Per-tenant limits | Burst capacity support |
| **Bandwidth Allocation** | QoS-based throttling | Time-based quotas |
| **Storage Limits** | Hierarchical quotas | Automatic cleanup policies |
| **API Rate Limits** | Request throttling | Fair usage enforcement |

**Governance Benefits**
- Centralized management with distributed control
- Comprehensive audit trails and compliance reporting
- Cross-tenant data sharing with approval workflows

---

### **High Availability**
*Fault-Tolerant Distributed Architecture*

Enterprise-grade availability designed to meet stringent SLA requirements through sophisticated clustering and consensus mechanisms.

#### **Understanding Distributed Systems and Clustering**

**What is Clustering?**

Clustering is the practice of connecting multiple independent servers (nodes) to work together as a single, unified system. In the context of file transfer systems, clustering provides redundancy, load distribution, and fault tolerance that single-server deployments cannot achieve.

**Why Clustering is Essential for Enterprise File Transfers**

**Single Points of Failure in Traditional Architectures**

Traditional single-server architecture creates critical vulnerabilities:

**Traditional Single-Server Architecture:**
- File Transfer Server (Single Point of Failure)
  - All operations stop if this fails
  - Connected to Storage

**Problems with Single-Server Deployments:**
- **Complete Service Outage**: Server failure means zero availability
- **Maintenance Downtime**: Updates require service interruption
- **Performance Bottlenecks**: Single server limits throughput
- **Data Loss Risk**: Hardware failure can result in data loss
- **Scalability Limits**: Cannot handle growing workloads

**Clustered Architecture Benefits**

**Quorus Clustered Architecture:**
- **Node 1 (Leader)** ↔ **Node 2 (Follower)** ↔ **Node 3 (Follower)**
- All nodes connected to **Shared Storage**
- Automatic failover between nodes
- Load distribution across cluster

**Enterprise Requirements Driving Clustering:**

| **Business Need** | **Clustering Solution** | **Impact** |
|-------------------|------------------------|------------|
| **24/7 Operations** | Automatic failover | Zero planned downtime |
| **Global Presence** | Geographic distribution | Local performance worldwide |
| **Regulatory Compliance** | Data replication | Disaster recovery compliance |
| **Performance SLAs** | Load distribution | Consistent response times |
| **Cost Optimization** | Resource sharing | Better hardware utilization |

#### **Distributed Consensus: The Foundation of Reliability**

**The Consensus Problem**

In distributed systems, multiple nodes must agree on the state of the system. This is challenging because:
- **Network Partitions**: Nodes may lose communication
- **Node Failures**: Servers can crash or become unresponsive
- **Timing Issues**: Messages can be delayed or arrive out of order
- **Byzantine Failures**: Nodes may behave unpredictably

**Why Consensus Matters for File Transfers**
- **Workflow State**: All nodes must agree on workflow execution status
- **Resource Allocation**: Coordinated assignment of transfer tasks
- **Configuration Changes**: Consistent system configuration across nodes
- **Leader Election**: Determining which node coordinates operations

#### **Raft Consensus Algorithm: Proven Reliability**

**What is Raft?**

Raft is a consensus algorithm designed to be understandable and practical for real-world systems. It ensures that a cluster of servers maintains consistent state even in the presence of failures.

**Raft's Core Principles**

1. **Strong Leader**: One node acts as the leader, simplifying decision-making
2. **Leader Election**: Automatic selection of a new leader when needed
3. **Log Replication**: All changes are recorded and replicated to followers
4. **Safety**: Guarantees that committed entries are never lost

**Raft Consensus Process**

**Normal Operation (3-node cluster):**

1. **Leader Node A** sends commands to **Follower Node B** and **Follower Node C**
2. Each node maintains identical log entries:
   - Command: Start Transfer X
   - Status: Committed
3. All nodes stay synchronized through continuous replication

**Leader Election Process**

When a leader fails, Raft automatically elects a new leader:

**Step 1: Leader Failure Detected**
- Leader Node A fails (❌)
- Follower Node B and Node C detect the failure
- Election timeout triggers

**Step 2: Candidate Election**
- Node B becomes a candidate
- Node B requests votes: "Vote for me!"
- Node C responds: "Yes, I'll vote!"
- Majority vote achieved

**Step 3: New Leader Established**
- Node B becomes the new leader
- Node C remains as follower
- Normal operations resume

**Fault Tolerance Mathematics**

Raft can tolerate **(N-1)/2** node failures where N is the total number of nodes:

| **Cluster Size** | **Failures Tolerated** | **Minimum Nodes for Operation** |
|------------------|------------------------|--------------------------------|
| **3 nodes** | 1 failure | 2 nodes (majority) |
| **5 nodes** | 2 failures | 3 nodes (majority) |
| **7 nodes** | 3 failures | 4 nodes (majority) |

**Why Odd Numbers?**
- **Majority Requirement**: Raft requires a majority of nodes to make decisions
- **Split-Brain Prevention**: Odd numbers prevent equal partitions
- **Resource Efficiency**: Minimal nodes needed for fault tolerance

#### **Leader Election: Coordinated Decision Making**

**The Role of the Leader**

The leader node in a Raft cluster has specific responsibilities:
- **Client Requests**: All client requests are handled by the leader
- **Log Replication**: Leader replicates all changes to followers
- **Heartbeats**: Regular communication to maintain leadership
- **Decision Making**: Coordinates all cluster-wide decisions

**Election Triggers**

Leader election occurs when:
1. **Initial Startup**: No leader exists when cluster starts
2. **Leader Failure**: Current leader becomes unresponsive
3. **Network Partition**: Leader loses contact with majority
4. **Planned Maintenance**: Graceful leader transfer

**Election Timeline**

**Typical Leader Election Timeline:**
- **0ms**: Leader failure detected
- **150ms**: Election timeout expires, candidate starts election
- **200ms**: Votes collected from majority, new leader elected
- **250ms**: New leader sends heartbeats, cluster operational

**Sub-Second Recovery**
- **Detection**: 50-150ms to detect leader failure
- **Election**: 50-100ms to complete election process
- **Stabilization**: 50-100ms for new leader to establish authority
- **Total**: Typically under 300ms for complete failover

#### **Advanced Clustering Features**

**Log Replication and Data Consistency**

Every operation in Quorus is recorded in a replicated log that ensures consistency across all nodes:

**Log Replication Process:**
1. **Client Request**: Client sends "Start Transfer" to Leader
2. **Leader Logs Entry**: Leader creates log entry
3. **Replicate to Followers**: Leader replicates entry to all followers
4. **Commit Entry**: Once majority confirms, operation is committed

**Consistency Guarantees**
- **Strong Consistency**: All nodes see the same data at the same time
- **Linearizability**: Operations appear to execute atomically
- **Durability**: Committed operations survive node failures
- **ACID Properties**: Database-like guarantees for workflow operations

**Network Partition Handling**

Quorus handles network partitions gracefully using the "majority wins" principle:

**Network Partition Scenario (5-node cluster):**

**Partition A (Majority - 3 nodes):**
- Node 1 (Leader), Node 2, Node 3
- Continues normal operations
- Can elect new leader if needed
- Maintains write capabilities

**Partition B (Minority - 2 nodes):**
- Node 4, Node 5
- Becomes read-only
- Cannot elect leader
- Waits for partition to heal

**Network Split Resolution:**
- When network heals, partitions automatically rejoin
- Minority partition synchronizes with majority
- Normal operations resume across all nodes

**Split-Brain Prevention**
- **Quorum Requirement**: Operations require majority approval
- **Read-Only Mode**: Minority partitions become read-only
- **Automatic Recovery**: Partitions automatically rejoin when network heals
- **Data Integrity**: No conflicting operations can occur

#### **Geographic Distribution and Disaster Recovery**

**Multi-Availability Zone Deployment**

Quorus supports deployment across multiple availability zones for maximum resilience:

**Geographic Distribution (Region: US-East):**

**Availability Zone 1a:**
- Node 1 (Leader)
- Storage Replica

**Availability Zone 1b:**
- Node 2 (Follower)
- Storage Replica

**Availability Zone 1c:**
- Node 3 (Follower)
- Storage Replica

**Benefits:**
- Survives entire availability zone failures
- Automatic failover between zones
- Data replicated across geographic locations
- Meets disaster recovery requirements

**Disaster Recovery Scenarios**

| **Failure Type** | **Impact** | **Recovery Time** | **Data Loss** |
|------------------|------------|-------------------|---------------|
| **Single Node** | None (automatic failover) | < 1 second | None |
| **Availability Zone** | Temporary performance impact | < 5 seconds | None |
| **Entire Region** | Service interruption | < 5 minutes | None (with backup) |
| **Multiple Regions** | Extended outage | Manual intervention | Minimal (last backup) |

**Cross-Region Replication**

```yaml
disaster_recovery:
  primary_region: "us-east-1"
  backup_regions:
    - region: "us-west-2"
      replication_mode: "async"
      rpo: "15 minutes"        # Recovery Point Objective
      rto: "5 minutes"         # Recovery Time Objective
    - region: "eu-west-1"
      replication_mode: "sync"
      rpo: "0 seconds"
      rto: "30 seconds"
```

#### **Clustering & Consensus Technology Stack**

| **Component** | **Technology** | **Fault Tolerance** | **Recovery Time** |
|---------------|----------------|-------------------|-------------------|
| **Raft Consensus** | Distributed consensus algorithm | Tolerates (N-1)/2 node failures | < 300ms |
| **Leader Election** | Automatic failover with voting | Sub-second leader selection | < 200ms |
| **Log Replication** | Append-only distributed log | Majority replication required | Real-time |
| **Geographic Distribution** | Multi-AZ deployment | Entire data center failure recovery | < 5 minutes |
| **Network Partitions** | Quorum-based decisions | Minority partitions read-only | Automatic |
| **Data Consistency** | Strong consistency model | ACID guarantees | Immediate |

#### **Operational Excellence**

| **Feature** | **Capability** | **Business Impact** |
|-------------|----------------|-------------------|
| **Zero-Downtime Updates** | Rolling deployments | No service interruption |
| **Split-Brain Prevention** | Quorum-based decisions | Data consistency guarantee |
| **Virtual IP Failover** | Seamless client redirection | Transparent failover |

#### **SLA Guarantees**
```
99.9% Uptime SLA
├── Automatic recovery from common failures
├── Predictive failure detection
└── Comprehensive monitoring and alerting
```

---

### **Progress Tracking**
*Real-Time Visibility and Analytics*

Comprehensive monitoring and observability for all transfer operations:

#### **Monitoring Levels**

| **Level** | **Metrics** | **Update Frequency** |
|-----------|-------------|-------------------|
| **Transfer Level** | Bytes transferred, rates, completion % | Sub-second updates |
| **Workflow Level** | Stage progress, overall completion | Real-time |
| **System Level** | Cluster health, resource utilization | Continuous |

#### **Analytics & Intelligence**

| **Feature** | **Technology** | **Business Value** |
|-------------|----------------|-------------------|
| **Predictive ETA** | ML-enhanced calculations | Accurate completion estimates |
| **Anomaly Detection** | Pattern recognition | Proactive issue identification |
| **Performance Insights** | Network analysis | Optimization recommendations |
| **Historical Trends** | Long-term analytics | Capacity planning support |

#### **Integration Capabilities**
```yaml
monitoring:
  dashboards: [Grafana, Custom Web UI]
  metrics: [Prometheus, InfluxDB]
  alerting: [PagerDuty, Slack, Email]
  siem: [Splunk, Elastic, QRadar]
```

**Operational Benefits**
- Proactive issue detection and resolution
- Performance optimization recommendations
- Comprehensive reporting for stakeholders

---

### **Enterprise Integration**
*Seamless Corporate Infrastructure Integration*

Deep integration with existing enterprise infrastructure and processes:

#### **Identity & Access Management**

| **Integration Type** | **Supported Systems** | **Features** |
|---------------------|----------------------|--------------|
| **Directory Services** | Active Directory, LDAP | Automatic user provisioning, group-based access |
| **Single Sign-On** | SAML, OAuth2, OpenID Connect, Kerberos | One-time authentication, seamless access |
| **Multi-Factor Auth** | RADIUS, TACACS+, RSA SecurID | Enhanced security compliance |

#### **Security & Compliance**

| **Framework** | **Capabilities** | **Automation** |
|---------------|------------------|----------------|
| **SOX** | Financial controls and audit trails | Automated compliance reporting |
| **GDPR** | Data privacy and protection | Automatic data classification |
| **HIPAA** | Healthcare data security | Encrypted storage and transmission |
| **PCI-DSS** | Payment card data protection | Secure data handling workflows |

#### **Infrastructure Integration**

| **Component** | **Integration Points** | **Benefits** |
|---------------|----------------------|--------------|
| **PKI Infrastructure** | Certificate management, key rotation | Automated security operations |
| **Network Security** | Firewall rules, proxy configuration | Seamless network integration |
| **Monitoring Systems** | Prometheus, Grafana, Splunk, SIEM | Unified observability |
| **API Ecosystem** | RESTful APIs, OpenAPI documentation | Custom application integration |

**Integration Benefits**
- Leverage existing enterprise investments
- Maintain consistent security and compliance posture
- Provide familiar user experience across corporate applications

### System Architecture

```mermaid
graph TB
    subgraph "Clients"
        UI[Web UI / CLI]
    end
    
    UI --> API[Quorus API]
    
    subgraph "Controller Cluster"
        Controller[Controller 1<br/>Leader]
        Controller2[Controller 2]
        Controller3[Controller 3]
        Controller -.->|Raft| Controller2
        Controller -.->|Raft| Controller3
        Controller2 -.->|Raft| Controller3
    end
    
    API --> Controller
    
    subgraph "Agent Fleet"
        Agent1[Transfer Agent 1]
        Agent2[Transfer Agent 2]
        AgentN[Transfer Agent n]
    end
    
    Controller --> Agent1
    Controller --> Agent2
    Controller --> AgentN
    
    subgraph "Storage"
        Storage1[Corporate Storage]
        Storage2[Department Shares]
        StorageN[Data Warehouse]
    end
    
    Agent1 --> Storage1
    Agent2 --> Storage2
    AgentN --> StorageN
```

## Getting Started

### Quick Start Guide

Welcome to Quorus! This section will help you get started with file transfers and workflow creation using our user-friendly YAML configuration format.

#### What You Need

**For Basic File Transfers:**
- Access to the Quorus web interface or CLI
- Source and destination locations
- Appropriate permissions for file access

**For YAML Workflows:**
- A text editor (VS Code, Notepad++, or any YAML-aware editor)
- Basic understanding of YAML syntax (we'll guide you through this)
- Knowledge of your file transfer requirements

#### Your First File Transfer

**Option 1: Using the Web Interface**
1. **Login** to the Quorus web interface
2. **Navigate** to "New Transfer"
3. **Configure** source and destination
4. **Select** transfer protocol (HTTP, FTP, SFTP, or SMB)
5. **Start** the transfer and monitor progress

**Option 2: Using YAML Workflow**
Create a simple workflow file:

```yaml
metadata:
  name: "My First Transfer"
  version: "1.0.0"
  description: "Simple file transfer from server to local storage for getting started with Quorus"
  type: "transfer-workflow"
  author: "your-email@company.com"
  created: "2025-08-21"
  tags: ["getting-started", "simple-transfer", "tutorial"]

spec:
  execution:
    parallelism: 1

  transferGroups:
    - name: simple-transfer
      description: "Transfer a single file"
      transfers:
        - name: download-report
          source: "https://reports.company.com/daily-report.csv"
          destination: "/local/reports/daily-report.csv"
          protocol: https
```

#### Next Steps

1. **Configure Routes** - Set up predefined transfer routes between agents
2. **Explore YAML Workflows** - Learn how to create more complex workflows
3. **Try Different Protocols** - Experiment with FTP, SFTP, and SMB transfers
4. **Set Up Monitoring** - Configure progress tracking and notifications
5. **Review Security** - Understand authentication and encryption options

### Route Configuration Guide

#### What are Routes?

Routes are predefined transfer paths between two agents in your Quorus deployment. Think of them as automated highways for your files - once configured, they automatically move files from Agent A to Agent B based on the conditions you specify.

**Key Concepts:**
- **Source Agent**: Monitors a specific location for files (folder, FTP server, etc.)
- **Destination Agent**: Receives and stores files in a target location
- **Trigger**: The condition that initiates the transfer (new file, schedule, batch count, etc.)
- **Controller Validation**: At startup, the controller verifies both agents are active before activating the route

#### Your First Route Configuration

**Option 1: Using YAML Configuration**

Create a route configuration file (`routes/crm-to-warehouse.yaml`):

```yaml
apiVersion: v1
kind: RouteConfiguration
metadata:
  name: crm-to-warehouse
  description: "Automatically transfer CRM exports to data warehouse"
  tags: ["crm", "data-warehouse", "automated"]

spec:
  # Source: Where files originate
  source:
    agent: agent-crm-001
    location: /corporate-data/crm/export/
    description: "CRM system export folder"

  # Destination: Where files go
  destination:
    agent: agent-warehouse-001
    location: /corporate-data/warehouse/import/
    description: "Data warehouse import folder"

  # Trigger: When to transfer
  trigger:
    type: EVENT_BASED
    events:
      - FILE_CREATED      # Transfer when new file appears
      - FILE_MODIFIED     # Transfer when file is updated
    filters:
      pattern: "customer-export-*.json"  # Only match specific files
      minSize: 1KB                        # Ignore tiny files

  # Options: How to transfer
  options:
    validation:
      checksumAlgorithm: SHA-256
      verifyIntegrity: true
    retry:
      maxAttempts: 3
      backoff: EXPONENTIAL
    monitoring:
      alertOnFailure: true
      logLevel: INFO
```

**Option 2: Using the Web Interface**

1. **Navigate** to "Route Management" in the Quorus web interface
2. **Click** "Create New Route"
3. **Configure Source**:
   - Select source agent from dropdown
   - Enter source location path
4. **Configure Destination**:
   - Select destination agent from dropdown
   - Enter destination location path
5. **Select Trigger Type**:
   - Event-Based (file appearance)
   - Time-Based (scheduled)
   - Interval-Based (periodic)
   - Batch-Based (file count threshold)
   - Manual (on-demand)
6. **Set Options** for validation, retry, and monitoring
7. **Save and Activate** the route

#### Route Trigger Types

**1. Event-Based Triggers** (Most Common)
```yaml
trigger:
  type: EVENT_BASED
  events: [FILE_CREATED, FILE_MODIFIED]
  filters:
    pattern: "*.csv"
```
*Use when*: Files appear unpredictably and need immediate transfer

**2. Time-Based Triggers** (Scheduled)
```yaml
trigger:
  type: TIME_BASED
  schedule:
    cron: "0 2 * * *"  # Every day at 2 AM
    timezone: "America/New_York"
```
*Use when*: Transfers should happen at specific times (nightly backups, daily reports)

**3. Interval-Based Triggers** (Periodic)
```yaml
trigger:
  type: INTERVAL_BASED
  interval:
    period: 15
    unit: MINUTES
```
*Use when*: Regular periodic transfers regardless of file changes

**4. Batch-Based Triggers** (Accumulation)
```yaml
trigger:
  type: BATCH_BASED
  batch:
    fileCount: 100      # Transfer when 100 files accumulate
    maxWaitTime: 1H     # Or after 1 hour, whichever comes first
```
*Use when*: Efficient to transfer files in batches rather than individually

**5. Size-Based Triggers** (Threshold)
```yaml
trigger:
  type: SIZE_BASED
  threshold:
    size: 1GB
    maxWaitTime: 2H
```
*Use when*: Want to transfer when cumulative size reaches a threshold

**6. Composite Triggers** (Multiple Conditions)
```yaml
trigger:
  type: COMPOSITE
  logic: OR  # OR | AND
  conditions:
    - type: BATCH_BASED
      batch: {fileCount: 50}
    - type: SIZE_BASED
      threshold: {size: 500MB}
```
*Use when*: Complex triggering logic needed

#### Route Lifecycle and Validation

**Controller Startup Process:**

1. **Load Routes**: Controller reads route configurations from repository
2. **Validate Source Agent**: Controller pings source agent to verify it's active and can access the monitored location
3. **Validate Destination Agent**: Controller pings destination agent to verify it's active and can write to target location
4. **Activate Route**: If both agents are healthy, route status changes to ACTIVE
5. **Monitor**: Controller continuously monitors agent health and route status

**Route Status States:**
- `CONFIGURED`: Route loaded but not yet validated
- `VALIDATING`: Controller checking agent availability
- `ACTIVE`: Both agents validated, route monitoring for triggers
- `TRIGGERED`: Trigger condition met, preparing transfer
- `TRANSFERRING`: Active file transfer in progress
- `DEGRADED`: One agent experiencing issues, reduced functionality
- `SUSPENDED`: Manually paused by administrator
- `FAILED`: Validation failed or repeated transfer failures

#### Managing Routes

**View Active Routes:**
```bash
# CLI
quorus route list --status ACTIVE

# API
GET /api/v1/routes?status=ACTIVE
```

**Suspend a Route:**
```bash
# CLI
quorus route suspend crm-to-warehouse

# API
POST /api/v1/routes/crm-to-warehouse/suspend
```

**View Route Statistics:**
```bash
# CLI
quorus route stats crm-to-warehouse

# Output:
# Route: crm-to-warehouse
# Status: ACTIVE
# Files Transferred: 1,247
# Total Bytes: 45.2 GB
# Average Throughput: 12.3 MB/s
# Last Transfer: 2025-01-09 14:32:15
# Success Rate: 99.8%
```

*Note: Detailed system architecture and technical implementation details, including all Java code examples, have been moved to [Part II: Technical Reference](#part-ii-technical-reference-developers) for better document organization. The following sections provide user-friendly overviews of system capabilities.*

---

## YAML Workflows

### Introduction to YAML Workflows

YAML workflows are the heart of Quorus, allowing you to define complex file transfer operations using simple, human-readable configuration files. Think of them as recipes that tell Quorus exactly what files to transfer, where to transfer them, and how to handle different scenarios.

### Why YAML?

**Human-Readable**: YAML looks like structured text that anyone can read and understand
**Version Control Friendly**: Easy to track changes and collaborate with teams
**Self-Documenting**: Comments and clear structure make workflows self-explanatory
**Powerful**: Can handle simple transfers or complex multi-step operations

### Basic YAML Workflow Structure

Every Quorus workflow has two main parts:

#### 1. Metadata (Who, What, When)
```yaml
metadata:
  name: "Daily Sales Report Transfer"
  version: "1.0.0"
  description: "Transfers daily sales reports from FTP server to data warehouse for business intelligence"
  type: "data-pipeline-workflow"
  author: "sales-team@company.com"
  created: "2025-08-21"
  tags: ["sales", "daily", "reports", "data-warehouse"]
```

#### 2. Specification (How)
```yaml
spec:
  execution:
    parallelism: 2    # Run 2 transfers at once

  transferGroups:
    - name: sales-data
      description: "Download sales files"
      transfers:
        - name: get-sales-report
          source: "ftp://sales.company.com/reports/daily.csv"
          destination: "/warehouse/sales/daily.csv"
          protocol: ftp
```

*Note: The following technical sections have been moved to Part II for better organization. Continue reading for user-friendly workflow examples and guides.*

```mermaid
graph TB
    subgraph "Control Plane"
        LB[Load Balancer/VIP]
        C1[Controller Leader]
        C2[Controller Follower 1]
        C3[Controller Follower 2]
        C1 -.->|Raft Consensus| C2
        C1 -.->|Raft Consensus| C3
    end

    subgraph "Data Plane"
        A1[Transfer Agent 1]
        A2[Transfer Agent 2]
        A3[Transfer Agent 3]
        AN[Transfer Agent N]
    end

    subgraph "API Layer"
        API[Quorus API Gateway]
        WF[Workflow Engine]
        MT[Multi-Tenant Manager]
    end

    subgraph "Storage Layer"
        CS[Corporate Storage]
        DS[Department Shares]
        DW[Data Warehouse]
        BS[Backup Systems]
    end

    LB --> C1
    LB --> C2
    LB --> C3

    API --> C1
    WF --> C1
    MT --> C1

    C1 --> A1
    C1 --> A2
    C1 --> A3
    C1 --> AN

    A1 --> CS
    A2 --> DS
    A3 --> DW
    AN --> BS
```

#### 1. Controller Cluster (Control Plane)

The **Controller Cluster** provides centralized coordination and state management using the Raft consensus algorithm. It serves as the brain of the Quorus system, making all critical decisions and maintaining global state consistency across the distributed infrastructure.

##### Key Responsibilities

### Job Scheduling: Assigns transfer jobs to available agents

The Job Scheduler is responsible for intelligent assignment of transfer jobs to the most appropriate agents in the fleet:

The Job Scheduler supports multiple intelligent scheduling strategies:

- **Round Robin**: Simple round-robin assignment across available agents
- **Least Loaded**: Assigns jobs to agents with the lowest current load
- **Capability Based**: Matches job requirements to agent capabilities
- **Locality Aware**: Prefers agents closer to source/destination
- **Tenant Affinity**: Uses agents dedicated to specific tenants

*Note: Detailed Java implementation examples have been moved to [Part II: Technical Reference](#part-ii-technical-reference-developers).*

**Scheduling Factors:**
- **Agent Capacity**: Current load, available bandwidth, concurrent transfer limits
- **Protocol Compatibility**: Agent must support the required transfer protocol
- **Geographic Proximity**: Network latency and bandwidth between agent and endpoints
- **Tenant Affinity**: Some agents may be dedicated to specific tenants
- **Resource Quotas**: Ensure tenant quotas are not exceeded
- **Job Priority**: High-priority jobs get preferential scheduling
- **Failure History**: Avoid agents with recent failures for the same job type

**Load Balancing Algorithms:**
```mermaid
graph LR
    subgraph "Scheduling Strategies"
        RR[Round Robin]
        LL[Least Loaded]
        CB[Capability Based]
        LA[Locality Aware]
        TA[Tenant Affinity]
    end

    subgraph "Decision Factors"
        AC[Agent Capacity]
        PC[Protocol Compatibility]
        GP[Geographic Proximity]
        TQ[Tenant Quotas]
        JP[Job Priority]
    end

    RR --> AC
    LL --> AC
    CB --> PC
    LA --> GP
    TA --> TQ
```

### State Management: Maintains cluster-wide state consistency

The State Management system ensures all controllers have a consistent view through:

- **Agent Registry**: Tracks all registered agents and their current status
- **Job Tracking**: Monitors active transfer jobs and their state transitions
- **Workflow Execution**: Manages running workflows and their progress
- **Tenant Configuration**: Maintains tenant-specific settings and quotas
- **Resource Usage**: Tracks resource consumption across the system
- **State Synchronization**: Uses Raft consensus to ensure consistency
- **Consistency Checks**: Validates state consistency across controllers

*Note: Detailed Java implementation examples have been moved to [Part II: Technical Reference](#part-ii-technical-reference-developers).*

**State Components:**
- **Agent Registry**: All registered agents with their capabilities and status
- **Job Queue**: Pending, active, and completed transfer jobs
- **Workflow State**: Current state of all running workflows
- **Tenant Configuration**: Multi-tenant settings and quotas
- **Resource Usage**: Real-time resource consumption tracking
- **System Metadata**: Configuration, policies, and operational parameters

**State Consistency Guarantees:**
- **Strong Consistency**: All reads return the most recent write
- **Linearizability**: Operations appear to execute atomically
- **Durability**: State changes are persisted and survive failures
- **Partition Tolerance**: System remains consistent during network partitions

### Agent Fleet Management: Monitors and manages transfer agents

The Agent Fleet Manager maintains a real-time view of all transfer agents and their health:

```java
public class AgentFleetManager {
    // Agent lifecycle management
    public void registerAgent(AgentRegistration registration) {
        Agent agent = createAgentFromRegistration(registration);

        // Validate agent capabilities
        validateAgentCapabilities(agent);

        // Add to fleet
        agentRegistry.register(agent);

        // Start health monitoring
        healthMonitor.startMonitoring(agent);

        // Notify scheduling system
        jobScheduler.onAgentAdded(agent);

        logger.info("Agent registered: {} with capabilities: {}",
                   agent.getId(), agent.getCapabilities());
    }

    // Health monitoring
    public void processHealthReport(String agentId, HealthReport report) {
        Agent agent = agentRegistry.getAgent(agentId);

        // Update agent status
        agent.updateHealth(report);

        // Check for degraded performance
        if (report.getPerformanceScore() < DEGRADED_THRESHOLD) {
            handleDegradedAgent(agent);
        }

        // Check for failure conditions
        if (report.getStatus() == AgentStatus.FAILED) {
            handleFailedAgent(agent);
        }

        // Update load balancing weights
        loadBalancer.updateAgentWeight(agentId, report.getCapacityScore());
    }
}
```

**Agent Monitoring Metrics:**
- **Health Status**: Online, degraded, offline, failed
- **Performance Metrics**: CPU usage, memory usage, network utilization
- **Transfer Statistics**: Active transfers, success rate, average duration
- **Capacity Metrics**: Available bandwidth, concurrent transfer slots
- **Error Rates**: Failed transfers, timeout rates, retry frequencies

**Fleet Management Operations:**
- **Auto-scaling**: Automatically add/remove agents based on demand
- **Load Redistribution**: Move jobs from overloaded to underutilized agents
- **Maintenance Mode**: Gracefully drain agents for maintenance
- **Failure Recovery**: Reassign jobs from failed agents to healthy ones

### Workflow Orchestration: Coordinates complex multi-step workflows

The Workflow Orchestrator manages the execution of complex, multi-step transfer workflows. Here are practical YAML examples showing different orchestration patterns:

#### **Sequential Workflow Example**
```yaml
metadata:
  name: "Sequential Data Pipeline"
  version: "1.0.0"
  description: "Sequential execution where each stage depends on the previous one"
  type: "sequential-workflow"
  author: "data-ops@company.com"
  created: "2025-08-21"
  tags: ["sequential", "data-pipeline", "etl"]

spec:
  execution:
    strategy: sequential    # Execute stages one after another
    parallelism: 1
    continueOnError: false  # Stop if any stage fails

  transferGroups:
    - name: extract-data
      description: "Extract raw data from source systems"
      transfers:
        - name: extract-customers
          source: "ftp://source.com/customers.csv"
          destination: "/staging/raw/customers.csv"
          protocol: ftp

    - name: transform-data
      description: "Clean and validate extracted data"
      dependsOn: ["extract-data"]  # Wait for extract to complete
      transfers:
        - name: clean-customers
          source: "/staging/raw/customers.csv"
          destination: "/staging/clean/customers.csv"
          protocol: file
          options:
            processor: "data-cleaner"
            validationRules: "customer-validation.json"

    - name: load-data
      description: "Load processed data into warehouse"
      dependsOn: ["transform-data"]  # Wait for transform to complete
      transfers:
        - name: load-customers
          source: "/staging/clean/customers.csv"
          destination: "/warehouse/customers.csv"
          protocol: file
```

#### **Parallel Workflow Example**
```yaml
metadata:
  name: "Parallel Backup Workflow"
  version: "1.0.0"
  description: "Parallel execution for independent backup operations"
  type: "parallel-workflow"
  author: "backup-ops@company.com"
  created: "2025-08-21"
  tags: ["parallel", "backup", "independent"]

spec:
  execution:
    strategy: parallel      # Execute all stages simultaneously
    parallelism: 5          # Up to 5 concurrent transfers
    continueOnError: true   # Continue even if some backups fail

  transferGroups:
    - name: backup-databases
      description: "Backup all database files"
      transfers:
        - name: backup-users-db
          source: "/data/users.db"
          destination: "s3://backups/databases/users.db"
          protocol: https

        - name: backup-orders-db
          source: "/data/orders.db"
          destination: "s3://backups/databases/orders.db"
          protocol: https

    - name: backup-logs
      description: "Backup application logs"
      transfers:
        - name: backup-app-logs
          source: "/logs/application.log"
          destination: "s3://backups/logs/application.log"
          protocol: https
```

**Workflow Execution Phases:**
1. **Parsing & Validation**: YAML parsing, schema validation, dependency analysis
2. **Planning**: Dependency resolution, execution stage creation, resource allocation
3. **Execution**: Stage-by-stage execution with progress tracking
4. **Monitoring**: Real-time status updates and error handling
5. **Completion**: Final status determination and cleanup

**Orchestration Features:**
- **Dependency Management**: Complex dependency graphs with parallel execution
- **Conditional Execution**: Execute stages based on runtime conditions
- **Error Handling**: Configurable error handling strategies (fail-fast, continue-on-error)
- **Progress Tracking**: Real-time workflow and stage-level progress
- **Resource Management**: Ensure workflows don't exceed tenant quotas

### Resource Allocation: Manages tenant quotas and resource distribution

The Resource Allocator ensures fair distribution of system resources across tenants:

```java
public class ResourceAllocator {
    // Quota enforcement
    public boolean checkQuotaAvailability(String tenantId, ResourceType resourceType, long requestedAmount) {
        TenantQuota quota = quotaManager.getQuota(tenantId, resourceType);
        ResourceUsage currentUsage = usageTracker.getCurrentUsage(tenantId, resourceType);

        long availableQuota = quota.getLimit() - currentUsage.getAmount();

        if (requestedAmount > availableQuota) {
            // Check for burst capacity
            if (quota.hasBurstCapacity() && requestedAmount <= quota.getBurstLimit()) {
                return authorizeBurstUsage(tenantId, resourceType, requestedAmount);
            }
            return false;
        }

        return true;
    }

    // Resource reservation
    public ResourceReservation reserveResources(String tenantId, ResourceRequest request) {
        // Check quota availability
        if (!checkQuotaAvailability(tenantId, request.getResourceType(), request.getAmount())) {
            throw new QuotaExceededException("Insufficient quota for tenant: " + tenantId);
        }

        // Reserve resources
        ResourceReservation reservation = createReservation(tenantId, request);
        reservationManager.addReservation(reservation);

        // Update usage tracking
        usageTracker.recordReservation(reservation);

        return reservation;
    }
}
```

**Resource Types Managed:**
- **Compute Resources**: CPU cores, memory allocation for processing
- **Network Resources**: Bandwidth allocation, concurrent connections
- **Storage Resources**: Storage quotas, file count limits
- **Transfer Resources**: Concurrent transfers, transfer volume limits
- **API Resources**: Request rate limits, workflow execution limits

##### Raft Consensus Implementation

### Leader Election: Automatic leader selection with (N-1)/2 fault tolerance

The Raft leader election ensures exactly one leader exists at any time:

```java
public class RaftLeaderElection {
    // Election process
    public void startElection() {
        currentTerm++;
        state = NodeState.CANDIDATE;
        votedFor = nodeId;

        // Vote for self
        int votes = 1;

        // Request votes from other nodes
        for (String nodeId : clusterNodes) {
            if (!nodeId.equals(this.nodeId)) {
                CompletableFuture<VoteResponse> voteResponse =
                    sendVoteRequest(nodeId, currentTerm, lastLogIndex, lastLogTerm);

                voteResponse.thenAccept(response -> {
                    if (response.isVoteGranted()) {
                        votes++;
                        if (votes > clusterSize / 2) {
                            becomeLeader();
                        }
                    }
                });
            }
        }
    }

    // Leader responsibilities
    private void becomeLeader() {
        state = NodeState.LEADER;

        // Initialize leader state
        initializeLeaderState();

        // Start sending heartbeats
        startHeartbeats();

        // Begin processing client requests
        enableClientRequestProcessing();

        logger.info("Node {} became leader for term {}", nodeId, currentTerm);
    }
}
```

**Election Triggers:**
- **Startup**: When a node starts and no leader exists
- **Leader Failure**: When followers stop receiving heartbeats
- **Network Partition**: When a partition loses contact with the leader
- **Manual Trigger**: Administrative command to force re-election

**Election Guarantees:**
- **Safety**: At most one leader per term
- **Liveness**: Eventually a leader will be elected
- **Fault Tolerance**: Tolerates (N-1)/2 node failures
- **Network Partition**: Majority partition can elect a leader

### Log Replication: Ensures all state changes are consistently replicated

The Raft log replication mechanism ensures all state changes are durably stored and consistently applied:

```java
public class RaftLogReplication {
    // Append entries to followers
    public void replicateEntry(LogEntry entry) {
        // Add entry to local log
        log.append(entry);

        // Replicate to all followers
        for (String followerId : followers) {
            CompletableFuture<AppendEntriesResponse> response =
                sendAppendEntries(followerId, entry);

            response.thenAccept(resp -> {
                if (resp.isSuccess()) {
                    updateFollowerProgress(followerId, entry.getIndex());
                } else {
                    handleReplicationFailure(followerId, resp);
                }
            });
        }
    }

    // Commit entries when majority acknowledges
    private void checkForCommit() {
        for (long index = commitIndex + 1; index <= log.getLastIndex(); index++) {
            int acknowledgments = 1; // Leader counts as acknowledgment

            for (String followerId : followers) {
                if (getFollowerMatchIndex(followerId) >= index) {
                    acknowledgments++;
                }
            }

            // Commit if majority acknowledges
            if (acknowledgments > clusterSize / 2) {
                commitIndex = index;
                applyToStateMachine(log.getEntry(index));
            }
        }
    }
}
```

**Replication Process:**
1. **Entry Creation**: Leader creates log entry for state change
2. **Parallel Replication**: Send entry to all followers simultaneously
3. **Acknowledgment Collection**: Wait for majority acknowledgment
4. **Commit Decision**: Commit entry when majority acknowledges
5. **State Application**: Apply committed entry to state machine
6. **Client Response**: Respond to client after successful commit

**Replication Guarantees:**
- **Durability**: Committed entries survive node failures
- **Consistency**: All nodes apply entries in the same order
- **Majority Agreement**: Entries committed only with majority consent
- **Monotonicity**: Log entries are never removed or reordered

### Split-Brain Prevention: Quorum-based decision making prevents inconsistencies

Raft prevents split-brain scenarios through strict quorum requirements:

```java
public class QuorumManager {
    // Quorum calculation
    public boolean hasQuorum(Set<String> availableNodes) {
        int requiredQuorum = (clusterSize / 2) + 1;
        return availableNodes.size() >= requiredQuorum;
    }

    // Leadership validation
    public boolean canMaintainLeadership() {
        Set<String> reachableNodes = getReachableNodes();
        reachableNodes.add(nodeId); // Include self

        if (!hasQuorum(reachableNodes)) {
            // Step down as leader
            stepDownAsLeader();
            return false;
        }

        return true;
    }

    // Split-brain detection
    public void detectSplitBrain() {
        if (state == NodeState.LEADER) {
            // Continuously verify quorum
            if (!canMaintainLeadership()) {
                logger.warn("Lost quorum, stepping down as leader");
                state = NodeState.FOLLOWER;
                stopClientRequestProcessing();
            }
        }
    }
}
```

**Split-Brain Prevention Mechanisms:**
- **Quorum Requirements**: All decisions require majority agreement
- **Leadership Validation**: Leaders continuously verify they have quorum
- **Automatic Step-Down**: Leaders step down when they lose quorum
- **Client Request Blocking**: Minority partitions reject client requests

### Network Partition Tolerance: Maintains availability during network issues

The system handles network partitions gracefully while maintaining consistency:

```java
public class NetworkPartitionHandler {
    // Partition detection
    public void handleNetworkPartition() {
        Set<String> reachableNodes = detectReachableNodes();

        if (hasQuorum(reachableNodes)) {
            // Majority partition - continue operations
            continueAsActivePartition();

            // Mark unreachable nodes as suspected failed
            markNodesAsSuspected(getUnreachableNodes());

        } else {
            // Minority partition - become read-only
            enterReadOnlyMode();

            // Stop accepting write requests
            stopWriteOperations();

            // Continue serving read requests from local state
            enableReadOnlyOperations();
        }
    }

    // Partition recovery
    public void handlePartitionRecovery(Set<String> recoveredNodes) {
        // Verify recovered nodes are legitimate
        authenticateRecoveredNodes(recoveredNodes);

        // Synchronize state with recovered nodes
        synchronizeState(recoveredNodes);

        // Resume normal operations if quorum restored
        if (hasQuorum(getAllNodes())) {
            resumeNormalOperations();
        }
    }
}
```

**Partition Handling Strategies:**
- **Majority Partition**: Continues normal operations
- **Minority Partition**: Becomes read-only, rejects writes
- **Partition Recovery**: Automatic state synchronization
- **Consistency Preservation**: No conflicting state changes

##### High Availability Features

### Virtual IP Failover: Seamless client redirection during leader changes

Virtual IP failover provides transparent leader changes to clients:

```java
public class VirtualIPManager {
    // VIP management
    public void promoteToLeader() {
        try {
            // Acquire virtual IP
            acquireVirtualIP();

            // Update DNS records
            updateDNSRecords();

            // Start accepting client connections
            startClientListener();

            // Notify load balancer
            notifyLoadBalancer();

            logger.info("Virtual IP acquired, now serving client requests");

        } catch (Exception e) {
            logger.error("Failed to acquire virtual IP", e);
            throw new LeadershipException("Cannot serve as leader without VIP");
        }
    }

    // Graceful failover
    public void stepDownAsLeader() {
        try {
            // Stop accepting new requests
            stopAcceptingNewRequests();

            // Complete in-flight requests
            waitForInFlightRequests();

            // Release virtual IP
            releaseVirtualIP();

            // Update DNS records
            updateDNSRecords();

        } catch (Exception e) {
            logger.error("Error during leader step-down", e);
        }
    }
}
```

**VIP Failover Features:**
- **Automatic Acquisition**: New leader automatically acquires VIP
- **Graceful Release**: Outgoing leader gracefully releases VIP
- **DNS Integration**: Automatic DNS record updates
- **Load Balancer Integration**: Seamless load balancer updates
- **Client Transparency**: Clients see no interruption

### Geographic Distribution: Multi-AZ deployment for disaster recovery

Geographic distribution provides disaster recovery across multiple availability zones:

```java
public class GeographicDistribution {
    // Multi-AZ deployment
    public void deployMultiAZ() {
        // Deploy controllers across availability zones
        Map<String, String> nodeToAZ = Map.of(
            "controller1", "us-east-1a",
            "controller2", "us-east-1b",
            "controller3", "us-east-1c"
        );

        // Configure cross-AZ networking
        configureCrossAZNetworking(nodeToAZ);

        // Set up data replication
        configureDataReplication(nodeToAZ);

        // Configure disaster recovery procedures
        setupDisasterRecovery(nodeToAZ);
    }

    // Disaster recovery
    public void handleAZFailure(String failedAZ) {
        Set<String> affectedNodes = getNodesInAZ(failedAZ);

        // Mark affected nodes as failed
        markNodesAsFailed(affectedNodes);

        // Trigger leader election if leader was affected
        if (isLeaderInFailedAZ(failedAZ)) {
            triggerLeaderElection();
        }

        // Redistribute workload
        redistributeWorkload(affectedNodes);

        // Notify operations team
        sendDisasterRecoveryAlert(failedAZ);
    }
}
```

**Geographic Distribution Benefits:**
- **Disaster Recovery**: Survive entire availability zone failures
- **Reduced Latency**: Deploy closer to data sources and destinations
- **Regulatory Compliance**: Meet data residency requirements
- **Load Distribution**: Distribute load across geographic regions

### Rolling Updates: Zero-downtime upgrades and maintenance

Rolling updates enable zero-downtime system upgrades:

```java
public class RollingUpdateManager {
    // Rolling update process
    public void performRollingUpdate(String newVersion) {
        List<String> updateOrder = calculateUpdateOrder();

        for (String nodeId : updateOrder) {
            // Ensure cluster has quorum without this node
            if (!canSafelyUpdateNode(nodeId)) {
                throw new UpdateException("Cannot safely update node " + nodeId);
            }

            // Gracefully drain node
            drainNode(nodeId);

            // Update node
            updateNode(nodeId, newVersion);

            // Wait for node to rejoin cluster
            waitForNodeRejoin(nodeId);

            // Verify cluster health
            verifyClusterHealth();
        }
    }

    // Safe update validation
    private boolean canSafelyUpdateNode(String nodeId) {
        Set<String> remainingNodes = new HashSet<>(allNodes);
        remainingNodes.remove(nodeId);

        // Ensure remaining nodes form quorum
        return hasQuorum(remainingNodes);
    }
}
```

**Rolling Update Process:**
1. **Update Planning**: Calculate safe update order
2. **Node Draining**: Gracefully stop processing on target node
3. **Software Update**: Update node software/configuration
4. **Rejoin Verification**: Ensure node successfully rejoins cluster
5. **Health Validation**: Verify cluster health before next update

### Health Monitoring: Continuous health checks and automatic recovery

Comprehensive health monitoring enables proactive issue detection and automatic recovery:

```java
public class HealthMonitoringSystem {
    // Health check types
    public enum HealthCheckType {
        BASIC_CONNECTIVITY,     // Basic network connectivity
        RAFT_PARTICIPATION,     // Raft protocol participation
        STATE_CONSISTENCY,      // State machine consistency
        PERFORMANCE_METRICS,    // Performance and resource usage
        EXTERNAL_DEPENDENCIES   // External system dependencies
    }

    // Comprehensive health assessment
    public HealthStatus performHealthCheck(String nodeId) {
        HealthStatus.Builder status = HealthStatus.builder(nodeId);

        // Basic connectivity check
        status.addCheck(BASIC_CONNECTIVITY, checkConnectivity(nodeId));

        // Raft participation check
        status.addCheck(RAFT_PARTICIPATION, checkRaftParticipation(nodeId));

        // State consistency check
        status.addCheck(STATE_CONSISTENCY, checkStateConsistency(nodeId));

        // Performance metrics check
        status.addCheck(PERFORMANCE_METRICS, checkPerformanceMetrics(nodeId));

        // External dependencies check
        status.addCheck(EXTERNAL_DEPENDENCIES, checkExternalDependencies(nodeId));

        return status.build();
    }

    // Automatic recovery actions
    public void handleUnhealthyNode(String nodeId, HealthStatus status) {
        if (status.isCritical()) {
            // Critical failure - immediate action required
            handleCriticalFailure(nodeId, status);
        } else if (status.isDegraded()) {
            // Degraded performance - gradual remediation
            handleDegradedPerformance(nodeId, status);
        }
    }
}
```

**Health Monitoring Features:**
- **Multi-Level Checks**: Basic connectivity to complex state validation
- **Automated Recovery**: Automatic remediation for common issues
- **Escalation Procedures**: Graduated response based on severity
- **Predictive Analytics**: Trend analysis for proactive maintenance
- **Integration**: SNMP, Prometheus, and enterprise monitoring systems

#### 2. Transfer Agents (Data Plane)

**Transfer Agents** are the distributed workhorses that perform actual file transfer operations. They form the data plane of the Quorus architecture, executing transfer jobs assigned by the controller cluster while providing real-time feedback and maintaining high reliability.

##### Agent Capabilities

### Protocol Support: HTTP/HTTPS, FTP, SFTP, SMB, and extensible protocol framework

The protocol support system provides a unified interface for different transfer protocols while allowing protocol-specific optimizations:

```java
public class ProtocolManager {
    // Protocol registry and factory
    private final Map<String, ProtocolHandler> protocolHandlers;

    public void registerProtocol(String scheme, ProtocolHandler handler) {
        protocolHandlers.put(scheme.toLowerCase(), handler);
        logger.info("Registered protocol handler for scheme: {}", scheme);
    }

    // Protocol selection and optimization
    public ProtocolHandler selectOptimalProtocol(TransferRequest request) {
        String scheme = extractScheme(request.getSourceUri());
        ProtocolHandler handler = protocolHandlers.get(scheme);

        if (handler == null) {
            throw new UnsupportedProtocolException("No handler for scheme: " + scheme);
        }

        // Apply protocol-specific optimizations
        return optimizeForTransfer(handler, request);
    }
}

// HTTP/HTTPS Protocol Implementation
public class HttpProtocolHandler implements ProtocolHandler {
    @Override
    public TransferResult executeTransfer(TransferRequest request) {
        HttpTransferContext context = createTransferContext(request);

        // Configure HTTP client with optimizations
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        // Support for range requests (resume capability)
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(request.getSourceUri()))
            .timeout(Duration.ofSeconds(request.getTimeout()));

        // Add range header for resume support
        if (request.getResumeOffset() > 0) {
            requestBuilder.header("Range", "bytes=" + request.getResumeOffset() + "-");
        }

        // Execute transfer with progress tracking
        return executeWithProgressTracking(client, requestBuilder.build(), context);
    }

    // HTTP-specific features
    private void configureHttpOptimizations(HttpClient.Builder builder, TransferRequest request) {
        // Connection pooling
        builder.connectionTimeout(Duration.ofSeconds(30));

        // Compression support
        if (request.isCompressionEnabled()) {
            builder.header("Accept-Encoding", "gzip, deflate");
        }

        // Authentication
        if (request.hasCredentials()) {
            configureAuthentication(builder, request.getCredentials());
        }
    }
}
```

**Protocol-Specific Features:**

*HTTP/HTTPS Protocol:*
- **Range Requests**: Support for partial content and resume capability
- **Compression**: Automatic gzip/deflate compression handling
- **Authentication**: OAuth2, Basic Auth, Bearer tokens, custom headers
- **Redirects**: Automatic redirect following with loop detection
- **Connection Pooling**: Efficient connection reuse for multiple transfers
- **Proxy Support**: Corporate proxy integration with authentication

*FTP Protocol:*
- **Active/Passive Mode**: Automatic mode selection based on network topology
- **Binary/ASCII Transfer**: Automatic mode detection based on content type
- **Directory Operations**: Recursive directory transfers and synchronization
- **Resume Support**: Restart interrupted transfers from last position
- **Secure FTP**: FTPS (FTP over SSL/TLS) support

*SFTP Protocol:*
- **Key-Based Authentication**: SSH key authentication with passphrase support
- **Compression**: SSH compression for improved transfer speeds
- **Concurrent Streams**: Multiple parallel streams within single SSH connection
- **Directory Synchronization**: Efficient directory comparison and sync
- **File Permissions**: Preserve Unix file permissions and ownership

*SMB Protocol:*
- **Windows Authentication**: NTLM, Kerberos, and domain authentication
- **Share Enumeration**: Automatic discovery of available shares
- **File Locking**: Proper file locking to prevent corruption
- **Extended Attributes**: Preserve Windows file attributes and metadata
- **DFS Support**: Distributed File System path resolution

### Concurrent Transfers: Configurable parallelism based on agent capacity

The concurrent transfer system manages multiple simultaneous transfers while respecting resource constraints:

```java
public class ConcurrentTransferManager {
    private final ExecutorService transferExecutor;
    private final Semaphore transferSlots;
    private final Map<String, TransferExecution> activeTransfers;
    private final TransferCapacityManager capacityManager;

    public ConcurrentTransferManager(AgentConfiguration config) {
        // Configure thread pool based on agent capacity
        int maxConcurrentTransfers = config.getMaxConcurrentTransfers();
        this.transferExecutor = Executors.newFixedThreadPool(
            maxConcurrentTransfers,
            new ThreadFactoryBuilder()
                .setNameFormat("transfer-worker-%d")
                .setDaemon(true)
                .build()
        );

        // Semaphore to control concurrent transfers
        this.transferSlots = new Semaphore(maxConcurrentTransfers);
        this.activeTransfers = new ConcurrentHashMap<>();
        this.capacityManager = new TransferCapacityManager(config);
    }

    // Submit transfer for execution
    public CompletableFuture<TransferResult> submitTransfer(TransferJob job) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire transfer slot
                transferSlots.acquire();

                // Check capacity constraints
                if (!capacityManager.canAcceptTransfer(job)) {
                    throw new CapacityExceededException("Agent at capacity");
                }

                // Execute transfer
                return executeTransfer(job);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransferException("Transfer interrupted", e);
            } finally {
                // Release transfer slot
                transferSlots.release();
                activeTransfers.remove(job.getId());
            }
        }, transferExecutor);
    }

    // Dynamic capacity management
    public void adjustCapacity(CapacityAdjustment adjustment) {
        int currentCapacity = transferSlots.availablePermits() + activeTransfers.size();
        int newCapacity = adjustment.getNewCapacity();

        if (newCapacity > currentCapacity) {
            // Increase capacity
            transferSlots.release(newCapacity - currentCapacity);
        } else if (newCapacity < currentCapacity) {
            // Decrease capacity (gracefully)
            int toReduce = currentCapacity - newCapacity;
            transferSlots.acquireUninterruptibly(toReduce);
        }

        logger.info("Adjusted transfer capacity from {} to {}", currentCapacity, newCapacity);
    }
}
```

**Capacity Management Features:**
- **Dynamic Scaling**: Adjust concurrent transfer limits based on performance
- **Resource Monitoring**: Monitor CPU, memory, and network utilization
- **Backpressure Handling**: Gracefully handle capacity constraints
- **Priority Queuing**: High-priority transfers get preferential treatment
- **Load Balancing**: Distribute transfers across available resources

**Capacity Calculation Factors:**
```java
public class TransferCapacityCalculator {
    public int calculateOptimalCapacity(AgentResources resources) {
        // Base capacity on available resources
        int cpuBasedCapacity = resources.getAvailableCpuCores() * 2;
        int memoryBasedCapacity = (int) (resources.getAvailableMemoryMB() / 256);
        int networkBasedCapacity = (int) (resources.getNetworkBandwidthMbps() / 10);

        // Take the minimum to avoid resource exhaustion
        int baseCapacity = Math.min(cpuBasedCapacity,
                          Math.min(memoryBasedCapacity, networkBasedCapacity));

        // Apply safety factor
        return (int) (baseCapacity * 0.8); // 80% utilization target
    }
}
```

### Progress Tracking: Real-time transfer monitoring with rate calculation

The progress tracking system provides comprehensive real-time monitoring of transfer operations. Here are YAML examples showing how to configure progress tracking and monitoring:

#### **Basic Progress Tracking Configuration**
```yaml
metadata:
  name: "Progress Tracking Example"
  version: "1.0.0"
  description: "Demonstrates progress tracking and monitoring configuration"
  type: "monitoring-workflow"
  author: "monitoring-team@company.com"
  created: "2025-08-21"
  tags: ["monitoring", "progress", "tracking"]

spec:
  # Global progress tracking settings
  progressTracking:
    enabled: true
    reportingInterval: "1s"      # Report progress every second
    detailedMetrics: true        # Collect detailed transfer metrics
    predictiveETA: true          # Use ML-enhanced ETA calculations
    anomalyDetection: true       # Detect unusual transfer patterns

  transferGroups:
    - name: monitored-transfers
      transfers:
        - name: large-file-transfer
          source: "https://data.company.com/large-dataset.zip"
          destination: "/data/large-dataset.zip"
          protocol: https
          progressTracking:
            enabled: true
            reportingInterval: "500ms"  # More frequent updates for large files
            metrics:
              - "bytes-transferred"
              - "transfer-rate"
              - "eta"
              - "completion-percentage"
              - "network-latency"
```

#### **Advanced Progress Monitoring**
```yaml
metadata:
  name: "Advanced Progress Monitoring"
  version: "1.0.0"
  description: "Advanced progress monitoring with custom metrics and alerting"
  type: "advanced-monitoring-workflow"
  author: "ops-team@company.com"
  created: "2025-08-21"
  tags: ["monitoring", "advanced", "alerting", "metrics"]

spec:
  # Advanced monitoring configuration
  monitoring:
    progressTracking:
      enabled: true
      reportingInterval: "1s"
      rateSmoothing:
        enabled: true
        windowSize: "30s"        # 30-second moving average
        algorithm: "exponential" # or "simple", "weighted"

    # Custom metrics collection
    customMetrics:
      - name: "transfer-efficiency"
        formula: "bytes-transferred / elapsed-time"
        unit: "bytes/second"

      - name: "network-utilization"
        formula: "current-rate / max-bandwidth * 100"
        unit: "percentage"

      - name: "error-rate"
        formula: "retry-count / total-attempts * 100"
        unit: "percentage"

    # Alerting configuration
    alerts:
      - name: "slow-transfer-alert"
        condition: "transfer-rate < 1MB/s AND elapsed-time > 60s"
        action: "notify"
        channels: ["email", "slack"]

      - name: "high-error-rate-alert"
        condition: "error-rate > 10%"
        action: "escalate"
        channels: ["pagerduty"]

      - name: "eta-exceeded-alert"
        condition: "elapsed-time > eta * 1.5"
        action: "investigate"
        channels: ["monitoring-dashboard"]

  transferGroups:
    - name: critical-transfers
      monitoring:
        priority: "high"
        detailedLogging: true
        realTimeAlerts: true
      transfers:
        - name: financial-data-transfer
          source: "sftp://bank.com/daily-transactions.csv"
          destination: "/finance/transactions.csv"
          protocol: sftp
          monitoring:
            thresholds:
              minTransferRate: "5MB/s"
              maxDuration: "300s"
              maxRetries: 2
```

#### **Progress Reporting and Dashboards**
```yaml
metadata:
  name: "Progress Reporting Configuration"
  version: "1.0.0"
  description: "Configuration for progress reporting and dashboard integration"
  type: "reporting-workflow"
  author: "dashboard-team@company.com"
  created: "2025-08-21"
  tags: ["reporting", "dashboard", "visualization"]

spec:
  # Progress reporting configuration
  reporting:
    # Real-time progress updates
    realTime:
      enabled: true
      websocketEndpoint: "wss://monitoring.company.com/progress"
      updateInterval: "1s"

    # Batch progress reports
    batch:
      enabled: true
      interval: "5m"
      format: "json"
      destination: "kafka://metrics.company.com/transfer-progress"

    # Dashboard integration
    dashboards:
      - type: "grafana"
        endpoint: "https://grafana.company.com"
        datasource: "prometheus"
        metrics:
          - "transfer_bytes_total"
          - "transfer_rate_bytes_per_second"
          - "transfer_duration_seconds"
          - "transfer_errors_total"

      - type: "custom"
        endpoint: "https://dashboard.company.com/api/metrics"
        format: "json"
        authentication:
          type: "bearer"
          token: "{{env.DASHBOARD_TOKEN}}"

  # Export metrics to external systems
  metricsExport:
    prometheus:
      enabled: true
      endpoint: "http://prometheus:9090/metrics"
      labels:
        environment: "{{environment}}"
        team: "data-ops"

    influxdb:
      enabled: true
      endpoint: "http://influxdb:8086"
      database: "transfer_metrics"
      retention: "30d"

  transferGroups:
    - name: dashboard-monitored-transfers
      transfers:
        - name: dashboard-data-sync
          source: "https://api.company.com/dashboard-data.json"
          destination: "/dashboards/data.json"
          protocol: https
          reporting:
            includeInDashboard: true
            customLabels:
              business_unit: "analytics"
              criticality: "high"
              data_type: "dashboard"
```

#### **Performance Metrics and Analysis**
```yaml
metadata:
  name: "Performance Metrics Collection"
  version: "1.0.0"
  description: "Comprehensive performance metrics collection and analysis"
  type: "performance-workflow"
  author: "performance-team@company.com"
  created: "2025-08-21"
  tags: ["performance", "metrics", "analysis"]

spec:
  # Performance monitoring configuration
  performance:
    metrics:
      # Transfer volume metrics
      volume:
        - "bytes-transferred"
        - "total-bytes"
        - "completion-percentage"

      # Rate and throughput metrics
      throughput:
        - "current-transfer-rate"
        - "average-transfer-rate"
        - "peak-transfer-rate"
        - "rate-variance"

      # Time-based metrics
      timing:
        - "elapsed-time"
        - "estimated-time-remaining"
        - "connection-time"
        - "first-byte-time"

      # Network metrics
      network:
        - "network-latency"
        - "packet-loss"
        - "bandwidth-utilization"
        - "connection-quality"

      # Error and reliability metrics
      reliability:
        - "retry-count"
        - "error-rate"
        - "success-rate"
        - "failure-reasons"

    # Performance analysis
    analysis:
      enabled: true
      algorithms:
        - "trend-analysis"      # Identify performance trends
        - "anomaly-detection"   # Detect unusual patterns
        - "bottleneck-analysis" # Identify performance bottlenecks
        - "capacity-planning"   # Predict future capacity needs

    # Performance optimization
    optimization:
      autoTuning: true
      parameters:
        - "chunk-size"
        - "concurrent-connections"
        - "buffer-size"
        - "compression-level"

  transferGroups:
    - name: performance-monitored-transfers
      transfers:
        - name: performance-test-transfer
          source: "https://test-data.company.com/performance-test.dat"
          destination: "/performance/test-data.dat"
          protocol: https
          performance:
            benchmarking: true
            optimization: true
            detailedAnalysis: true
```

**Progress Metrics Collected:**
- **Transfer Volume**: Bytes transferred, total bytes, completion percentage
- **Transfer Rate**: Current rate, average rate, peak rate
- **Time Metrics**: Elapsed time, estimated time remaining (ETA)
- **Phase Tracking**: Connection, transfer, validation phases
- **Network Metrics**: Latency, throughput, packet loss (where available)
- **Error Metrics**: Retry attempts, error rates, failure reasons

**Progress Reporting Features:**
- **Real-Time Updates**: Sub-second progress updates for large transfers
- **Rate Smoothing**: Moving averages to smooth out rate fluctuations
- **Predictive ETA**: Machine learning-enhanced ETA calculations
- **Phase Detection**: Automatic detection of transfer phases
- **Anomaly Detection**: Identify unusual transfer patterns

### Integrity Verification: SHA-256 checksum validation and corruption detection

The integrity verification system ensures data integrity throughout the transfer process. Here are YAML examples showing how to configure checksum validation and corruption detection:

#### **Basic Integrity Verification**
```yaml
metadata:
  name: "Basic Integrity Verification"
  version: "1.0.0"
  description: "Basic checksum validation for ensuring data integrity"
  type: "integrity-workflow"
  author: "security-team@company.com"
  created: "2025-08-21"
  tags: ["integrity", "checksum", "validation"]

spec:
  # Global integrity settings
  integrity:
    enabled: true
    algorithm: "sha256"          # Default checksum algorithm
    validateOnTransfer: true     # Validate during transfer
    validateOnCompletion: true   # Validate after completion
    failOnMismatch: true        # Fail transfer if checksum doesn't match

  transferGroups:
    - name: verified-transfers
      transfers:
        - name: secure-document-transfer
          source: "https://secure.company.com/document.pdf"
          destination: "/secure/document.pdf"
          protocol: https
          integrity:
            enabled: true
            algorithm: "sha256"
            expectedChecksum: "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
```

#### **Multiple Checksum Algorithms**
```yaml
metadata:
  name: "Multiple Checksum Algorithms"
  version: "1.0.0"
  description: "Using different checksum algorithms for different security requirements"
  type: "multi-checksum-workflow"
  author: "security-ops@company.com"
  created: "2025-08-21"
  tags: ["integrity", "multi-algorithm", "security"]

spec:
  transferGroups:
    - name: high-security-transfers
      description: "High-security transfers with SHA-256"
      transfers:
        - name: financial-data
          source: "sftp://bank.com/transactions.csv"
          destination: "/finance/transactions.csv"
          protocol: sftp
          integrity:
            algorithm: "sha256"
            expectedChecksum: "{{source.metadata.sha256}}"
            validateChunks: true     # Validate individual chunks
            chunkSize: "1MB"

    - name: standard-transfers
      description: "Standard transfers with SHA-1"
      transfers:
        - name: customer-data
          source: "https://api.company.com/customers.json"
          destination: "/data/customers.json"
          protocol: https
          integrity:
            algorithm: "sha1"
            expectedChecksum: "{{response.headers.etag}}"

    - name: legacy-transfers
      description: "Legacy system compatibility with MD5"
      transfers:
        - name: legacy-data
          source: "ftp://legacy.company.com/data.txt"
          destination: "/legacy/data.txt"
          protocol: ftp
          integrity:
            algorithm: "md5"
            expectedChecksum: "{{source.sidecar.md5}}"
            allowWeakAlgorithm: true  # Explicitly allow MD5
```

#### **Advanced Integrity Verification**
```yaml
metadata:
  name: "Advanced Integrity Verification"
  version: "1.0.0"
  description: "Advanced integrity verification with multiple validation methods"
  type: "advanced-integrity-workflow"
  author: "data-integrity-team@company.com"
  created: "2025-08-21"
  tags: ["integrity", "advanced", "multi-validation"]

spec:
  # Advanced integrity configuration
  integrity:
    multipleValidation:
      enabled: true
      algorithms: ["sha256", "sha1"]  # Calculate multiple checksums
      requireAll: false              # At least one must match

    streaming:
      enabled: true                  # Calculate during transfer
      bufferSize: "64KB"            # Buffer size for streaming calculation

    corruption:
      detection: true                # Enable corruption detection
      recovery: "retry"              # or "fail", "skip"
      maxRetries: 3

  transferGroups:
    - name: critical-data-transfers
      integrity:
        strictValidation: true
        corruptionTolerance: "zero"   # No corruption allowed
      transfers:
        - name: database-backup
          source: "/database/backup.sql"
          destination: "s3://backups/database/backup.sql"
          protocol: https
          integrity:
            algorithms: ["sha256", "crc32"]
            expectedChecksums:
              sha256: "{{source.metadata.sha256}}"
              crc32: "{{source.metadata.crc32}}"
            partialValidation:
              enabled: true
              chunkSize: "10MB"
              validateEachChunk: true

    - name: large-file-transfers
      description: "Large files with chunk-based validation"
      transfers:
        - name: video-file-transfer
          source: "https://media.company.com/video.mp4"
          destination: "/media/video.mp4"
          protocol: https
          integrity:
            algorithm: "sha256"
            chunkValidation:
              enabled: true
              chunkSize: "100MB"
              parallelValidation: true
              resumeOnFailure: true
```

#### **Checksum Source Integration**
```yaml
metadata:
  name: "Checksum Source Integration"
  version: "1.0.0"
  description: "Integration with various checksum sources and metadata"
  type: "checksum-integration-workflow"
  author: "integration-team@company.com"
  created: "2025-08-21"
  tags: ["integrity", "integration", "metadata"]

spec:
  transferGroups:
    - name: http-header-checksums
      description: "Using HTTP headers for checksum validation"
      transfers:
        - name: api-data-with-etag
          source: "https://api.company.com/data.json"
          destination: "/data/api-data.json"
          protocol: https
          integrity:
            algorithm: "md5"
            expectedChecksum: "{{response.headers.content-md5}}"

        - name: api-data-with-custom-header
          source: "https://api.company.com/secure-data.json"
          destination: "/data/secure-data.json"
          protocol: https
          integrity:
            algorithm: "sha256"
            expectedChecksum: "{{response.headers.x-content-sha256}}"

    - name: file-metadata-checksums
      description: "Using file metadata and sidecar files"
      transfers:
        - name: data-with-sidecar
          source: "ftp://data.company.com/dataset.csv"
          destination: "/data/dataset.csv"
          protocol: ftp
          integrity:
            algorithm: "sha256"
            expectedChecksum: "{{sidecar.file('dataset.csv.sha256')}}"

        - name: data-with-extended-attributes
          source: "/source/data-with-xattr.bin"
          destination: "/dest/data.bin"
          protocol: file
          integrity:
            algorithm: "sha1"
            expectedChecksum: "{{source.xattr.user.checksum.sha1}}"

    - name: api-provided-checksums
      description: "Checksums provided by API responses"
      transfers:
        - name: api-with-checksum-response
          source: "https://secure-api.company.com/download/{{fileId}}"
          destination: "/downloads/{{fileName}}"
          protocol: https
          integrity:
            algorithm: "sha256"
            expectedChecksum: "{{api.response.checksum.sha256}}"
            preValidation:
              enabled: true
              endpoint: "https://secure-api.company.com/checksum/{{fileId}}"
```

#### **Error Handling and Recovery**
```yaml
metadata:
  name: "Integrity Error Handling"
  version: "1.0.0"
  description: "Error handling and recovery strategies for integrity failures"
  type: "integrity-error-handling-workflow"
  author: "reliability-team@company.com"
  created: "2025-08-21"
  tags: ["integrity", "error-handling", "recovery"]

spec:
  # Global error handling for integrity failures
  integrity:
    errorHandling:
      onChecksumMismatch: "retry"    # or "fail", "quarantine", "notify"
      onCorruption: "retry"          # or "fail", "skip", "repair"
      onValidationError: "fail"      # or "skip", "warn"

    recovery:
      maxRetries: 3
      retryDelay: "exponential"
      quarantineLocation: "/quarantine/"
      notificationChannels: ["email", "slack"]

  transferGroups:
    - name: resilient-integrity-transfers
      transfers:
        - name: self-healing-transfer
          source: "https://unreliable-source.com/data.zip"
          destination: "/data/data.zip"
          protocol: https
          integrity:
            algorithm: "sha256"
            expectedChecksum: "{{source.metadata.sha256}}"
            errorHandling:
              onMismatch: "retry-with-different-source"
              alternativeSources:
                - "https://backup-source.com/data.zip"
                - "ftp://mirror.company.com/data.zip"
              maxRetries: 5

        - name: quarantine-on-failure
          source: "sftp://external.com/suspicious-file.dat"
          destination: "/data/suspicious-file.dat"
          protocol: sftp
          integrity:
            algorithm: "sha256"
            expectedChecksum: "{{external.api.checksum}}"
            errorHandling:
              onMismatch: "quarantine"
              quarantineLocation: "/quarantine/suspicious/"
              notifySecurityTeam: true
              generateIncident: true
```

**Integrity Verification Features:**
- **Multiple Algorithms**: SHA-256, SHA-1, MD5, CRC32 support
- **Streaming Calculation**: Calculate checksum during transfer (no additional I/O)
- **Source Verification**: Compare against source-provided checksums
- **Corruption Detection**: Detect data corruption during transfer
- **Partial Verification**: Verify chunks for large file transfers
- **Performance Optimization**: Hardware-accelerated checksums where available

**Checksum Sources:**
- **HTTP Headers**: Content-MD5, ETag headers
- **File Metadata**: Extended attributes, sidecar files
- **API Responses**: Checksum provided by source API
- **Pre-calculated**: Checksums calculated before transfer
- **On-demand**: Calculate if not provided by source

### Retry Logic: Exponential backoff with configurable retry policies

The retry system provides intelligent retry logic for handling transient failures. Here are practical YAML examples showing different retry configurations:

#### **Basic Retry Configuration**
```yaml
metadata:
  name: "Basic Retry Example"
  version: "1.0.0"
  description: "Simple retry configuration for handling network failures"
  type: "retry-workflow"
  author: "ops@company.com"
  created: "2025-08-21"
  tags: ["retry", "resilience", "network"]

spec:
  execution:
    # Global retry policy for all transfers
    retryPolicy:
      maxAttempts: 3
      initialDelay: "1s"
      maxDelay: "30s"
      backoffStrategy: exponential
      backoffMultiplier: 2.0
      jitter: true  # Add randomness to prevent thundering herd
      retryOn: ["network-error", "timeout", "connection-refused"]

  transferGroups:
    - name: resilient-downloads
      transfers:
        - name: download-report
          source: "https://unreliable-api.com/daily-report.csv"
          destination: "/reports/daily-report.csv"
          protocol: https
          options:
            timeout: "30s"
```

#### **Advanced Retry Strategies**
```yaml
metadata:
  name: "Advanced Retry Strategies"
  version: "1.0.0"
  description: "Different retry strategies for different types of failures"
  type: "advanced-retry-workflow"
  author: "reliability-team@company.com"
  created: "2025-08-21"
  tags: ["retry", "advanced", "fault-tolerance"]

spec:
  transferGroups:
    - name: aggressive-retry-group
      description: "Aggressive retry for critical transfers"
      retryPolicy:
        maxAttempts: 5
        initialDelay: "500ms"
        maxDelay: "2m"
        backoffStrategy: exponential
        backoffMultiplier: 2.0
        jitter: true
        retryOn: ["network-error", "timeout", "server-error"]
      transfers:
        - name: critical-data-transfer
          source: "https://critical-api.com/data.json"
          destination: "/critical/data.json"
          protocol: https

    - name: conservative-retry-group
      description: "Conservative retry for non-critical transfers"
      retryPolicy:
        maxAttempts: 3
        initialDelay: "5s"
        maxDelay: "5m"
        backoffStrategy: exponential
        backoffMultiplier: 1.5
        jitter: false
        retryOn: ["timeout", "connection-refused"]
      transfers:
        - name: optional-data-transfer
          source: "ftp://optional-source.com/data.csv"
          destination: "/optional/data.csv"
          protocol: ftp
```

#### **Per-Transfer Retry Configuration**
```yaml
metadata:
  name: "Per-Transfer Retry Configuration"
  version: "1.0.0"
  description: "Individual retry settings for each transfer based on requirements"
  type: "custom-retry-workflow"
  author: "integration-team@company.com"
  created: "2025-08-21"
  tags: ["retry", "custom", "per-transfer"]

spec:
  transferGroups:
    - name: mixed-retry-requirements
      transfers:
        # High-priority transfer with aggressive retry
        - name: financial-data
          source: "sftp://bank.com/transactions.csv"
          destination: "/finance/transactions.csv"
          protocol: sftp
          retryPolicy:
            maxAttempts: 10
            initialDelay: "100ms"
            maxDelay: "1m"
            backoffStrategy: exponential
            backoffMultiplier: 1.2
            jitter: true
            retryOn: ["network-error", "authentication-error", "timeout"]

        # Medium-priority with standard retry
        - name: customer-data
          source: "https://crm.company.com/customers.json"
          destination: "/customers/data.json"
          protocol: https
          retryPolicy:
            maxAttempts: 3
            initialDelay: "1s"
            maxDelay: "30s"
            backoffStrategy: exponential
            backoffMultiplier: 2.0
            retryOn: ["network-error", "server-error"]

        # Low-priority with minimal retry
        - name: logs-backup
          source: "/logs/application.log"
          destination: "s3://backups/logs/application.log"
          protocol: https
          retryPolicy:
            maxAttempts: 2
            initialDelay: "5s"
            maxDelay: "10s"
            backoffStrategy: fixed
            retryOn: ["timeout"]
```

**Retry Policy Features:**
- **Exponential Backoff**: Progressively longer delays between retries
- **Jitter**: Random variation to prevent thundering herd problems
- **Exception Classification**: Different retry behavior for different error types
- **Circuit Breaker**: Temporarily disable retries for consistently failing endpoints
- **Adaptive Policies**: Adjust retry behavior based on historical success rates

**Retryable vs Non-Retryable Exceptions:**
```java
// Retryable exceptions (transient failures)
- NetworkTimeoutException
- ConnectionResetException
- TemporaryServerErrorException (5xx HTTP errors)
- RateLimitExceededException
- ServiceUnavailableException

// Non-retryable exceptions (permanent failures)
- AuthenticationException
- AuthorizationException
- FileNotFoundException
- InvalidChecksumException
- QuotaExceededException
```

This comprehensive expansion provides deep technical insight into how the Controller Cluster and Transfer Agents operate, showing the sophisticated engineering that makes Quorus a robust, enterprise-grade file transfer system.

**Agent Architecture:**
```mermaid
graph LR
    subgraph "Transfer Agent"
        TM[Transfer Manager]
        PE[Protocol Engine]
        PM[Progress Monitor]
        CM[Checksum Manager]

        TM --> PE
        TM --> PM
        TM --> CM

        subgraph "Protocol Handlers"
            HTTP[HTTP Handler]
            FTP[FTP Handler]
            SFTP[SFTP Handler]
            SMB[SMB Handler]
        end

        PE --> HTTP
        PE --> FTP
        PE --> SFTP
        PE --> SMB
    end
```

**Agent Registration and Discovery:**
- **Automatic Registration**: Agents self-register with the controller cluster
- **Capability Advertisement**: Agents advertise their supported protocols and capacity
- **Health Reporting**: Continuous health and performance metrics reporting
- **Dynamic Scaling**: Agents can be added/removed without service interruption

#### 3. API Gateway Layer

The **API Gateway** provides unified access to all Quorus functionality:

**Gateway Features:**
- **Authentication & Authorization**: Enterprise SSO integration and RBAC
- **Rate Limiting**: Tenant-based request throttling and quota enforcement
- **Request Routing**: Intelligent routing to healthy controller nodes
- **Protocol Translation**: REST API to internal protocol conversion
- **Audit Logging**: Comprehensive request/response logging for compliance

**API Design Principles:**
- **RESTful Design**: Standard HTTP methods and status codes
- **OpenAPI Specification**: Complete API documentation and client generation
- **Versioning**: Backward-compatible API evolution
- **Idempotency**: Safe retry semantics for critical operations

### Data Flow Architecture

#### Transfer Request Lifecycle

```mermaid
sequenceDiagram
    participant Client
    participant API as API Gateway
    participant Controller as Controller Leader
    participant Agent as Transfer Agent
    participant Storage as Target Storage

    Client->>API: Submit Transfer Request
    API->>API: Authenticate & Authorize
    API->>Controller: Forward Request
    Controller->>Controller: Schedule Transfer
    Controller->>Agent: Assign Transfer Job
    Agent->>Storage: Execute Transfer
    Agent->>Controller: Report Progress
    Controller->>API: Update Status
    API->>Client: Return Progress
    Agent->>Controller: Report Completion
    Controller->>API: Final Status
    API->>Client: Transfer Complete
```

#### Workflow Execution Flow

```mermaid
sequenceDiagram
    participant Client
    participant WE as Workflow Engine
    participant Controller as Controller
    participant Agent1 as Agent 1
    participant Agent2 as Agent 2

    Client->>WE: Submit Workflow
    WE->>WE: Parse & Validate YAML
    WE->>WE: Build Dependency Graph
    WE->>WE: Create Execution Plan

    loop For Each Transfer Group
        WE->>Controller: Submit Transfer Jobs
        Controller->>Agent1: Assign Job 1
        Controller->>Agent2: Assign Job 2
        Agent1->>WE: Report Progress
        Agent2->>WE: Report Progress
        Agent1->>WE: Job Complete
        Agent2->>WE: Job Complete
    end

    WE->>Client: Workflow Complete
```

### Fault Tolerance and Resilience

#### Controller Fault Tolerance

**Raft Consensus Guarantees:**
- **Consistency**: All nodes agree on the same state
- **Availability**: System remains operational with majority of nodes
- **Partition Tolerance**: Handles network splits gracefully

**Failure Scenarios:**
1. **Leader Failure**: Automatic re-election within election timeout
2. **Follower Failure**: Continues operation with remaining nodes
3. **Network Partition**: Majority partition maintains service
4. **Split-Brain Prevention**: Minority partitions become read-only

#### Agent Fault Tolerance

**Agent Failure Handling:**
- **Job Reassignment**: Failed jobs automatically reassigned to healthy agents
- **Partial Transfer Recovery**: Resume transfers from last checkpoint
- **Circuit Breaker**: Temporarily disable failing agents
- **Graceful Degradation**: Reduce capacity rather than complete failure

**Network Resilience:**
- **Connection Pooling**: Efficient connection reuse and management
- **Timeout Management**: Configurable timeouts for different scenarios
- **Retry Strategies**: Exponential backoff with jitter
- **Bandwidth Adaptation**: Dynamic rate limiting based on network conditions

### Security Architecture

#### Defense in Depth

```mermaid
graph TB
    subgraph "Network Security"
        FW[Corporate Firewall]
        VPN[VPN/Private Network]
        TLS[TLS Encryption]
    end

    subgraph "Application Security"
        AUTH[Authentication]
        AUTHZ[Authorization]
        RBAC[Role-Based Access Control]
    end

    subgraph "Data Security"
        EAR[Encryption at Rest]
        EIT[Encryption in Transit]
        CS[Checksum Validation]
    end

    subgraph "Operational Security"
        AUDIT[Audit Logging]
        MON[Security Monitoring]
        COMP[Compliance Controls]
    end

    FW --> AUTH
    VPN --> AUTHZ
    TLS --> RBAC

    AUTH --> EAR
    AUTHZ --> EIT
    RBAC --> CS

    EAR --> AUDIT
    EIT --> MON
    CS --> COMP
```

#### Security Layers

**1. Network Security:**
- **Corporate Firewall Integration**: Leverages existing network security
- **Private Network Communication**: Internal-only communication paths
- **TLS 1.3 Encryption**: Modern encryption for all external communications
- **Certificate Management**: Automated certificate lifecycle management

**2. Application Security:**
- **Enterprise SSO Integration**: Active Directory, LDAP, SAML, OAuth2
- **Multi-Factor Authentication**: Support for corporate MFA requirements
- **Role-Based Access Control**: Fine-grained permission management
- **API Security**: Rate limiting, input validation, and injection prevention

**3. Data Security:**
- **Encryption at Rest**: AES-256 encryption for stored data
- **Encryption in Transit**: TLS for all network communications
- **Key Management**: Integration with corporate key management systems
- **Data Classification**: Support for corporate data classification policies

**4. Operational Security:**
- **Comprehensive Audit Logging**: All operations logged for compliance
- **Security Event Monitoring**: Integration with SIEM systems
- **Compliance Frameworks**: SOX, GDPR, HIPAA, PCI-DSS support
- **Incident Response**: Automated alerting and response procedures

*Note: Detailed core functional concepts and all Java implementation examples have been moved to [Part II: Technical Reference](#part-ii-technical-reference-developers) for better document organization. The following sections provide user-friendly examples and guides.*

---

## Basic File Transfers

### Simple File Transfer Examples

Let's start with some practical examples of common file transfer scenarios using YAML workflows.

#### Example 1: Download a Daily Report

```yaml
metadata:
  name: "Download Daily Sales Report"
  version: "1.0.0"
  description: "Downloads the daily sales report from our FTP server for business analysis and reporting"
  type: "download-workflow"
  author: "sales@company.com"
  created: "2025-08-21"
  tags: ["sales", "daily", "ftp", "business-intelligence"]

spec:
  transferGroups:
    - name: download-report
      transfers:
        - name: get-sales-data
          source: "ftp://reports.company.com/sales/daily-sales.csv"
          destination: "/local/reports/sales/daily-sales.csv"
          protocol: ftp
          options:
            username: "sales_user"
            password: "{{env.FTP_PASSWORD}}"
```

#### Example 2: Upload Files to Cloud Storage

```yaml
metadata:
  name: "Upload Backup Files"
  version: "1.0.0"
  description: "Uploads database backup files to cloud storage for disaster recovery and compliance"
  type: "backup-workflow"
  author: "backup@company.com"
  created: "2025-08-21"
  tags: ["backup", "cloud", "https", "disaster-recovery"]

spec:
  transferGroups:
    - name: upload-backups
      transfers:
        - name: upload-database-backup
          source: "/backups/database/daily-backup.sql"
          destination: "https://storage.company.com/backups/database/daily-backup.sql"
          protocol: https
          options:
            method: "PUT"
            headers:
              Authorization: "Bearer {{env.CLOUD_TOKEN}}"
```

#### Example 3: Synchronize Directories

```yaml
metadata:
  name: "Sync Customer Files"
  version: "1.0.0"
  description: "Synchronizes customer files between primary and backup servers for data redundancy"
  type: "sync-workflow"
  author: "operations@company.com"
  created: "2025-08-21"
  tags: ["sync", "customer-data", "sftp", "redundancy"]

spec:
  transferGroups:
    - name: sync-customer-data
      transfers:
        - name: sync-customer-files
          source: "sftp://source.company.com/customer-data/"
          destination: "sftp://backup.company.com/customer-data/"
          protocol: sftp
          options:
            recursive: true
            preserve_permissions: true
            ssh_key: "/keys/backup-key.pem"
```

### Transfer Protocols Quick Reference

| **Protocol** | **Best For** | **Example Use Case** |
|--------------|--------------|---------------------|
| **HTTPS** | Cloud APIs, web services | Uploading to cloud storage |
| **FTP/FTPS** | Legacy systems, bulk transfers | Downloading reports from mainframe |
| **SFTP** | Secure transfers, Unix systems | Syncing files between Linux servers |
| **SMB** | Windows networks | Copying files to network shares |

*Note: The following technical sections have been moved to Part II for better organization. Continue reading for multi-tenant operations and administration guides.*

A **Transfer Job** represents a single file transfer operation with the following lifecycle:

```mermaid
stateDiagram-v2
    [*] --> PENDING: Job Created
    PENDING --> QUEUED: Scheduled
    QUEUED --> ASSIGNED: Agent Selected
    ASSIGNED --> IN_PROGRESS: Transfer Started
    IN_PROGRESS --> VALIDATING: Transfer Complete
    VALIDATING --> COMPLETED: Checksum Valid
    VALIDATING --> FAILED: Checksum Invalid
    IN_PROGRESS --> FAILED: Transfer Error
    IN_PROGRESS --> CANCELLED: User Cancelled
    FAILED --> RETRYING: Retry Attempt
    RETRYING --> IN_PROGRESS: Retry Started
    RETRYING --> FAILED: Max Retries Exceeded
    COMPLETED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

**Transfer Job Properties:**
- **Job ID**: Unique identifier for tracking and management
- **Source URI**: Complete source location with protocol and credentials
- **Destination Path**: Target location within the tenant's storage space
- **Protocol**: Transfer protocol (HTTP, HTTPS, FTP, SFTP, SMB)
- **Options**: Timeout, retry policy, checksum validation, chunk size
- **Metadata**: File size, content type, creation time, tags
- **Progress**: Bytes transferred, transfer rate, ETA, completion percentage

#### Protocol Abstraction

The **Protocol Engine** provides a unified interface for different transfer protocols:

```java
public interface TransferProtocol {
    // Protocol identification
    String getProtocolName();
    List<String> getSupportedSchemes();

    // Transfer operations
    CompletableFuture<TransferResult> transfer(TransferRequest request);
    CompletableFuture<Void> cancel(String jobId);

    // Capability queries
    boolean supportsResume();
    boolean supportsChecksum();
    long getMaxFileSize();

    // Configuration
    void configure(ProtocolConfiguration config);
    ProtocolCapabilities getCapabilities();
}
```

**Protocol-Specific Features:**
- **HTTP/HTTPS**: Range requests, compression, custom headers, OAuth2
- **FTP**: Active/passive mode, binary/ASCII transfer, directory operations
- **SFTP**: Key-based authentication, compression, concurrent streams
- **SMB**: Windows authentication, share enumeration, file locking

#### Progress Tracking System

The **Progress Tracking System** provides real-time visibility into transfer operations:

**Progress Metrics:**
- **Bytes Transferred**: Current and total bytes
- **Transfer Rate**: Current, average, and peak transfer rates
- **Time Estimates**: Elapsed time, estimated time remaining (ETA)
- **Completion Percentage**: Visual progress indicator
- **Network Statistics**: Latency, packet loss, connection quality

**Progress Events:**
```java
public class ProgressEvent {
    private String jobId;
    private long bytesTransferred;
    private long totalBytes;
    private double transferRate;
    private Duration elapsedTime;
    private Duration estimatedTimeRemaining;
    private TransferPhase phase; // CONNECTING, TRANSFERRING, VALIDATING
    private Map<String, Object> metadata;
}
```

### Workflow Engine Concepts

#### Workflow Definition Model

A **Workflow Definition** is a declarative specification that orchestrates file transfer operations. Let's start with simple examples and gradually build to complex enterprise scenarios, explaining each configuration line by line.

##### Basic Workflow Structure - Simple Example

Let's begin with the simplest possible workflow that transfers a single file:

```yaml
# Complete metadata section - all required fields for schema validation
metadata:
  name: "Simple File Transfer"                      # Required: Human-readable name
  version: "1.0.0"                                 # Required: Semantic version
  description: "Transfer a single file from API to storage for data processing"  # Required: Business purpose (min 10 chars)
  type: "transfer-workflow"                         # Required: Workflow classification
  author: "data-team@company.com"                   # Required: Ownership/contact
  created: "2025-08-21"                            # Required: Creation date (ISO format)
  tags: ["simple", "api", "data-transfer"]          # Required: Classification tags

# Workflow specification - what actually happens
spec:
  transferGroups:                     # Container for groups of related transfers
    - name: single-transfer           # Name of this transfer group
      transfers:                      # List of individual file transfers
        - name: copy-data-file        # Name of this specific transfer
          source: "https://api.example.com/data.csv"    # Where to get the file
          destination: "/storage/data.csv"              # Where to put the file
          protocol: https             # Transfer protocol
```

**Field-by-Field Explanation:**

**Metadata Section (Required for Schema Validation):**
- **name**: Human-readable workflow identifier (2-100 characters)
- **version**: Semantic version number (e.g., "1.0.0", "2.1.3")
- **description**: Business purpose description (10-500 characters)
- **type**: Workflow classification (e.g., "transfer-workflow", "data-pipeline-workflow")
- **author**: Contact information (email address or name)
- **created**: Creation date in ISO format (YYYY-MM-DD)
- **tags**: Array of classification tags (lowercase, hyphen-separated)

**Specification Section:**
- **transferGroups**: Contains one or more groups of related transfers
- **name**: Each group has a unique name for identification and dependency management
- **transfers**: Lists the actual file transfer operations
- **source**: Specifies where to retrieve the file (supports various protocols)
- **destination**: Specifies where to store the file
- **protocol**: Transfer protocol (https, ftp, sftp, smb)

##### Adding Basic Configuration - Intermediate Example

Now let's add some basic configuration options:

```yaml
metadata:
  name: "Configured File Transfer"
  version: "1.0.0"
  description: "Transfer with basic configuration options including variables and error handling"
  type: "transfer-workflow"
  author: "data-team@company.com"
  created: "2025-08-21"
  tags: ["configured", "api", "variables", "error-handling"]

spec:
  # Global variables that can be reused throughout the workflow
  variables:
    sourceUrl: "https://api.example.com"      # Base URL for API calls
    targetDir: "/storage/daily-imports"       # Base directory for files

  # Execution configuration controls how transfers run
  execution:
    strategy: sequential    # Run transfers one after another (vs parallel)
    timeout: 1800          # Maximum time in seconds (30 minutes)
    parallelism: 1         # Maximum concurrent transfers

  transferGroups:
    - name: daily-data-import
      transfers:
        - name: import-customers
          # Using variables with {{variable}} syntax
          source: "{{sourceUrl}}/customers/export.csv"
          destination: "{{targetDir}}/customers.csv"
          protocol: https
          # Transfer-specific options
          options:
            timeout: 600              # Override global timeout (10 minutes)
            checksumValidation: true  # Verify file integrity after transfer
            retryAttempts: 3         # Retry failed transfers up to 3 times
```

**New Configuration Explained:**
- **Complete Metadata**: All required fields for schema validation are included
- **Variables Section**: Defines reusable values that can be referenced with `{{variableName}}` syntax
- **Execution Section**: Controls workflow-level behavior including parallelism and timeouts
- **Protocol Specification**: Each transfer now explicitly specifies the protocol to use
- **Enhanced Options**: Transfer-specific options override global settings
- **Line 14**: `strategy: sequential` means transfers run one at a time
- **Line 15**: `timeout: 1800` sets maximum workflow execution time (30 minutes)
- **Line 16**: `parallelism: 1` limits concurrent transfers
- **Lines 22-23**: `{{sourceUrl}}` and `{{targetDir}}` are replaced with variable values
- **Lines 24-28**: `options` section provides transfer-specific configuration
- **Line 26**: `checksumValidation: true` enables integrity verification
- **Line 27**: `retryAttempts: 3` automatically retries failed transfers

##### Adding Dependencies - Multi-Stage Example

Let's create a workflow with multiple stages that depend on each other:

```yaml
metadata:
  name: "Multi-Stage Data Pipeline"
  version: "1.1.0"
  description: "Multi-stage data processing pipeline with extract, transform, and load phases"
  type: "data-pipeline-workflow"
  author: "data-engineering@company.com"
  created: "2025-08-21"
  tags: ["etl", "multi-stage", "data-warehouse", "sequential"]
  # Optional labels for additional categorization
  labels:
    environment: production    # Environment designation
    department: finance       # Owning department
    schedule: daily          # Execution frequency

spec:
  variables:
    # Line 14: Built-in function that returns current date (e.g., "2025-01-20")
    processingDate: "{{today}}"
    sourceSystem: "https://erp.company.com"
    stagingArea: "/staging/finance"
    finalDestination: "/warehouse/finance"

  execution:
    # Line 19: Mixed strategy allows some parallel execution within constraints
    strategy: mixed
    parallelism: 3
    timeout: 3600    # 1 hour total

  transferGroups:
    # Stage 1: Extract raw data from source systems
    - name: extract-raw-data
      # Line 26: Description documents the purpose of this stage
      description: "Extract customer and order data from ERP system"
      transfers:
        - name: extract-customers
          source: "{{sourceSystem}}/api/customers/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw-customers-{{processingDate}}.json"

        - name: extract-orders
          source: "{{sourceSystem}}/api/orders/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw-orders-{{processingDate}}.json"

    # Stage 2: Process the extracted data
    - name: process-data
      description: "Clean and validate the extracted data"
      # Line 38: This stage depends on the previous stage completing first
      dependsOn: [extract-raw-data]
      transfers:
        - name: clean-customer-data
          # Line 41: Using output from previous stage as input
          source: "{{stagingArea}}/raw-customers-{{processingDate}}.json"
          destination: "{{stagingArea}}/clean-customers-{{processingDate}}.json"

        - name: clean-order-data
          source: "{{stagingArea}}/raw-orders-{{processingDate}}.json"
          destination: "{{stagingArea}}/clean-orders-{{processingDate}}.json"

    # Stage 3: Load processed data to final destination
    - name: load-final-data
      description: "Load processed data into data warehouse"
      # Line 50: This stage depends on data processing completing
      dependsOn: [process-data]
      transfers:
        - name: load-customers
          source: "{{stagingArea}}/clean-customers-{{processingDate}}.json"
          destination: "{{finalDestination}}/customers/{{processingDate}}.json"

        - name: load-orders
          source: "{{stagingArea}}/clean-orders-{{processingDate}}.json"
          destination: "{{finalDestination}}/orders/{{processingDate}}.json"
```

**New Concepts Explained:**
- **Lines 7-10**: `labels` provide metadata for categorization and filtering
- **Line 14**: `{{today}}` is a built-in function that returns the current date
- **Line 19**: `strategy: mixed` allows intelligent parallel execution within dependencies
- **Line 26**: `description` at the group level documents the stage's purpose
- **Line 38**: `dependsOn: [extract-raw-data]` creates a dependency relationship
- **Line 41**: Using output from one stage as input to the next creates a data pipeline
- **Line 50**: Multiple dependencies ensure proper execution order

##### Adding Conditional Logic - Advanced Example

Now let's add conditional execution based on business rules:

```yaml
metadata:
  name: "Conditional Business Pipeline"
  version: "2.0.0"
  description: "Business pipeline with conditional processing based on data validation and business rules"
  type: "business-workflow"
  author: "business-intelligence@company.com"
  created: "2025-08-21"
  tags: ["conditional", "business-rules", "validation", "intelligent"]
  labels:
    environment: production
    criticality: high
    compliance: required

spec:
  variables:
    processingDate: "{{today}}"
    environment: "production"
    # Line 16: Built-in function that returns day of month (1-31)
    dayOfMonth: "{{dayOfMonth}}"
    # Line 18: Built-in function that returns day of week (1=Monday, 7=Sunday)
    dayOfWeek: "{{dayOfWeek}}"
    sourceERP: "https://erp.company.com"
    stagingArea: "/staging/finance"

  # Lines 22-32: Global conditions that can be referenced by transfer groups
  conditions:
    - name: is-month-end
      # Line 24: Expression evaluates to true if day of month >= 28
      expression: "{{dayOfMonth}} >= 28"
      description: "True during the last few days of the month"

    - name: is-business-day
      # Line 28: Expression checks if it's Monday through Friday
      expression: "{{dayOfWeek}} >= 1 && {{dayOfWeek}} <= 5"
      description: "True on weekdays (Monday-Friday)"

    - name: is-production
      expression: "{{environment}} == 'production'"
      description: "True when running in production environment"

  execution:
    strategy: mixed
    parallelism: 5
    timeout: 7200    # 2 hours

  transferGroups:
    # Always run: Daily data extraction
    - name: daily-extraction
      description: "Extract daily transaction data"
      # Line 43: Only run if it's a business day AND we're in production
      condition: "is-business-day && is-production"
      transfers:
        - name: extract-daily-transactions
          source: "{{sourceERP}}/api/transactions/daily?date={{processingDate}}"
          destination: "{{stagingArea}}/daily-transactions-{{processingDate}}.json"

    # Conditional: Month-end processing
    - name: month-end-processing
      description: "Additional processing required at month-end"
      dependsOn: [daily-extraction]
      # Line 52: Only run during month-end periods
      condition: "is-month-end && is-production"
      transfers:
        - name: extract-month-end-adjustments
          source: "{{sourceERP}}/api/adjustments/month-end?date={{processingDate}}"
          destination: "{{stagingArea}}/month-end-adjustments-{{processingDate}}.json"

        - name: generate-monthly-report
          source: "{{stagingArea}}/daily-transactions-{{processingDate}}.json"
          destination: "/reports/monthly-summary-{{processingDate}}.pdf"
          # Lines 60-64: Transfer-specific options for report generation
          options:
            timeout: 1800        # 30 minutes for report generation
            reportFormat: "pdf"  # Custom option for report type
            includeCharts: true  # Custom option for visual elements
```

**Conditional Logic Explained:**
- **Lines 22-32**: `conditions` section defines reusable boolean expressions
- **Line 24**: `{{dayOfMonth}} >= 28` uses a built-in function in an expression
- **Line 28**: `&&` is the logical AND operator for combining conditions
- **Line 43**: `condition` field references defined conditions with logical operators
- **Line 52**: Conditions can be combined with `&&` (AND) and `||` (OR)
- **Lines 60-64**: Custom options can be passed to specialized transfer handlers

##### Adding Error Handling and Notifications - Production Example

Finally, let's add comprehensive error handling and notifications for production use:

```yaml
metadata:
  name: "Production Financial Pipeline"
  version: "3.0.0"
  description: "Production-ready financial data pipeline with comprehensive error handling and compliance features"
  type: "financial-data-workflow"
  author: "financial-systems@company.com"
  created: "2025-08-21"
  tags: ["production", "financial", "compliance", "error-handling", "sox"]
  # Line 8: Tenant specifies which organization owns this workflow
  tenant: acme-corporation
  # Line 10: Namespace provides additional isolation within the tenant
  namespace: finance-operations
  labels:
    environment: production
    criticality: high
    compliance: sox-required
    # Line 15: Data classification affects security and retention policies
    dataClassification: confidential
  # Lines 16-24: Annotations provide extended metadata for governance
  annotations:
    owner: "finance-data-team@acme-corp.com"
    businessOwner: "cfo@acme-corp.com"
    documentation: "https://wiki.acme-corp.com/finance/daily-pipeline"
    approvedBy: "finance-director@acme-corp.com"
    approvalDate: "2025-01-15"
    complianceReviewed: "2025-01-10"

spec:
  variables:
    processingDate: "{{today}}"
    environment: "production"
    sourceERP: "https://erp.acme-corp.internal"
    stagingArea: "/corporate-data/staging/finance"
    warehouseArea: "/corporate-data/warehouse/finance"
    # Line 32: Email addresses for different types of notifications
    opsTeamEmail: "finance-ops@acme-corp.com"
    managementEmail: "finance-director@acme-corp.com"

  conditions:
    - name: is-production
      expression: "{{environment}} == 'production'"
    - name: is-business-day
      expression: "{{dayOfWeek}} >= 1 && {{dayOfWeek}} <= 5"

  execution:
    strategy: mixed
    parallelism: 3
    timeout: 14400    # 4 hours maximum
    # Lines 44-49: Workflow-level retry policy
    retryPolicy:
      maxAttempts: 2           # Retry entire workflow up to 2 times
      backoffMultiplier: 2.0   # Double the wait time between retries
      initialDelay: 300        # Wait 5 minutes before first retry
      jitterEnabled: true      # Add randomness to prevent thundering herd

  transferGroups:
    - name: extract-financial-data
      description: "Extract daily financial data from ERP system"
      condition: "is-production && is-business-day"
      # Line 54: Continue processing other groups even if this one fails
      continueOnError: false    # Stop workflow if extraction fails (critical data)
      # Line 56: Maximum parallel transfers within this group
      maxParallelTransfers: 2
      transfers:
        - name: extract-transactions
          source: "{{sourceERP}}/api/transactions/export?date={{processingDate}}"
          destination: "{{stagingArea}}/transactions-{{processingDate}}.json"
          # Lines 61-70: Comprehensive transfer options
          options:
            timeout: 1800                    # 30 minutes for this transfer
            checksumValidation: true         # Verify file integrity
            checksumAlgorithm: "SHA256"     # Use SHA-256 for checksums
            retryAttempts: 5                # Retry this transfer up to 5 times
            retryBackoffMultiplier: 2.0     # Double wait time between retries
            initialRetryDelay: 60           # Wait 1 minute before first retry
            compressionEnabled: true        # Enable compression for large files
          # Lines 68-74: Authentication configuration for secure API access
          credentials:
            type: "oauth2"                  # Use OAuth 2.0 authentication
            tokenEndpoint: "{{sourceERP}}/oauth/token"
            clientId: "quorus-finance-client"
            # Line 72: Reference to securely stored client secret
            clientSecret: "{{vault:erp-client-secret}}"
            scope: "transactions:read"      # Minimal required permissions

        - name: extract-accounts
          source: "{{sourceERP}}/api/accounts/export?date={{processingDate}}"
          destination: "{{stagingArea}}/accounts-{{processingDate}}.json"
          # Line 78: This transfer depends on transactions completing first
          dependsOn: [extract-transactions]
          options:
            timeout: 900        # 15 minutes (smaller dataset)
            checksumValidation: true
            retryAttempts: 3

    - name: validate-and-process
      description: "Validate data integrity and process for warehouse loading"
      dependsOn: [extract-financial-data]
      # Line 87: Continue with other groups even if validation has issues
      continueOnError: true
      transfers:
        - name: validate-data-integrity
          source: "{{stagingArea}}/transactions-{{processingDate}}.json"
          destination: "{{stagingArea}}/validation-report-{{processingDate}}.json"
          options:
            # Line 93: Custom validation script execution
            executeScript: true
            scriptPath: "/scripts/validate-financial-data.py"
            scriptArguments: ["--date", "{{processingDate}}", "--strict-mode"]
            timeout: 600        # 10 minutes for validation
            # Lines 97-102: Error handling for validation failures
            onError:
              action: "continue"              # Continue workflow even if validation fails
              logLevel: "WARN"               # Log validation failures as warnings
              notification: "{{opsTeamEmail}}" # Notify ops team of validation issues
              createTicket: true             # Automatically create support ticket
              ticketPriority: "medium"       # Set ticket priority level

    - name: load-to-warehouse
      description: "Load processed data into corporate data warehouse"
      dependsOn: [validate-and-process]
      continueOnError: false    # Critical final step - must succeed
      transfers:
        - name: load-transactions
          source: "{{stagingArea}}/transactions-{{processingDate}}.json"
          destination: "{{warehouseArea}}/transactions/{{processingDate}}.parquet"
          options:
            timeout: 2700       # 45 minutes for warehouse loading
            # Line 113: Convert JSON to Parquet format for analytics
            outputFormat: "parquet"
            compressionCodec: "snappy"      # Efficient compression for analytics
            # Line 116: Partition data by date for query performance
            partitionBy: ["transaction_date"]
            # Lines 117-122: Warehouse-specific options
            warehouseOptions:
              updateMode: "append"          # Add new data without replacing existing
              createIndexes: true          # Create database indexes for performance
              updateStatistics: true      # Update table statistics for query optimizer
              validateSchema: true        # Ensure data matches expected schema

  # Lines 123-150: Comprehensive notification configuration
  notifications:
    # Success notifications - sent when workflow completes successfully
    onSuccess:
      - type: email
        # Line 127: Send success notification to operations team
        recipients: ["{{opsTeamEmail}}"]
        subject: "✅ Financial Data Pipeline Completed - {{processingDate}}"
        # Line 130: Use predefined email template
        template: "workflow-success-template"
        # Line 132: Include performance metrics in the email
        includeMetrics: true
        attachments:
          - "{{stagingArea}}/validation-report-{{processingDate}}.json"

      - type: slack
        # Line 137: Also notify via Slack for immediate visibility
        channel: "#finance-data-ops"
        message: "✅ Daily financial pipeline completed successfully for {{processingDate}}"
        # Line 140: Include key metrics in Slack message
        includeMetrics: true

    # Failure notifications - sent when workflow fails
    onFailure:
      - type: email
        # Line 145: Escalate failures to both ops and management
        recipients: ["{{opsTeamEmail}}", "{{managementEmail}}"]
        subject: "🚨 URGENT: Financial Pipeline Failed - {{processingDate}}"
        template: "workflow-failure-template"
        # Line 149: Include detailed error information and logs
        includeErrorDetails: true
        includeLogs: true
        # Line 152: Mark as high priority for email filtering
        priority: "high"

      - type: slack
        channel: "#finance-data-ops"
        message: "🚨 CRITICAL: Financial pipeline FAILED for {{processingDate}} - immediate attention required!"
        # Line 157: Mention specific people for urgent issues
        mentions: ["@finance-ops-oncall", "@data-team-lead"]

      - type: webhook
        # Line 160: Integrate with incident management system
        url: "https://alerts.acme-corp.com/api/incidents"
        method: "POST"
        headers:
          Authorization: "Bearer {{vault:alerting-system-token}}"
          Content-Type: "application/json"
        # Lines 165-171: Structured incident data
        payload:
          severity: "high"
          service: "finance-data-pipeline"
          environment: "{{environment}}"
          description: "Financial data pipeline execution failed"
          impact: "Financial reporting may be delayed"
          urgency: "high"

    # Progress notifications - optional periodic updates
    onProgress:
      # Line 174: Enable progress notifications every 30 minutes
      enabled: true
      intervalMinutes: 30
      recipients: ["{{opsTeamEmail}}"]
      # Line 178: Only send progress updates for long-running workflows
      minimumDurationMinutes: 60

  # Lines 180-190: Cleanup and maintenance configuration
  cleanup:
    enabled: true
    # Line 183: Keep staging files for 7 days for troubleshooting
    retainStagingDays: 7
    # Line 185: Keep detailed logs for 30 days for audit purposes
    retainLogsDays: 30
    # Line 187: Automatically archive completed workflow metadata
    archiveEnabled: true
    archiveLocation: "/corporate-data/archive/workflows"
    # Line 190: Compress archived data to save storage space
    compressionEnabled: true

  # Lines 191-196: Service Level Agreement definitions
  sla:
    # Line 193: Maximum acceptable execution time
    maxExecutionTime: "PT4H"        # 4 hours (ISO 8601 duration format)
    # Line 195: Target execution time for performance monitoring
    targetExecutionTime: "PT2H"     # 2 hours target
    # Line 197: Minimum acceptable success rate
    availabilityTarget: 99.5        # 99.5% success rate required
```

**Production Features Explained:**
- **Lines 8-10**: `tenant` and `namespace` provide multi-tenant isolation
- **Lines 16-24**: `annotations` provide governance metadata for enterprise compliance
- **Line 32**: Variables can reference email addresses for dynamic notifications
- **Lines 44-49**: Workflow-level `retryPolicy` handles transient failures
- **Line 54**: `continueOnError: false` stops workflow if critical steps fail
- **Line 56**: `maxParallelTransfers` controls resource usage within groups
- **Lines 68-74**: `credentials` section handles secure authentication
- **Line 78**: Transfer-level `dependsOn` creates fine-grained dependencies
- **Lines 97-102**: `onError` configuration provides granular error handling
- **Lines 123-150**: Comprehensive `notifications` for different scenarios
- **Lines 180-190**: `cleanup` configuration manages storage and maintenance
- **Lines 191-196**: `sla` definitions enable performance monitoring

##### YAML Configuration Learning Path Summary

We've progressed through increasingly complex YAML configurations:

**1. Simple Example (15 lines)**
- Basic `apiVersion`, `kind`, `metadata`
- Single transfer group with one transfer
- Minimal required fields only

**2. Intermediate Example (28 lines)**
- Added `version` and `variables`
- Introduced `execution` configuration
- Transfer `options` for customization
- Variable substitution with `{{variable}}` syntax

**3. Multi-Stage Example (58 lines)**
- Multiple transfer groups with `dependsOn` relationships
- Built-in functions like `{{today}}`
- `labels` for categorization
- Data pipeline pattern (extract → process → load)

**4. Conditional Example (64 lines)**
- Global `conditions` with boolean expressions
- Conditional execution using `condition` fields
- Logical operators (`&&`, `||`) in expressions
- Business rule implementation

**5. Production Example (197 lines)**
- Complete enterprise metadata with `tenant`, `namespace`, `annotations`
- Comprehensive error handling with `continueOnError`, `onError`
- Authentication with `credentials`
- Multi-channel `notifications` (email, Slack, webhook)
- Resource management and `cleanup` policies
- Service Level Agreements (`sla`)

##### Key YAML Configuration Patterns

**Essential Structure Pattern:**
```yaml
metadata:                    # Always required - complete identification
  name: "Workflow Name"      # Always required - human-readable name
  version: "1.0.0"          # Always required - semantic version
  description: "Purpose"     # Always required - business description
  type: "workflow-type"      # Always required - workflow classification
  author: "owner@company.com" # Always required - contact information
  created: "2025-08-21"      # Always required - creation date
  tags: ["tag1", "tag2"]     # Always required - classification tags
spec:                        # Always required - workflow definition
  transferGroups:            # Always required - contains transfers
    - name: group-name       # Always required - group identifier
      transfers:             # Always required - list of transfers
        - name: transfer-name # Always required - transfer identifier
          source: "..."       # Always required - source location
          destination: "..."  # Always required - destination location
          protocol: https     # Always required - transfer protocol
```

**Variable Usage Pattern:**
```yaml
spec:
  variables:                 # Define reusable values
    baseUrl: "https://api.example.com"
    targetDir: "/storage"
    date: "{{today}}"        # Built-in functions
  transferGroups:
    - name: example
      transfers:
        - name: transfer
          source: "{{baseUrl}}/data.csv"     # Reference variables
          destination: "{{targetDir}}/{{date}}.csv"  # Combine variables
```

**Dependency Pattern:**
```yaml
transferGroups:
  - name: stage-1            # First stage - no dependencies
    transfers: [...]

  - name: stage-2
    dependsOn: [stage-1]     # Explicit dependency
    transfers: [...]

  - name: stage-3
    dependsOn: [stage-1, stage-2]  # Multiple dependencies
    transfers: [...]
```

**Conditional Execution Pattern:**
```yaml
conditions:                  # Define reusable conditions
  - name: is-production
    expression: "{{environment}} == 'production'"

transferGroups:
  - name: prod-only-group
    condition: is-production  # Reference condition
    transfers: [...]

  - name: complex-condition
    condition: "is-production && {{dayOfWeek}} <= 5"  # Combine conditions
    transfers: [...]
```

**Error Handling Pattern:**
```yaml
transferGroups:
  - name: critical-group
    continueOnError: false   # Stop workflow if this fails
    transfers:
      - name: critical-transfer
        source: "..."
        destination: "..."
        options:
          retryAttempts: 5   # Transfer-level retries
          onError:           # Transfer-level error handling
            action: "fail"
            notification: "admin@company.com"

  - name: optional-group
    continueOnError: true    # Continue workflow even if this fails
    transfers: [...]
```

**Notification Pattern:**
```yaml
notifications:
  onSuccess:                 # Workflow succeeded
    - type: email
      recipients: ["ops@company.com"]
      subject: "Workflow completed"

  onFailure:                 # Workflow failed
    - type: email
      recipients: ["ops@company.com", "manager@company.com"]
      subject: "URGENT: Workflow failed"
      priority: "high"

    - type: slack
      channel: "#alerts"
      message: "Workflow failed - immediate attention required"
```

##### Best Practices for YAML Configuration

**1. Start Simple, Add Complexity Gradually**
- Begin with basic transfer requirements
- Add configuration options as needed
- Implement error handling and notifications last

**2. Use Meaningful Names**
```yaml
# Good - descriptive names
- name: extract-customer-data
- name: validate-financial-records
- name: load-to-data-warehouse

# Bad - generic names
- name: step1
- name: transfer
- name: process
```

**3. Leverage Variables for Reusability**
```yaml
# Good - reusable configuration
variables:
  apiBase: "https://api.company.com"
  targetDir: "/data/{{department}}"

# Bad - hardcoded values repeated
source: "https://api.company.com/customers"
source: "https://api.company.com/orders"
```

**4. Document with Descriptions**
```yaml
# Good - well documented
- name: month-end-processing
  description: "Additional financial processing required at month-end for regulatory compliance"
  condition: "{{dayOfMonth}} >= 28"

# Bad - no documentation
- name: month-end-processing
  condition: "{{dayOfMonth}} >= 28"
```

**5. Use Appropriate Error Handling**
```yaml
# Critical data - fail fast
- name: financial-data-extract
  continueOnError: false

# Optional data - continue on error
- name: supplementary-data-extract
  continueOnError: true
```

This progressive approach helps users understand YAML configuration by building from simple concepts to complex enterprise scenarios, with each example building on the previous one while introducing new concepts gradually.

```yaml
# Metadata section - workflow identification and classification
metadata:
  # Complete metadata required for schema validation
  name: "Enterprise Financial Data Pipeline"
  version: "1.0.0"
  description: "Comprehensive financial data pipeline for enterprise reporting and compliance"
  type: "financial-data-workflow"
  author: "financial-engineering@company.com"
  created: "2025-08-21"
  tags: ["enterprise", "financial", "compliance", "reporting"]

  # Human-readable description for documentation
  description: |
    Comprehensive daily financial data synchronization pipeline that extracts
    data from multiple ERP systems, validates data integrity, transforms data
    for analytics, and loads into the corporate data warehouse with full
    audit trail and compliance reporting.

  # Semantic version for workflow evolution tracking
  version: "2.3.1"

  # Tenant and namespace for multi-tenant isolation
  tenant: acme-corporation
  namespace: finance-operations

  # Labels for categorization and selection (key-value pairs)
  labels:
    environment: production           # Environment designation
    criticality: high                # Business criticality level
    schedule: daily                  # Execution frequency
    department: finance              # Owning department
    compliance: sox-required         # Compliance requirements
    dataClassification: confidential # Data sensitivity level
    automationLevel: full           # Level of automation

  # Annotations for extended metadata (not used for selection)
  annotations:
    # Business ownership and contact information
    owner: "finance-data-team@acme-corp.com"
    businessOwner: "cfo@acme-corp.com"
    technicalContact: "data-ops@acme-corp.com"

    # Documentation and reference links
    documentation: "https://wiki.acme-corp.com/finance/data-pipeline"
    runbook: "https://runbooks.acme-corp.com/finance/daily-sync"
    architecture: "https://confluence.acme-corp.com/finance/data-architecture"

    # Approval and governance information
    approvedBy: "finance-director@acme-corp.com"
    approvalDate: "2025-01-15"
    nextReview: "2025-07-15"
    complianceReviewed: "2025-01-10"

    # Change management
    lastModified: "2025-01-20T10:30:00Z"
    modifiedBy: "data-engineer@acme-corp.com"
    changeTicket: "CHG-2025-001234"

# Workflow specification - the core workflow definition
spec:
  # Global variables available to all transfers and conditions
  variables:
    # System endpoints and configurations
    sourceERP: "https://erp.acme-corp.internal"
    sourceCRM: "https://crm.acme-corp.internal"
    targetWarehouse: "/corporate-data-warehouse/finance"
    stagingArea: "/staging/finance"
    archiveLocation: "/archive/finance"

    # Date and time variables with formatting
    processingDate: "{{today}}"                    # 2025-01-20
    processingTimestamp: "{{timestamp}}"           # 2025-01-20T06:00:00Z
    yearMonth: "{{format(today, 'yyyy-MM')}}"     # 2025-01
    fiscalQuarter: "{{fiscalQuarter(today)}}"     # Q1-2025

    # Environment and configuration
    environment: "production"
    region: "us-east-1"
    dataCenter: "primary"

    # Business logic variables
    batchSize: 10000
    maxRetries: 5
    timeoutMinutes: 30

    # Notification and alerting
    primaryNotificationEmail: "finance-ops@acme-corp.com"
    escalationEmail: "finance-director@acme-corp.com"
    slackChannel: "#finance-data-ops"

    # Compliance and audit
    auditRequired: true
    retentionPeriodDays: 2555  # 7 years for financial data
    encryptionRequired: true

  # Execution configuration
  execution:
    # Execution strategy determines how transfer groups are executed
    strategy: mixed  # Options: sequential, parallel, mixed

    # Maximum number of parallel transfers across all groups
    parallelism: 8

    # Global timeout for entire workflow (in seconds)
    timeout: 14400  # 4 hours

    # Workflow-level retry policy
    retryPolicy:
      maxAttempts: 3
      backoffMultiplier: 2.0
      initialDelay: 300        # 5 minutes
      maxDelay: 1800          # 30 minutes
      jitterEnabled: true     # Add randomness to prevent thundering herd

    # Resource constraints
    resources:
      maxMemoryMB: 2048
      maxCpuCores: 4
      maxNetworkBandwidthMbps: 100

    # Execution preferences
    preferences:
      preferredAgents: ["agent-finance-01", "agent-finance-02"]
      avoidAgents: ["agent-maintenance"]
      requireDedicatedAgents: false

  # Global conditions that can be referenced by transfer groups
  conditions:
    # Environment-based conditions
    - name: production-only
      description: "Execute only in production environment"
      expression: "{{environment}} == 'production'"

    - name: business-hours
      description: "Execute only during business hours (6 AM - 10 PM EST)"
      expression: "{{hour}} >= 6 && {{hour}} <= 22"

    - name: weekday-only
      description: "Execute only on weekdays"
      expression: "{{dayOfWeek}} >= 1 && {{dayOfWeek}} <= 5"

    - name: month-end
      description: "Execute additional processing at month end"
      expression: "{{dayOfMonth}} >= 28"

    - name: quarter-end
      description: "Execute quarterly processing"
      expression: "{{isQuarterEnd(today)}}"

    # Data availability conditions
    - name: source-data-available
      description: "Check if source data is available"
      expression: "{{checkDataAvailability(sourceERP, processingDate)}}"

  # Transfer groups - logical groupings of related transfers
  transferGroups:
    # Stage 1: Data Extraction from Source Systems
    - name: extract-master-data
      description: |
        Extract master data from ERP and CRM systems including customer
        information, product catalogs, and organizational hierarchies.
        This data forms the foundation for all subsequent processing.

      # Execution conditions for this group
      condition: production-only

      # Group-level configuration
      continueOnError: false  # Fail entire group if any transfer fails
      maxParallelTransfers: 3

      # Individual transfers within the group
      transfers:
        - name: extract-customer-master
          description: "Extract customer master data from ERP system"
          source: "{{sourceERP}}/api/v2/customers/export"
          destination: "{{stagingArea}}/raw/customers/{{processingDate}}.json"
          protocol: https

          # Transfer-specific options
          options:
            timeout: 1800  # 30 minutes
            checksumValidation: true
            checksumAlgorithm: SHA256
            retryAttempts: 3
            chunkSize: 10485760  # 10MB chunks
            compressionEnabled: true

          # Authentication for this specific transfer
          credentials:
            type: oauth2
            tokenEndpoint: "{{sourceERP}}/oauth/token"
            clientId: "quorus-finance-client"
            clientSecret: "{{vault:erp-client-secret}}"
            scope: "customers:read"

          # Transfer-specific metadata
          metadata:
            dataType: "customer-master"
            sensitivity: "confidential"
            retention: "{{retentionPeriodDays}}"

        - name: extract-product-catalog
          description: "Extract product catalog from ERP system"
          source: "{{sourceERP}}/api/v2/products/export?includeInactive=false"
          destination: "{{stagingArea}}/raw/products/{{processingDate}}.json"
          protocol: https

          # This transfer can run in parallel with customer extraction
          dependsOn: []  # No dependencies within this group

          options:
            timeout: 900   # 15 minutes (smaller dataset)
            checksumValidation: true
            retryAttempts: 3

        - name: extract-chart-of-accounts
          description: "Extract chart of accounts and GL structure"
          source: "{{sourceERP}}/api/v2/accounting/coa/export"
          destination: "{{stagingArea}}/raw/coa/{{processingDate}}.json"
          protocol: https

          # This should run after customer master for referential integrity
          dependsOn: [extract-customer-master]

          options:
            timeout: 600   # 10 minutes
            checksumValidation: true

    # Stage 2: Transactional Data Extraction
    - name: extract-transactional-data
      description: |
        Extract transactional data including sales orders, invoices, payments,
        and journal entries. This data depends on master data for referential
        integrity validation.

      # This group depends on master data extraction
      dependsOn: [extract-master-data]
      condition: production-only
      continueOnError: false
      maxParallelTransfers: 4

      transfers:
        - name: extract-sales-orders
          description: "Extract sales orders for processing date"
          source: "{{sourceERP}}/api/v2/sales/orders/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/sales-orders/{{processingDate}}.json"
          protocol: https

          options:
            timeout: 2700  # 45 minutes (large dataset)
            checksumValidation: true
            retryAttempts: 5  # Higher retry for critical data

        - name: extract-invoices
          description: "Extract invoice data for processing date"
          source: "{{sourceERP}}/api/v2/accounting/invoices/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/invoices/{{processingDate}}.json"
          protocol: https

          # Can run in parallel with sales orders
          dependsOn: []

          options:
            timeout: 1800  # 30 minutes
            checksumValidation: true

        - name: extract-payments
          description: "Extract payment transactions"
          source: "{{sourceERP}}/api/v2/accounting/payments/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/payments/{{processingDate}}.json"
          protocol: https

          # Payments should be extracted after invoices for reconciliation
          dependsOn: [extract-invoices]

        - name: extract-journal-entries
          description: "Extract general ledger journal entries"
          source: "{{sourceERP}}/api/v2/accounting/journal/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/journal/{{processingDate}}.json"
          protocol: https

          # Only extract if month-end processing is required
          condition: month-end

    # Stage 3: Data Validation and Quality Checks
    - name: data-validation
      description: |
        Perform comprehensive data validation including referential integrity
        checks, business rule validation, and data quality assessments.
        Generate validation reports for audit purposes.

      dependsOn: [extract-master-data, extract-transactional-data]
      condition: production-only
      continueOnError: true  # Continue even if some validations fail

      transfers:
        - name: validate-referential-integrity
          description: "Validate referential integrity between datasets"
          source: "{{stagingArea}}/scripts/validate-integrity.py"
          destination: "{{stagingArea}}/validation/integrity-{{processingDate}}.log"
          protocol: file

          # Custom validation script execution
          options:
            executeScript: true
            scriptArguments:
              - "--date={{processingDate}}"
              - "--input-dir={{stagingArea}}/raw"
              - "--output-dir={{stagingArea}}/validation"

        - name: data-quality-assessment
          description: "Perform data quality checks and generate metrics"
          source: "{{stagingArea}}/scripts/quality-check.py"
          destination: "{{stagingArea}}/quality/quality-report-{{processingDate}}.json"
          protocol: file

          dependsOn: [validate-referential-integrity]

        - name: generate-audit-report
          description: "Generate audit report for compliance"
          source: "{{stagingArea}}/validation"
          destination: "{{archiveLocation}}/audit/audit-report-{{processingDate}}.pdf"
          protocol: file

          # Only generate if audit is required
          condition: "{{auditRequired}} == true"

          options:
            reportFormat: "pdf"
            includeMetrics: true
            digitalSignature: true

    # Stage 4: Data Transformation and Processing
    - name: data-transformation
      description: |
        Transform raw data into analytics-ready format including data
        cleansing, standardization, enrichment, and aggregation.

      dependsOn: [data-validation]
      condition: business-hours  # Only during business hours for resource management
      continueOnError: false
      maxParallelTransfers: 2  # Resource-intensive operations

      transfers:
        - name: transform-customer-data
          description: "Transform and enrich customer data"
          source: "{{stagingArea}}/raw/customers/{{processingDate}}.json"
          destination: "{{targetWarehouse}}/processed/customers/{{processingDate}}.parquet"
          protocol: file

          options:
            transformationScript: "{{stagingArea}}/transforms/customer-transform.sql"
            outputFormat: "parquet"
            compressionCodec: "snappy"
            partitionBy: ["region", "customer_type"]

        - name: transform-financial-data
          description: "Transform financial transactions"
          source: "{{stagingArea}}/raw/invoices/{{processingDate}}.json"
          destination: "{{targetWarehouse}}/processed/financials/{{processingDate}}.parquet"
          protocol: file

          dependsOn: [transform-customer-data]  # Needs customer data for enrichment

          options:
            transformationScript: "{{stagingArea}}/transforms/financial-transform.sql"
            outputFormat: "parquet"

    # Stage 5: Data Loading and Publishing
    - name: data-loading
      description: |
        Load processed data into analytics systems and publish to
        downstream consumers including BI tools and reporting systems.

      dependsOn: [data-transformation]
      condition: business-hours
      continueOnError: true  # Some downstream systems may be temporarily unavailable

      transfers:
        - name: load-data-warehouse
          description: "Load data into primary data warehouse"
          source: "{{targetWarehouse}}/processed"
          destination: "analytics://warehouse/finance/{{processingDate}}"
          protocol: analytics

          options:
            loadStrategy: "upsert"  # Update existing, insert new
            indexing: true
            statisticsUpdate: true

        - name: publish-to-bi-system
          description: "Publish data to business intelligence system"
          source: "{{targetWarehouse}}/processed"
          destination: "bi://finance-mart/{{processingDate}}"
          protocol: bi

          # Can run in parallel with data warehouse loading
          dependsOn: []

        - name: update-data-catalog
          description: "Update enterprise data catalog with new datasets"
          source: "{{targetWarehouse}}/processed"
          destination: "catalog://finance/datasets/{{processingDate}}"
          protocol: catalog

          options:
            updateMetadata: true
            generateLineage: true

  # Workflow-level notifications
  notifications:
    # Success notifications
    onSuccess:
      - type: email
        recipients: ["{{primaryNotificationEmail}}"]
        subject: "Finance Data Pipeline Completed Successfully - {{processingDate}}"
        template: "workflow-success"
        includeMetrics: true

      - type: slack
        channel: "{{slackChannel}}"
        message: "✅ Finance data pipeline completed successfully for {{processingDate}}"

    # Failure notifications
    onFailure:
      - type: email
        recipients: ["{{primaryNotificationEmail}}", "{{escalationEmail}}"]
        subject: "URGENT: Finance Data Pipeline Failed - {{processingDate}}"
        template: "workflow-failure"
        includeErrorDetails: true
        includeLogs: true

      - type: slack
        channel: "{{slackChannel}}"
        message: "🚨 Finance data pipeline FAILED for {{processingDate}} - immediate attention required"

      - type: webhook
        url: "https://alerts.acme-corp.com/api/incidents"
        method: POST
        headers:
          Authorization: "Bearer {{vault:alerting-token}}"
        payload:
          severity: "high"
          service: "finance-data-pipeline"
          description: "Workflow execution failed"

    # Progress notifications (optional)
    onProgress:
      enabled: true
      intervalMinutes: 30
      recipients: ["{{primaryNotificationEmail}}"]

  # Cleanup and maintenance
  cleanup:
    # Automatic cleanup of staging data
    enabled: true
    retainStagingDays: 7
    retainLogsDays: 30

    # Archive completed workflows
    archiveEnabled: true
    archiveLocation: "{{archiveLocation}}/workflows"

  # Service Level Agreements
  sla:
    maxExecutionTime: "PT4H"      # 4 hours maximum
    targetExecutionTime: "PT2H"   # 2 hours target
    availabilityTarget: 99.5      # 99.5% success rate

  # Monitoring and observability
  monitoring:
    metricsEnabled: true
    tracingEnabled: true
    customMetrics:
      - name: "records_processed"
        type: "counter"
        description: "Number of records processed"

      - name: "data_quality_score"
        type: "gauge"
        description: "Overall data quality score"
```

#### Dependency Resolution Engine

The **Dependency Resolution Engine** is a sophisticated system that analyzes workflow definitions, builds dependency graphs, and creates optimal execution plans. It handles complex dependency scenarios while ensuring data consistency and resource optimization.

##### Dependency Types and Analysis

**1. Explicit Dependencies**
Explicitly defined via `dependsOn` clauses in the workflow definition:

```yaml
transferGroups:
  - name: extract-customers
    transfers:
      - name: customer-data
        source: "{{sourceSystem}}/customers"
        destination: "{{staging}}/customers.json"

  - name: extract-orders
    # Explicit dependency - orders need customer data for validation
    dependsOn: [extract-customers]
    transfers:
      - name: order-data
        source: "{{sourceSystem}}/orders"
        destination: "{{staging}}/orders.json"

  - name: generate-reports
    # Multiple explicit dependencies
    dependsOn: [extract-customers, extract-orders]
    transfers:
      - name: customer-order-report
        source: "{{staging}}/customers.json,{{staging}}/orders.json"
        destination: "{{reports}}/customer-orders.pdf"
```

**2. Implicit Dependencies**
Automatically inferred from data flow patterns:

```yaml
transferGroups:
  - name: stage-1
    transfers:
      - name: raw-data-extract
        source: "{{source}}/raw-data.csv"
        destination: "{{staging}}/raw-data.csv"

  - name: stage-2
    transfers:
      - name: process-data
        # Implicit dependency inferred from source path
        source: "{{staging}}/raw-data.csv"  # Uses output from stage-1
        destination: "{{processed}}/clean-data.csv"

  - name: stage-3
    transfers:
      - name: final-report
        # Implicit dependency on processed data
        source: "{{processed}}/clean-data.csv"  # Uses output from stage-2
        destination: "{{reports}}/final-report.pdf"
```

**3. Conditional Dependencies**
Dependencies that apply only under certain conditions:

```yaml
conditions:
  - name: month-end
    expression: "{{dayOfMonth}} >= 28"
  - name: quarter-end
    expression: "{{isQuarterEnd(today)}}"

transferGroups:
  - name: daily-processing
    transfers:
      - name: daily-sales
        source: "{{source}}/daily-sales.csv"
        destination: "{{staging}}/daily-sales.csv"

  - name: monthly-processing
    # Conditional dependency - only runs at month-end
    dependsOn: [daily-processing]
    condition: month-end
    transfers:
      - name: monthly-aggregation
        source: "{{staging}}/daily-sales.csv"
        destination: "{{reports}}/monthly-summary.csv"

  - name: quarterly-processing
    # Conditional dependency with multiple conditions
    dependsOn: [daily-processing, monthly-processing]
    condition: quarter-end
    transfers:
      - name: quarterly-report
        source: "{{reports}}/monthly-summary.csv"
        destination: "{{reports}}/quarterly-report.pdf"
```

**4. Resource Dependencies**
Dependencies based on shared resources or capacity constraints:

```yaml
transferGroups:
  - name: high-bandwidth-group-1
    transfers:
      - name: large-file-1
        source: "{{source}}/large-file-1.zip"
        destination: "{{target}}/large-file-1.zip"
        options:
          bandwidthMbps: 500  # Requires significant bandwidth

  - name: high-bandwidth-group-2
    # Resource dependency - can't run simultaneously due to bandwidth limits
    resourceDependencies:
      - type: bandwidth
        constraint: "total_bandwidth < 1000"
        conflictsWith: [high-bandwidth-group-1]
    transfers:
      - name: large-file-2
        source: "{{source}}/large-file-2.zip"
        destination: "{{target}}/large-file-2.zip"
        options:
          bandwidthMbps: 600
```

##### Advanced Dependency Graph Analysis

```java
public class AdvancedDependencyGraph {
    private final Map<String, TransferGroup> groups;
    private final Map<String, Set<String>> dependencies;
    private final Map<String, Set<String>> conditionalDependencies;
    private final Map<String, ResourceConstraint> resourceConstraints;

    // Graph construction with validation
    public void addTransferGroup(TransferGroup group) {
        validateGroupDefinition(group);
        groups.put(group.getName(), group);

        // Build explicit dependencies
        for (String dependency : group.getDependsOn()) {
            addDependency(dependency, group.getName(), DependencyType.EXPLICIT);
        }

        // Infer implicit dependencies from data flow
        inferImplicitDependencies(group);

        // Process conditional dependencies
        processConditionalDependencies(group);

        // Analyze resource dependencies
        analyzeResourceDependencies(group);
    }

    // Implicit dependency inference
    private void inferImplicitDependencies(TransferGroup group) {
        for (Transfer transfer : group.getTransfers()) {
            String sourcePath = transfer.getSource();

            // Find groups that produce this source path
            for (TransferGroup otherGroup : groups.values()) {
                if (otherGroup.getName().equals(group.getName())) continue;

                for (Transfer otherTransfer : otherGroup.getTransfers()) {
                    if (sourcePath.equals(otherTransfer.getDestination())) {
                        // Implicit dependency found
                        addDependency(otherGroup.getName(), group.getName(),
                                    DependencyType.IMPLICIT);
                        logger.info("Inferred dependency: {} -> {} (data flow)",
                                  otherGroup.getName(), group.getName());
                    }
                }
            }
        }
    }

    // Advanced cycle detection with detailed reporting
    public CycleAnalysisResult detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<List<String>> cycles = new ArrayList<>();

        for (String group : groups.keySet()) {
            if (!visited.contains(group)) {
                List<String> currentPath = new ArrayList<>();
                if (detectCyclesDFS(group, visited, recursionStack, currentPath, cycles)) {
                    // Cycle detected
                }
            }
        }

        return new CycleAnalysisResult(cycles.isEmpty(), cycles,
                                     generateCycleResolutionSuggestions(cycles));
    }

    // Critical path analysis for performance optimization
    public CriticalPathAnalysis calculateCriticalPath() {
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();

        // Initialize distances
        for (String group : groups.keySet()) {
            distances.put(group, 0);
        }

        // Calculate longest path (critical path)
        List<String> topologicalOrder = topologicalSort();
        for (String group : topologicalOrder) {
            TransferGroup transferGroup = groups.get(group);
            int groupDuration = estimateGroupDuration(transferGroup);

            for (String dependent : getDependents(group)) {
                int newDistance = distances.get(group) + groupDuration;
                if (newDistance > distances.get(dependent)) {
                    distances.put(dependent, newDistance);
                    predecessors.put(dependent, group);
                }
            }
        }

        // Build critical path
        List<String> criticalPath = buildCriticalPath(distances, predecessors);
        int totalDuration = distances.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        return new CriticalPathAnalysis(criticalPath, totalDuration,
                                      identifyBottlenecks(criticalPath));
    }

    // Intelligent execution planning
    public ExecutionPlan createOptimalExecutionPlan(ExecutionStrategy strategy,
                                                   ResourceConstraints constraints) {
        // Validate dependencies
        CycleAnalysisResult cycleAnalysis = detectCycles();
        if (cycleAnalysis.hasCycles()) {
            throw new DependencyException("Circular dependencies detected: " +
                                        cycleAnalysis.getCycles());
        }

        // Calculate critical path
        CriticalPathAnalysis criticalPath = calculateCriticalPath();

        // Create execution stages based on strategy
        List<ExecutionStage> stages = switch (strategy) {
            case SEQUENTIAL -> createSequentialPlan();
            case PARALLEL -> createParallelPlan(constraints);
            case MIXED -> createMixedPlan(constraints, criticalPath);
            case ADAPTIVE -> createAdaptivePlan(constraints, criticalPath);
        };

        return new ExecutionPlan(stages, criticalPath, estimateTotalDuration(stages));
    }

    // Mixed execution strategy with intelligent optimization
    private List<ExecutionStage> createMixedPlan(ResourceConstraints constraints,
                                               CriticalPathAnalysis criticalPath) {
        List<ExecutionStage> stages = new ArrayList<>();
        Set<String> completed = new HashSet<>();

        while (completed.size() < groups.size()) {
            ExecutionStage stage = new ExecutionStage();

            // Find groups ready for execution
            Set<String> readyGroups = findReadyGroups(completed);

            // Prioritize critical path groups
            List<String> prioritizedGroups = prioritizeGroups(readyGroups, criticalPath);

            // Add groups to stage respecting resource constraints
            for (String group : prioritizedGroups) {
                if (stage.canAddGroup(groups.get(group), constraints)) {
                    stage.addGroup(groups.get(group));
                    completed.add(group);
                }
            }

            if (!stage.isEmpty()) {
                stages.add(stage);
            } else {
                // Deadlock detection
                throw new ExecutionPlanningException("Unable to create execution plan - " +
                                                   "possible resource deadlock");
            }
        }

        return optimizeStages(stages, constraints);
    }
}

// Supporting classes for dependency analysis
public class CycleAnalysisResult {
    private final boolean hasCycles;
    private final List<List<String>> cycles;
    private final List<String> resolutionSuggestions;

    // Methods for cycle analysis and resolution
}

public class CriticalPathAnalysis {
    private final List<String> criticalPath;
    private final int totalDuration;
    private final List<String> bottlenecks;
    private final Map<String, Integer> slackTimes;

    // Methods for critical path optimization
}

public class ExecutionStage {
    private final List<TransferGroup> groups;
    private final ResourceRequirements requirements;
    private final int estimatedDuration;

    public boolean canAddGroup(TransferGroup group, ResourceConstraints constraints) {
        ResourceRequirements newRequirements = requirements.add(group.getResourceRequirements());
        return constraints.canAccommodate(newRequirements);
    }
}
```

##### Execution Strategies with Detailed Examples

**1. Sequential Execution**
Execute transfer groups one at a time in dependency order:

```yaml
# Workflow configuration
execution:
  strategy: sequential

# Results in execution plan:
# Stage 1: [extract-customers]
# Stage 2: [extract-orders]
# Stage 3: [generate-reports]
```

**Benefits:**
- Predictable resource usage
- Simple error handling
- Guaranteed dependency satisfaction

**Use Cases:**
- Resource-constrained environments
- Critical workflows requiring careful control
- Debugging and testing scenarios

**2. Parallel Execution**
Execute all independent groups simultaneously:

```yaml
# Workflow configuration
execution:
  strategy: parallel
  parallelism: 10

# Results in execution plan:
# Stage 1: [extract-customers, extract-products, extract-vendors]
# Stage 2: [extract-orders, process-customers, process-products]
# Stage 3: [generate-reports, update-warehouse]
```

**Benefits:**
- Maximum throughput
- Optimal resource utilization
- Shortest total execution time

**Use Cases:**
- High-performance environments
- Independent data processing
- Batch processing scenarios

**3. Mixed Execution (Recommended)**
Intelligent optimization based on dependencies and resources:

```yaml
# Workflow configuration
execution:
  strategy: mixed
  parallelism: 5
  resources:
    maxMemoryMB: 4096
    maxCpuCores: 8
    maxNetworkBandwidthMbps: 1000

# Results in optimized execution plan:
# Stage 1: [extract-customers, extract-products] (parallel, independent)
# Stage 2: [extract-orders] (depends on customers)
# Stage 3: [process-customers, process-products] (parallel, sufficient resources)
# Stage 4: [generate-reports] (depends on all processing)
```

**Benefits:**
- Balanced performance and resource usage
- Intelligent dependency handling
- Adaptive to resource constraints

**Use Cases:**
- Production environments
- Complex workflows with mixed dependencies
- Resource-aware execution

**4. Adaptive Execution (Advanced)**
Dynamic optimization based on runtime conditions:

```yaml
# Workflow configuration
execution:
  strategy: adaptive
  adaptiveSettings:
    monitorPerformance: true
    adjustParallelism: true
    resourceThresholds:
      cpuUtilization: 80
      memoryUtilization: 75
      networkUtilization: 70
```

**Benefits:**
- Runtime optimization
- Self-tuning performance
- Automatic resource management

**Use Cases:**
- Long-running workflows
- Variable resource environments
- Performance-critical applications

#### Variable Resolution System

The **Variable Resolution System** is a sophisticated templating engine that provides dynamic configuration management, enabling workflows to adapt to different environments, dates, and runtime conditions. It supports complex expressions, built-in functions, and hierarchical variable resolution.

##### Variable Resolution Hierarchy

Variables are resolved in the following precedence order (highest to lowest):

**1. Runtime Context Variables**
Variables provided at workflow execution time:

```bash
# Command line execution with runtime variables
quorus-workflow execute daily-sync.yaml \
  --variable processingDate=2025-01-20 \
  --variable environment=production \
  --variable urgentMode=true

# REST API execution with runtime variables
POST /api/v1/workflows
{
  "workflowDefinition": "...",
  "runtimeVariables": {
    "processingDate": "2025-01-20",
    "environment": "production",
    "urgentMode": true,
    "customBatchSize": 50000
  }
}
```

**2. Workflow Definition Variables**
Variables defined in the workflow spec:

```yaml
spec:
  variables:
    # Static configuration
    sourceSystem: "https://erp.corp.internal"
    targetWarehouse: "/data-warehouse/finance"

    # Dynamic variables using built-in functions
    processingDate: "{{today}}"
    processingTimestamp: "{{timestamp}}"

    # Computed variables using other variables
    archivePath: "{{targetWarehouse}}/archive/{{processingDate}}"
    logFile: "{{targetWarehouse}}/logs/workflow-{{processingTimestamp}}.log"
```

**3. Tenant Configuration Variables**
Tenant-specific default values:

```yaml
# Tenant configuration (managed separately)
tenantDefaults:
  acme-corp:
    defaultTimeout: 1800
    maxRetries: 5
    notificationEmail: "ops@acme-corp.com"
    dataRetentionDays: 2555
    encryptionRequired: true
```

**4. System Default Variables**
Global system defaults:

```yaml
# System defaults (built into Quorus)
systemDefaults:
  defaultTimeout: 300
  maxRetries: 3
  checksumValidation: true
  compressionEnabled: false
  parallelism: 3
```

**5. Built-in Functions and System Variables**
System-provided functions and runtime values.

##### Comprehensive Built-in Functions

**Date and Time Functions:**
```yaml
variables:
  # Basic date functions
  today: "{{today}}"                           # 2025-01-20
  yesterday: "{{yesterday}}"                   # 2025-01-19
  tomorrow: "{{tomorrow}}"                     # 2025-01-21

  # Formatted dates
  todayFormatted: "{{format(today, 'yyyy-MM-dd')}}"      # 2025-01-20
  monthYear: "{{format(today, 'yyyy-MM')}}"              # 2025-01
  yearOnly: "{{format(today, 'yyyy')}}"                  # 2025

  # Date arithmetic
  lastWeek: "{{addDays(today, -7)}}"                     # 2025-01-13
  nextMonth: "{{addMonths(today, 1)}}"                   # 2025-02-20
  quarterStart: "{{startOfQuarter(today)}}"              # 2025-01-01
  monthEnd: "{{endOfMonth(today)}}"                      # 2025-01-31

  # Time components
  currentHour: "{{hour}}"                      # 14 (2 PM)
  currentMinute: "{{minute}}"                  # 30
  dayOfWeek: "{{dayOfWeek}}"                   # 1 (Monday)
  dayOfMonth: "{{dayOfMonth}}"                 # 20
  dayOfYear: "{{dayOfYear}}"                   # 20
  weekOfYear: "{{weekOfYear}}"                 # 3

  # Timestamps
  timestamp: "{{timestamp}}"                   # 2025-01-20T14:30:00Z
  timestampMillis: "{{timestampMillis}}"       # 1737379800000
  isoTimestamp: "{{isoTimestamp}}"             # 2025-01-20T14:30:00.000Z

  # Business date functions
  isBusinessDay: "{{isBusinessDay(today)}}"    # true/false
  nextBusinessDay: "{{nextBusinessDay(today)}}" # 2025-01-21
  businessDaysInMonth: "{{businessDaysInMonth(today)}}" # 22

  # Fiscal calendar functions (configurable fiscal year start)
  fiscalYear: "{{fiscalYear(today)}}"          # 2025 (if fiscal year starts in January)
  fiscalQuarter: "{{fiscalQuarter(today)}}"    # Q1-2025
  fiscalMonth: "{{fiscalMonth(today)}}"        # 1 (first month of fiscal year)
  isQuarterEnd: "{{isQuarterEnd(today)}}"      # true/false
  isYearEnd: "{{isYearEnd(today)}}"            # true/false
```

**System and Environment Functions:**
```yaml
variables:
  # System information
  hostname: "{{hostname}}"                     # quorus-controller-01
  ipAddress: "{{ipAddress}}"                   # 10.0.1.100
  region: "{{region}}"                         # us-east-1
  availabilityZone: "{{availabilityZone}}"     # us-east-1a

  # Execution context
  tenant: "{{tenant}}"                         # acme-corp
  namespace: "{{namespace}}"                   # finance
  workflowName: "{{workflowName}}"             # daily-financial-sync
  executionId: "{{executionId}}"               # wf-exec-20250120-001

  # User context
  executedBy: "{{executedBy}}"                 # user@corp.com
  executionMode: "{{executionMode}}"           # normal/dry-run/virtual-run

  # Environment detection
  environment: "{{environment}}"               # production/staging/development
  isProduction: "{{environment == 'production'}}" # true/false
  isDevelopment: "{{environment == 'development'}}" # true/false
```

**Utility and Generation Functions:**
```yaml
variables:
  # Unique identifiers
  uuid: "{{uuid}}"                             # 550e8400-e29b-41d4-a716-446655440000
  shortUuid: "{{shortUuid}}"                   # a1b2c3d4
  correlationId: "{{correlationId}}"           # Unique per workflow execution

  # Random values
  randomNumber: "{{random(1000)}}"             # 0-999
  randomString: "{{randomString(8)}}"          # 8-character random string
  randomAlphanumeric: "{{randomAlphanumeric(12)}}" # 12-character alphanumeric

  # Encoding functions
  base64Encode: "{{base64Encode('hello world')}}" # aGVsbG8gd29ybGQ=
  base64Decode: "{{base64Decode('aGVsbG8gd29ybGQ=')}}" # hello world
  urlEncode: "{{urlEncode('hello world')}}"    # hello%20world

  # Hash functions
  md5Hash: "{{md5('hello world')}}"            # 5d41402abc4b2a76b9719d911017c592
  sha256Hash: "{{sha256('hello world')}}"      # b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
```

**String Manipulation Functions:**
```yaml
variables:
  # Case conversion
  upperCase: "{{upper(department)}}"           # FINANCE
  lowerCase: "{{lower(department)}}"           # finance
  titleCase: "{{title(department)}}"           # Finance
  camelCase: "{{camel(department_name)}}"      # departmentName

  # String operations
  substring: "{{substring(sourceFile, 0, 10)}}" # First 10 characters
  length: "{{length(sourceFile)}}"             # String length
  trim: "{{trim(inputString)}}"                # Remove whitespace

  # String replacement
  replace: "{{replace(sourceFile, '.csv', '.json')}}" # Replace extension
  replaceRegex: "{{replaceRegex(sourceFile, '[0-9]+', 'XXX')}}" # Replace numbers

  # String splitting and joining
  split: "{{split(csvList, ',')}}"             # Split into array
  join: "{{join(arrayVar, '|')}}"              # Join array with delimiter

  # String validation
  contains: "{{contains(sourceFile, 'customer')}}" # true/false
  startsWith: "{{startsWith(sourceFile, 'data')}}" # true/false
  endsWith: "{{endsWith(sourceFile, '.csv')}}"     # true/false
  matches: "{{matches(sourceFile, '[a-z]+\\.csv')}}" # Regex match
```

**Mathematical and Logical Functions:**
```yaml
variables:
  # Arithmetic operations
  sum: "{{add(batchSize, bufferSize)}}"        # Addition
  difference: "{{subtract(totalRecords, processedRecords)}}" # Subtraction
  product: "{{multiply(batchSize, parallelism)}}" # Multiplication
  quotient: "{{divide(totalSize, chunkSize)}}" # Division
  remainder: "{{mod(totalRecords, batchSize)}}" # Modulo

  # Mathematical functions
  absolute: "{{abs(difference)}}"              # Absolute value
  minimum: "{{min(timeout1, timeout2)}}"      # Minimum value
  maximum: "{{max(retries1, retries2)}}"      # Maximum value
  round: "{{round(averageSize)}}"              # Round to nearest integer
  ceiling: "{{ceil(averageSize)}}"             # Round up
  floor: "{{floor(averageSize)}}"              # Round down

  # Logical operations
  and: "{{and(isProduction, isBusinessDay)}}" # Logical AND
  or: "{{or(isUrgent, isHighPriority)}}"      # Logical OR
  not: "{{not(isTestMode)}}"                   # Logical NOT

  # Comparison functions
  equals: "{{eq(environment, 'production')}}" # Equality
  notEquals: "{{ne(status, 'failed')}}"       # Inequality
  greaterThan: "{{gt(fileSize, maxSize)}}"    # Greater than
  lessThan: "{{lt(retryCount, maxRetries)}}"  # Less than
  greaterEqual: "{{gte(progress, 50)}}"       # Greater than or equal
  lessEqual: "{{lte(errorRate, threshold)}}"  # Less than or equal
```

**Conditional and Control Flow Functions:**
```yaml
variables:
  # Simple conditional
  databaseUrl: "{{if(isProduction, 'prod-db.corp.com', 'test-db.corp.com')}}"

  # Multi-condition switch
  logLevel: "{{switch(environment,
              'production', 'WARN',
              'staging', 'INFO',
              'development', 'DEBUG',
              'INFO')}}"  # default value

  # Null coalescing
  timeout: "{{coalesce(customTimeout, defaultTimeout, 300)}}" # First non-null value

  # Default value assignment
  batchSize: "{{default(configuredBatchSize, 1000)}}" # Use default if null/empty

  # Conditional execution
  enableAudit: "{{if(and(isProduction, isFinancialData), true, false)}}"

  # Complex conditional logic
  processingMode: "{{if(isUrgent, 'fast',
                       if(isLargeDataset, 'batch', 'standard'))}}"
```

**Data Structure and Collection Functions:**
```yaml
variables:
  # Array operations
  arrayLength: "{{size(fileList)}}"            # Array size
  firstItem: "{{first(fileList)}}"             # First element
  lastItem: "{{last(fileList)}}"               # Last element
  arrayContains: "{{contains(fileList, 'data.csv')}}" # Contains check

  # Array manipulation
  sortedArray: "{{sort(fileList)}}"            # Sort array
  uniqueArray: "{{unique(fileList)}}"          # Remove duplicates
  reverseArray: "{{reverse(fileList)}}"        # Reverse order

  # Map/object operations
  mapKeys: "{{keys(configMap)}}"               # Get all keys
  mapValues: "{{values(configMap)}}"           # Get all values
  hasKey: "{{hasKey(configMap, 'timeout')}}"   # Check key existence
  getValue: "{{get(configMap, 'timeout', 300)}}" # Get value with default
```

**File and Path Functions:**
```yaml
variables:
  # Path manipulation
  fileName: "{{basename(sourceFile)}}"         # Extract filename
  directory: "{{dirname(sourceFile)}}"         # Extract directory
  extension: "{{extension(sourceFile)}}"       # Extract file extension
  nameWithoutExt: "{{nameWithoutExtension(sourceFile)}}" # Filename without extension

  # Path construction
  fullPath: "{{joinPath(baseDir, subDir, fileName)}}" # Join path components
  relativePath: "{{relativePath(basePath, fullPath)}}" # Make relative path

  # File information (if accessible)
  fileExists: "{{fileExists(sourceFile)}}"    # Check file existence
  fileSize: "{{fileSize(sourceFile)}}"         # Get file size
  lastModified: "{{lastModified(sourceFile)}}" # Last modification time
```

##### Advanced Variable Resolution Examples

**Complex Business Logic:**
```yaml
variables:
  # Dynamic source selection based on business rules
  primarySource: "{{if(isBusinessDay(today),
                      'https://erp.corp.com/api/daily',
                      'https://erp.corp.com/api/weekend')}}"

  # Conditional batch sizing based on data volume
  batchSize: "{{if(gt(estimatedRecords, 1000000), 50000,
                if(gt(estimatedRecords, 100000), 10000, 1000))}}"

  # Dynamic timeout based on file size and network conditions
  transferTimeout: "{{multiply(divide(fileSizeBytes, 1048576),
                              if(eq(networkQuality, 'high'), 2,
                                if(eq(networkQuality, 'medium'), 5, 10)))}}"

  # Compliance-based retention calculation
  retentionDays: "{{if(contains(dataClassification, 'financial'), 2555,
                    if(contains(dataClassification, 'personal'), 1095, 365)}}"
```

**Environment-Specific Configuration:**
```yaml
variables:
  # Environment-specific endpoints
  apiEndpoint: "{{switch(environment,
                  'production', 'https://api.corp.com',
                  'staging', 'https://api-staging.corp.com',
                  'development', 'https://api-dev.corp.com',
                  'http://localhost:8080')}}"

  # Environment-specific resource limits
  maxConcurrentTransfers: "{{switch(environment,
                            'production', 50,
                            'staging', 20,
                            'development', 5,
                            3)}}"

  # Environment-specific notification settings
  notificationLevel: "{{switch(environment,
                        'production', 'ERROR',
                        'staging', 'WARN',
                        'development', 'DEBUG',
                        'INFO')}}"
```

**Date-Based Processing Logic:**
```yaml
variables:
  # Month-end processing flag
  isMonthEnd: "{{gte(dayOfMonth, 28)}}"

  # Quarter-end additional processing
  includeQuarterlyReports: "{{isQuarterEnd(today)}}"

  # Business day processing schedule
  processingSchedule: "{{if(isBusinessDay(today),
                          if(eq(dayOfWeek, 1), 'monday-full',
                            if(eq(dayOfWeek, 5), 'friday-eod', 'daily-standard')),
                          'weekend-maintenance')}}"

  # Fiscal year-based archival
  archiveLocation: "{{joinPath(baseArchive, fiscalYear(today),
                               format(today, 'MM'), format(today, 'dd'))}}"
```

##### Variable Validation and Error Handling

**Variable Validation in YAML Workflows:**

```yaml
metadata:
  name: "Variable Validation Example"
  version: "1.0.0"
  description: "Demonstrates variable validation patterns and error handling"
  type: "validation-workflow"
  author: "config-team@company.com"
  created: "2025-08-21"
  tags: ["validation", "variables", "error-handling"]

spec:
  # Variable definitions with validation rules
  variableDefinitions:
    # String validation with pattern
    - name: "environment"
      type: "string"
      required: true
      pattern: "^(production|staging|development)$"
      description: "Deployment environment"

    # Number validation with range
    - name: "maxConcurrentTransfers"
      type: "number"
      required: true
      minimum: 1
      maximum: 100
      description: "Maximum concurrent transfers allowed"

    # Boolean validation
    - name: "enableRetries"
      type: "boolean"
      required: false
      default: true
      description: "Whether to enable retry logic"

    # Array validation
    - name: "allowedProtocols"
      type: "array"
      required: true
      items:
        type: "string"
        enum: ["http", "https", "ftp", "sftp", "smb"]
      description: "List of allowed transfer protocols"

  # Variables with validation
  variables:
    environment: "{{env.DEPLOYMENT_ENV | default('development')}}"
    maxConcurrentTransfers: "{{env.MAX_TRANSFERS | default(10) | toNumber}}"
    enableRetries: "{{env.ENABLE_RETRIES | default('true') | toBoolean}}"
    allowedProtocols: ["https", "sftp"]

    # Conditional validation
    apiEndpoint: "{{if(eq(environment, 'production'),
                      'https://api.company.com',
                      if(eq(environment, 'staging'),
                         'https://api-staging.company.com',
                         'http://localhost:8080'))}}"

  transferGroups:
    - name: validated-transfers
      transfers:
        - name: environment-aware-transfer
          source: "{{apiEndpoint}}/data.json"
          destination: "/data/environment-data.json"
          protocol: "{{first(allowedProtocols)}}"
          options:
            maxRetries: "{{if(enableRetries, 3, 0)}}"
            concurrency: "{{maxConcurrentTransfers}}"
```

**Error Handling for Variable Issues:**

```yaml
metadata:
  name: "Variable Error Handling"
  version: "1.0.0"
  description: "Demonstrates error handling for variable resolution issues"
  type: "error-handling-workflow"
  author: "reliability-team@company.com"
  created: "2025-08-21"
  tags: ["error-handling", "variables", "fallback"]

spec:
  variables:
    # Variable with fallback chain
    primarySource: "{{env.PRIMARY_SOURCE |
                     fallback(env.BACKUP_SOURCE) |
                     fallback('https://default-api.com')}}"

    # Variable with validation and error message
    requiredConfig: "{{env.REQUIRED_CONFIG |
                       required('REQUIRED_CONFIG environment variable must be set')}}"

    # Variable with type conversion and validation
    numericSetting: "{{env.NUMERIC_SETTING |
                       toNumber |
                       validate('must be between 1 and 100', gte(1), lte(100))}}"

    # Safe variable resolution with try-catch
    optionalFeature: "{{try(env.OPTIONAL_FEATURE | toBoolean, false)}}"

  # Error handling configuration
  errorHandling:
    onVariableError: "fail-fast"  # or "use-default", "skip-transfer"
    logLevel: "ERROR"
    notifyOnError: true

  transferGroups:
    - name: error-resilient-transfers
      transfers:
        - name: primary-transfer
          source: "{{primarySource}}/data.json"
          destination: "/data/primary.json"
          protocol: https
          onError:
            action: "retry"
            maxAttempts: 3

        - name: conditional-transfer
          # Only execute if optional feature is enabled
          condition: "{{optionalFeature}}"
          source: "{{primarySource}}/optional.json"
          destination: "/data/optional.json"
          protocol: https
```

**Preventing Circular References:**

```yaml
metadata:
  name: "Safe Variable References"
  version: "1.0.0"
  description: "Examples of safe variable referencing to avoid circular dependencies"
  type: "safe-variables-workflow"
  author: "config-team@company.com"
  created: "2025-08-21"
  tags: ["variables", "safe-patterns", "best-practices"]

spec:
  variables:
    # ✅ GOOD: Linear dependency chain
    baseUrl: "https://api.company.com"
    apiVersion: "v2"
    fullApiUrl: "{{baseUrl}}/{{apiVersion}}"
    dataEndpoint: "{{fullApiUrl}}/data"

    # ✅ GOOD: Independent variables
    environment: "{{env.ENVIRONMENT}}"
    region: "{{env.AWS_REGION}}"

    # ✅ GOOD: Using built-in functions (no circular risk)
    today: "{{date.today}}"
    timestamp: "{{date.now}}"

    # ❌ BAD: Would cause circular reference (detected and prevented)
    # varA: "{{varB}}/path"
    # varB: "{{varA}}/other"

    # ✅ GOOD: Conditional logic without circular references
    sourceType: "{{env.SOURCE_TYPE | default('api')}}"
    sourceUrl: "{{if(eq(sourceType, 'api'),
                    'https://api.company.com',
                    if(eq(sourceType, 'ftp'),
                       'ftp://files.company.com',
                       'file:///local/data'))}}"

  transferGroups:
    - name: safe-variable-usage
      transfers:
        - name: data-transfer
          source: "{{dataEndpoint}}"
          destination: "/data/{{format(today, 'yyyy-MM-dd')}}/data.json"
          protocol: https
```

This comprehensive variable resolution system enables workflows to be highly dynamic and adaptable while maintaining type safety and preventing common configuration errors.

### Multi-Tenancy Concepts

#### Tenant Hierarchy Model

The **Tenant Hierarchy** provides organizational structure and inheritance:

```mermaid
graph TB
    subgraph "Enterprise Tenant"
        ET[Enterprise Root]

        subgraph "Corporate Tenants"
            ACME[ACME Corp]
            PARTNER[Partner Corp]
        end

        subgraph "ACME Departments"
            FIN[Finance Dept]
            HR[HR Dept]
            IT[IT Dept]
        end

        subgraph "Finance Teams"
            ACC[Accounting Team]
            BUD[Budget Team]
            AUD[Audit Team]
        end
    end

    ET --> ACME
    ET --> PARTNER
    ACME --> FIN
    ACME --> HR
    ACME --> IT
    FIN --> ACC
    FIN --> BUD
    FIN --> AUD
```

**Tenant Properties:**
- **Identity**: Unique tenant ID and display name
- **Hierarchy**: Parent-child relationships with inheritance
- **Resources**: Quotas, limits, and usage tracking
- **Security**: Authentication providers and authorization policies
- **Storage**: Isolation strategy and storage configuration
- **Workflows**: Tenant-specific workflow definitions and templates

#### Resource Management System

The **Resource Management System** enforces quotas and tracks usage across the tenant hierarchy:

**Resource Types:**
```java
public enum ResourceType {
    // Transfer resources
    CONCURRENT_TRANSFERS,    // Number of simultaneous transfers
    TRANSFER_BANDWIDTH,      // Total bandwidth allocation
    TRANSFER_VOLUME,         // Data volume per time period

    // Storage resources
    STORAGE_QUOTA,           // Total storage allocation
    FILE_COUNT,              // Maximum number of files
    FILE_SIZE,               // Maximum individual file size

    // Compute resources
    CPU_ALLOCATION,          // CPU cores for processing
    MEMORY_ALLOCATION,       // Memory allocation

    // API resources
    API_REQUESTS,            // API calls per time period
    WORKFLOW_EXECUTIONS      // Workflow executions per time period
}
```

**Quota Enforcement:**
- **Hard Limits**: Strict enforcement that blocks operations
- **Soft Limits**: Warning thresholds with notification
- **Burst Capacity**: Temporary allowance above normal limits
- **Time-based Quotas**: Quotas that reset over time periods

**Usage Tracking:**
```java
public class ResourceUsage {
    private String tenantId;
    private ResourceType resourceType;
    private long currentUsage;
    private long quotaLimit;
    private Instant lastUpdated;
    private Map<String, Long> usageBreakdown; // by namespace, user, etc.

    // Usage calculations
    public double getUtilizationPercentage();
    public long getRemainingQuota();
    public boolean isOverQuota();
    public Duration getTimeUntilReset();
}
```

#### Isolation Strategies

The **Isolation System** provides different levels of tenant separation:

**1. Logical Isolation:**
- **Shared Infrastructure**: All tenants share the same physical resources
- **Namespace Separation**: Logical separation using tenant prefixes
- **Access Control**: RBAC-based access restrictions
- **Data Tagging**: Metadata-based tenant identification

**2. Physical Isolation:**
- **Dedicated Resources**: Separate infrastructure per tenant
- **Network Isolation**: VLANs or separate network segments
- **Storage Isolation**: Dedicated storage systems or volumes
- **Compute Isolation**: Dedicated servers or containers

**3. Hybrid Isolation:**
- **Sensitive Data**: Physical isolation for critical tenants
- **Regular Data**: Logical isolation for standard tenants
- **Flexible Migration**: Ability to move between isolation levels
- **Cost Optimization**: Balance security and cost requirements

This enhanced system design and functional concepts section provides deep technical insight into how Quorus operates internally, helping users understand not just how to use the system, but how it works under the hood to provide enterprise-grade file transfer capabilities.

## Getting Started

### Prerequisites

- **Java 21+** - Required for running Quorus components
- **Docker** (optional) - For containerized deployments
- **Network Access** - Internal corporate network connectivity
- **Storage Access** - Permissions to source and destination locations

### Quick Start

#### 1. Basic Installation

```bash
# Download and extract Quorus
wget https://releases.quorus.dev/v2.0/quorus-2.0.tar.gz
tar -xzf quorus-2.0.tar.gz
cd quorus-2.0

# Start the API service
java -jar quorus-api-2.0.jar

# Verify installation
curl http://localhost:8080/api/v1/info
```

#### 2. Docker Deployment

```bash
# Clone the repository
git clone https://github.com/your-org/quorus.git
cd quorus

# Start with Docker Compose
docker-compose up -d

# Check cluster status
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
```

#### 3. First Transfer

```bash
# Create a simple transfer job
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "https://httpbin.org/json",
    "destinationPath": "/tmp/test-download.json",
    "protocol": "http"
  }'
```

## Basic File Transfers

### Supported Protocols

| Protocol | Description | Use Case |
|----------|-------------|----------|
| HTTP/HTTPS | Web-based transfers | API downloads, web content |
| FTP | File Transfer Protocol | Legacy system integration |
| SFTP | Secure FTP | Secure file transfers |
| SMB | Server Message Block | Windows file shares |

### Simple Transfer Examples

#### HTTP Download

```java
// Java API Example
TransferEngine engine = new SimpleTransferEngine();

TransferRequest request = TransferRequest.builder()
    .sourceUri("https://api.internal.corp/data/export.csv")
    .destinationPath("/corporate-storage/imports/daily-export.csv")
    .protocol(TransferProtocol.HTTP)
    .checksumValidation(true)
    .build();

CompletableFuture<TransferResult> future = engine.submitTransfer(request);
TransferResult result = future.get();

if (result.isSuccessful()) {
    System.out.println("Transfer completed: " + result.getBytesTransferred() + " bytes");
} else {
    System.err.println("Transfer failed: " + result.getErrorMessage());
}
```

#### REST API Transfer

```bash
# Submit transfer job
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "sourceUri": "sftp://fileserver.corp.local/exports/data.zip",
    "destinationPath": "/data-warehouse/incoming/data.zip",
    "protocol": "sftp",
    "credentials": {
      "username": "transfer-user",
      "keyPath": "/etc/quorus/keys/transfer-key"
    },
    "options": {
      "checksumValidation": true,
      "retryAttempts": 3,
      "timeout": 300
    }
  }'

# Check transfer status
curl http://localhost:8080/api/v1/transfers/{job-id}/status
```

### Progress Monitoring

```bash
# Monitor transfer progress
curl http://localhost:8080/api/v1/transfers/{job-id}/progress

# Example response
{
  "jobId": "transfer-123",
  "status": "IN_PROGRESS",
  "bytesTransferred": 1048576,
  "totalBytes": 10485760,
  "progressPercentage": 10.0,
  "transferRate": 2097152,
  "estimatedTimeRemaining": 4.5,
  "startTime": "2025-01-20T10:00:00Z",
  "lastUpdate": "2025-01-20T10:00:30Z"
}
```

## YAML Workflows

### Basic Workflow Structure

```yaml
metadata:
  name: "Daily Data Sync"
  version: "1.0.0"
  description: "Daily synchronization of corporate data across multiple systems for business intelligence"
  type: "sync-workflow"
  author: "data-ops@acme-corp.com"
  created: "2025-08-21"
  tags: ["daily", "sync", "corporate", "multi-tenant"]
  # Multi-tenant specific metadata
  tenant: acme-corp
  namespace: finance

spec:
  variables:
    sourceApi: "https://crm.internal.corp"
    targetStorage: "/corporate-storage/data-warehouse"
    date: "{{today}}"
    
  execution:
    strategy: sequential  # or parallel, mixed
    parallelism: 3
    timeout: 3600
    
  transferGroups:
    - name: data-extraction
      description: Extract data from CRM system
      transfers:
        - name: customer-export
          source: "{{sourceApi}}/customers/export?date={{date}}"
          destination: "{{targetStorage}}/customers/{{date}}.json"
          protocol: http
          
        - name: orders-export
          source: "{{sourceApi}}/orders/export?date={{date}}"
          destination: "{{targetStorage}}/orders/{{date}}.json"
          protocol: http
          dependsOn: [customer-export]
```

### Advanced Workflow Features

#### Conditional Execution

```yaml
transferGroups:
  - name: conditional-processing
    condition: "{{environment}} == 'production'"
    transfers:
      - name: production-backup
        source: "/production/data"
        destination: "/backup/production/{{timestamp}}"
        protocol: file
```

#### Error Handling

```yaml
transferGroups:
  - name: resilient-transfers
    continueOnError: true
    retryPolicy:
      maxAttempts: 3
      backoffMultiplier: 2
      initialDelay: 5
    transfers:
      - name: critical-transfer
        source: "{{criticalSource}}"
        destination: "{{criticalDest}}"
        onError: 
          action: retry
          notification: "admin@corp.com"
```

#### Complex Dependencies

```yaml
transferGroups:
  - name: stage-1
    transfers:
      - name: extract-customers
      - name: extract-products
        
  - name: stage-2
    dependsOn: [stage-1]
    transfers:
      - name: process-data
        dependsOn: [extract-customers, extract-products]
        
  - name: stage-3
    dependsOn: [stage-2]
    transfers:
      - name: generate-reports
      - name: send-notifications
```

### Workflow Execution

#### Command Line

```bash
# Execute workflow
java -jar quorus-workflow-2.0.jar execute \
  --workflow-file daily-sync.yaml \
  --mode normal \
  --tenant acme-corp \
  --namespace finance

# Dry run (validation only)
java -jar quorus-workflow-2.0.jar execute \
  --workflow-file daily-sync.yaml \
  --mode dry-run

# Virtual run (simulation)
java -jar quorus-workflow-2.0.jar execute \
  --workflow-file daily-sync.yaml \
  --mode virtual-run
```

#### REST API

```bash
# Submit workflow
curl -X POST http://localhost:8080/api/v1/workflows \
  -H "Content-Type: application/yaml" \
  -H "X-Tenant: acme-corp" \
  -H "X-Namespace: finance" \
  --data-binary @daily-sync.yaml

# Monitor workflow execution
curl http://localhost:8080/api/v1/workflows/{execution-id}/status
```

#### Java API

```java
// Load and execute workflow
WorkflowDefinitionParser parser = new WorkflowDefinitionParser();
WorkflowDefinition workflow = parser.parseFromFile("daily-sync.yaml");

ExecutionContext context = ExecutionContext.builder()
    .mode(ExecutionContext.ExecutionMode.NORMAL)
    .tenant("acme-corp")
    .namespace("finance")
    .variable("today", LocalDate.now().toString())
    .build();

SimpleWorkflowEngine engine = new SimpleWorkflowEngine();
String executionId = engine.executeWorkflow(workflow, context);

// Monitor execution
WorkflowExecution execution = engine.getExecution(executionId);
System.out.println("Status: " + execution.getStatus());
```

## Multi-Tenant Operations

### Tenant Hierarchy

```mermaid
graph TB
    Enterprise[Enterprise Tenant]
    Enterprise --> Corp1[ACME Corp]
    Enterprise --> Corp2[Partner Corp]
    
    Corp1 --> Dept1[Finance Department]
    Corp1 --> Dept2[HR Department]
    Corp1 --> Dept3[IT Department]
    
    Dept1 --> Team1[Accounting Team]
    Dept1 --> Team2[Budgeting Team]
```

### Tenant Configuration

```yaml
# tenant-config.yaml
apiVersion: quorus.dev/v1
kind: TenantConfiguration
metadata:
  name: acme-corp
  parent: enterprise-tenant

spec:
  displayName: "ACME Corporation"
  description: "Primary corporate tenant for ACME Corp"
  
  resources:
    quotas:
      maxConcurrentTransfers: 50
      maxStorageGB: 1000
      maxBandwidthMbps: 100
    limits:
      maxFileSize: "10GB"
      maxTransferDuration: "24h"
      
  security:
    authentication:
      provider: "active-directory"
      domain: "corp.acme.com"
    authorization:
      defaultRole: "user"
      adminRole: "admin"
      
  storage:
    isolation: "logical"  # logical, physical, hybrid
    basePath: "/corporate-storage/acme-corp"
    encryption: true
    
  namespaces:
    - name: finance
      quotas:
        maxConcurrentTransfers: 20
        maxStorageGB: 500
    - name: hr
      quotas:
        maxConcurrentTransfers: 10
        maxStorageGB: 200
```

### Tenant Operations

#### Creating Tenants

```bash
# Create tenant via API
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/yaml" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  --data-binary @tenant-config.yaml
```

#### Tenant-Aware Transfers

```bash
# Transfer within tenant context
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "X-Tenant: acme-corp" \
  -H "X-Namespace: finance" \
  -d '{
    "sourceUri": "https://finance-api.corp/reports/monthly.pdf",
    "destinationPath": "/finance/reports/monthly-{{month}}.pdf"
  }'
```

#### Resource Monitoring

```bash
# Check tenant resource usage
curl http://localhost:8080/api/v1/tenants/acme-corp/usage

# Example response
{
  "tenant": "acme-corp",
  "currentUsage": {
    "concurrentTransfers": 15,
    "storageUsedGB": 450.5,
    "bandwidthUsedMbps": 25.3
  },
  "quotas": {
    "maxConcurrentTransfers": 50,
    "maxStorageGB": 1000,
    "maxBandwidthMbps": 100
  },
  "utilizationPercentage": {
    "transfers": 30.0,
    "storage": 45.1,
    "bandwidth": 25.3
  }
}
```

## REST API Usage

The Quorus REST API provides comprehensive programmatic access to all system functionality. Built on OpenAPI 3.0 specifications, it offers consistent, well-documented endpoints for integration with enterprise applications, automation systems, and custom tooling.

### API Architecture and Design Principles

#### RESTful Design Standards
The Quorus API follows REST architectural principles with:
- **Resource-Based URLs**: Clear, hierarchical resource paths (`/api/v1/transfers/{id}`)
- **HTTP Method Semantics**: Proper use of GET, POST, PUT, DELETE, PATCH
- **Stateless Operations**: Each request contains all necessary information
- **Consistent Response Formats**: Standardized JSON responses with predictable structure
- **HATEOAS Support**: Hypermedia links for resource navigation and discovery

#### API Versioning Strategy
```bash
# Current API version
GET /api/v1/transfers

# Future versions maintain backward compatibility
GET /api/v2/transfers  # Enhanced features
GET /api/v1/transfers  # Still supported for existing integrations
```

**Versioning Approach:**
- **URL Path Versioning**: Version specified in URL path for clarity
- **Backward Compatibility**: Previous versions supported for minimum 2 years
- **Deprecation Notices**: 6-month advance notice for version deprecation
- **Migration Guides**: Comprehensive guides for version upgrades

### Authentication and Authorization

Quorus supports multiple authentication methods designed for enterprise security requirements:

#### OAuth 2.0 / OpenID Connect Integration

```bash
# Step 1: Obtain access token from corporate identity provider
curl -X POST https://sso.corp.com/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=quorus-api-client" \
  -d "client_secret=your-client-secret" \
  -d "scope=quorus:transfers:read quorus:transfers:write quorus:workflows:execute"

# Response
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "quorus:transfers:read quorus:transfers:write quorus:workflows:execute"
}

# Step 2: Use token in API requests
curl -X GET http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Accept: application/json"
```

**OAuth 2.0 Grant Types Supported:**
- **Client Credentials**: Service-to-service authentication
- **Authorization Code**: Interactive user authentication
- **Resource Owner Password**: Legacy system integration (discouraged)
- **JWT Bearer**: Token exchange for federated scenarios

#### API Key Authentication

```bash
# API key in header (recommended)
curl -X GET http://localhost:8080/api/v1/transfers \
  -H "X-API-Key: qrs_live_1234567890abcdef" \
  -H "Accept: application/json"

# API key in query parameter (less secure, for testing only)
curl -X GET "http://localhost:8080/api/v1/transfers?api_key=qrs_live_1234567890abcdef"
```

**API Key Management:**
- **Key Rotation**: Automatic key rotation with configurable intervals
- **Scope Limitation**: Keys can be restricted to specific operations
- **IP Restrictions**: Limit key usage to specific IP ranges
- **Usage Monitoring**: Track API key usage and detect anomalies

#### Certificate-Based Authentication (mTLS)

```bash
# Mutual TLS authentication for high-security environments
curl -X GET https://localhost:8443/api/v1/transfers \
  --cert /path/to/client.crt \
  --key /path/to/client.key \
  --cacert /path/to/ca.crt \
  -H "Accept: application/json"
```

**Certificate Authentication Features:**
- **Client Certificate Validation**: Verify client certificates against corporate CA
- **Certificate Revocation**: Support for CRL and OCSP validation
- **Subject DN Mapping**: Map certificate subjects to user identities
- **Hardware Security Module**: Support for HSM-stored private keys

#### Basic Authentication (Development/Testing)

```bash
# Basic authentication for development environments
curl -X GET http://localhost:8080/api/v1/transfers \
  -u "username:password" \
  -H "Accept: application/json"

# With base64 encoding
curl -X GET http://localhost:8080/api/v1/transfers \
  -H "Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=" \
  -H "Accept: application/json"
```

### Authorization and Role-Based Access Control

#### Permission Model
```json
{
  "permissions": [
    "transfers:create",
    "transfers:read:own",
    "transfers:read:tenant",
    "transfers:cancel:own",
    "workflows:execute",
    "workflows:read",
    "tenants:read:own",
    "system:health:read"
  ]
}
```

**Permission Hierarchy:**
- **System Level**: `system:*` - Administrative operations
- **Tenant Level**: `tenants:*` - Tenant management operations
- **Workflow Level**: `workflows:*` - Workflow operations
- **Transfer Level**: `transfers:*` - Transfer operations
- **Scope Modifiers**: `:own`, `:tenant`, `:all` - Access scope limitations

#### Role Definitions
```yaml
# Standard roles with permission mappings
roles:
  admin:
    permissions: ["*"]
    description: "Full system administration"

  tenant-admin:
    permissions:
      - "tenants:*:own"
      - "transfers:*:tenant"
      - "workflows:*:tenant"
    description: "Tenant administration"

  operator:
    permissions:
      - "transfers:create"
      - "transfers:read:tenant"
      - "workflows:execute"
      - "workflows:read"
    description: "Transfer operations"

  user:
    permissions:
      - "transfers:create:own"
      - "transfers:read:own"
      - "workflows:execute:own"
    description: "Basic user operations"
```

### Core API Endpoints

#### Transfer Management

The Transfer Management API provides comprehensive control over file transfer operations with detailed configuration options and real-time monitoring capabilities.

##### Create Transfer Job

```bash
POST /api/v1/transfers
Content-Type: application/json
Authorization: Bearer {token}
X-Tenant: acme-corp
X-Namespace: finance

{
  "sourceUri": "https://api.corp.internal/exports/financial-data.zip",
  "destinationPath": "/corporate-storage/finance/imports/financial-data-2025-01-20.zip",
  "protocol": "https",
  "metadata": {
    "description": "Daily financial data import",
    "tags": ["finance", "daily", "automated"],
    "businessOwner": "finance-team@corp.com",
    "priority": "high",
    "dataClassification": "confidential"
  },
  "options": {
    "checksumValidation": true,
    "checksumAlgorithm": "SHA256",
    "expectedChecksum": "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3",
    "retryAttempts": 5,
    "retryBackoffMultiplier": 2.0,
    "initialRetryDelay": "PT30S",
    "maxRetryDelay": "PT10M",
    "timeout": 1800,
    "chunkSize": 10485760,
    "compressionEnabled": true,
    "resumeSupported": true
  },
  "credentials": {
    "type": "oauth2",
    "tokenEndpoint": "https://api.corp.internal/oauth/token",
    "clientId": "quorus-client",
    "clientSecret": "encrypted:vault:secret-key",
    "scope": "data:read"
  },
  "notifications": {
    "onSuccess": ["finance-team@corp.com"],
    "onFailure": ["finance-team@corp.com", "ops-team@corp.com"],
    "onProgress": {
      "enabled": true,
      "intervalSeconds": 300,
      "recipients": ["finance-ops@corp.com"]
    }
  },
  "scheduling": {
    "startAfter": "2025-01-20T06:00:00Z",
    "deadline": "2025-01-20T18:00:00Z",
    "priority": 8
  }
}
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "status": "PENDING",
  "createdAt": "2025-01-20T05:45:00Z",
  "estimatedStartTime": "2025-01-20T06:00:00Z",
  "queuePosition": 3,
  "assignedAgent": null,
  "links": {
    "self": "/api/v1/transfers/transfer-20250120-001",
    "progress": "/api/v1/transfers/transfer-20250120-001/progress",
    "logs": "/api/v1/transfers/transfer-20250120-001/logs",
    "cancel": "/api/v1/transfers/transfer-20250120-001"
  }
}
```

##### Get Transfer Status

```bash
GET /api/v1/transfers/{jobId}
Authorization: Bearer {token}
Accept: application/json

# Example: GET /api/v1/transfers/transfer-20250120-001
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "status": "IN_PROGRESS",
  "phase": "TRANSFERRING",
  "createdAt": "2025-01-20T05:45:00Z",
  "startedAt": "2025-01-20T06:00:00Z",
  "lastUpdated": "2025-01-20T06:15:30Z",
  "assignedAgent": "agent-east-01",
  "sourceUri": "https://api.corp.internal/exports/financial-data.zip",
  "destinationPath": "/corporate-storage/finance/imports/financial-data-2025-01-20.zip",
  "fileSize": 157286400,
  "bytesTransferred": 78643200,
  "progressPercentage": 50.0,
  "transferRate": 5242880,
  "averageTransferRate": 4718592,
  "estimatedTimeRemaining": "PT2M30S",
  "checksumStatus": "CALCULATING",
  "retryAttempts": 0,
  "lastError": null,
  "metadata": {
    "description": "Daily financial data import",
    "tags": ["finance", "daily", "automated"],
    "businessOwner": "finance-team@corp.com",
    "priority": "high"
  },
  "links": {
    "progress": "/api/v1/transfers/transfer-20250120-001/progress",
    "logs": "/api/v1/transfers/transfer-20250120-001/logs",
    "cancel": "/api/v1/transfers/transfer-20250120-001"
  }
}
```

##### Get Transfer Progress (Real-time)

```bash
GET /api/v1/transfers/{jobId}/progress
Authorization: Bearer {token}
Accept: application/json

# Server-Sent Events for real-time updates
GET /api/v1/transfers/{jobId}/progress/stream
Accept: text/event-stream
```

**JSON Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "timestamp": "2025-01-20T06:15:30Z",
  "status": "IN_PROGRESS",
  "phase": "TRANSFERRING",
  "progress": {
    "bytesTransferred": 78643200,
    "totalBytes": 157286400,
    "progressPercentage": 50.0,
    "chunksCompleted": 75,
    "totalChunks": 150
  },
  "performance": {
    "currentTransferRate": 5242880,
    "averageTransferRate": 4718592,
    "peakTransferRate": 6291456,
    "networkLatency": 15,
    "connectionQuality": "GOOD"
  },
  "timing": {
    "elapsedTime": "PT15M30S",
    "estimatedTimeRemaining": "PT2M30S",
    "estimatedCompletionTime": "2025-01-20T06:18:00Z"
  },
  "validation": {
    "checksumStatus": "CALCULATING",
    "checksumProgress": 50.0,
    "integrityChecks": {
      "chunksValidated": 75,
      "validationErrors": 0
    }
  }
}
```

**Server-Sent Events Stream:**
```
data: {"jobId":"transfer-20250120-001","bytesTransferred":78643200,"progressPercentage":50.0,"transferRate":5242880}

data: {"jobId":"transfer-20250120-001","bytesTransferred":83886080,"progressPercentage":53.3,"transferRate":5242880}

data: {"jobId":"transfer-20250120-001","status":"COMPLETED","bytesTransferred":157286400,"progressPercentage":100.0}
```

##### Cancel Transfer

```bash
DELETE /api/v1/transfers/{jobId}
Authorization: Bearer {token}
X-Reason: "Business requirement changed"

# Graceful cancellation with cleanup
DELETE /api/v1/transfers/{jobId}?graceful=true&cleanup=true
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "status": "CANCELLING",
  "message": "Transfer cancellation initiated",
  "gracefulShutdown": true,
  "cleanupRequested": true,
  "estimatedCancellationTime": "2025-01-20T06:16:00Z"
}
```

##### List Transfers with Advanced Filtering

```bash
# Basic listing
GET /api/v1/transfers
Authorization: Bearer {token}

# Advanced filtering and pagination
GET /api/v1/transfers?status=RUNNING,COMPLETED&tenant=acme-corp&namespace=finance&limit=50&offset=0&sort=createdAt:desc

# Filter by date range
GET /api/v1/transfers?createdAfter=2025-01-20T00:00:00Z&createdBefore=2025-01-20T23:59:59Z

# Filter by metadata tags
GET /api/v1/transfers?tags=finance,daily&priority=high

# Search by description or source
GET /api/v1/transfers?search=financial-data&searchFields=description,sourceUri
```

**Query Parameters:**
- **status**: Filter by transfer status (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED)
- **tenant**: Filter by tenant ID
- **namespace**: Filter by namespace
- **assignedAgent**: Filter by assigned agent
- **priority**: Filter by priority level (1-10)
- **tags**: Filter by metadata tags
- **createdAfter/createdBefore**: Date range filtering
- **search**: Full-text search across specified fields
- **limit/offset**: Pagination controls
- **sort**: Sort order (field:asc/desc)

**Response:**
```json
{
  "transfers": [
    {
      "jobId": "transfer-20250120-001",
      "status": "COMPLETED",
      "sourceUri": "https://api.corp.internal/exports/financial-data.zip",
      "destinationPath": "/corporate-storage/finance/imports/financial-data-2025-01-20.zip",
      "createdAt": "2025-01-20T05:45:00Z",
      "completedAt": "2025-01-20T06:18:00Z",
      "fileSize": 157286400,
      "duration": "PT33M",
      "averageTransferRate": 4718592,
      "priority": "high",
      "tags": ["finance", "daily", "automated"]
    }
  ],
  "pagination": {
    "total": 1250,
    "limit": 50,
    "offset": 0,
    "hasNext": true,
    "hasPrevious": false,
    "nextOffset": 50,
    "previousOffset": null
  },
  "links": {
    "self": "/api/v1/transfers?limit=50&offset=0",
    "next": "/api/v1/transfers?limit=50&offset=50",
    "first": "/api/v1/transfers?limit=50&offset=0",
    "last": "/api/v1/transfers?limit=50&offset=1200"
  }
}
```

##### Transfer Logs and Diagnostics

```bash
# Get transfer logs
GET /api/v1/transfers/{jobId}/logs
Authorization: Bearer {token}

# Get logs with filtering
GET /api/v1/transfers/{jobId}/logs?level=ERROR,WARN&since=2025-01-20T06:00:00Z

# Stream logs in real-time
GET /api/v1/transfers/{jobId}/logs/stream
Accept: text/event-stream
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "logs": [
    {
      "timestamp": "2025-01-20T06:00:00Z",
      "level": "INFO",
      "message": "Transfer started",
      "component": "TransferManager",
      "agent": "agent-east-01"
    },
    {
      "timestamp": "2025-01-20T06:00:15Z",
      "level": "DEBUG",
      "message": "Connected to source: https://api.corp.internal/exports/financial-data.zip",
      "component": "HttpProtocolHandler",
      "details": {
        "responseCode": 200,
        "contentLength": 157286400,
        "contentType": "application/zip"
      }
    },
    {
      "timestamp": "2025-01-20T06:18:00Z",
      "level": "INFO",
      "message": "Transfer completed successfully",
      "component": "TransferManager",
      "details": {
        "bytesTransferred": 157286400,
        "duration": "PT18M",
        "averageRate": 4718592,
        "checksumValid": true
      }
    }
  ],
  "pagination": {
    "total": 156,
    "limit": 100,
    "offset": 0
  }
}
```

#### Workflow Management

The Workflow Management API enables submission, monitoring, and control of complex multi-step transfer workflows with comprehensive execution tracking and management capabilities.

##### Submit Workflow

```bash
POST /api/v1/workflows
Content-Type: application/yaml
Authorization: Bearer {token}
X-Tenant: acme-corp
X-Namespace: finance
X-Execution-Mode: normal  # normal, dry-run, virtual-run

# YAML workflow definition
metadata:
  name: "Daily Financial Sync"
  version: "2.1.0"
  description: "Daily synchronization of financial data across systems for regulatory reporting and analysis"
  type: "financial-sync-workflow"
  author: "financial-ops@acme-corp.com"
  created: "2025-08-21"
  tags: ["daily", "financial", "sync", "regulatory"]
  # Multi-tenant metadata
  tenant: acme-corp
  namespace: finance
  labels:
    environment: production
    schedule: daily
    criticality: high
  annotations:
    owner: "finance-team@corp.com"
    documentation: "https://wiki.corp.com/finance/data-sync"
    approvedBy: "finance-director@corp.com"

spec:
  variables:
    sourceSystem: "https://erp.corp.internal"
    targetWarehouse: "/data-warehouse/finance"
    processingDate: "{{today}}"
    environment: "production"
    notificationEmail: "finance-ops@corp.com"

  execution:
    strategy: mixed
    parallelism: 3
    timeout: 7200
    retryPolicy:
      maxAttempts: 3
      backoffMultiplier: 2
      initialDelay: 60

  conditions:
    - name: production-only
      expression: "{{environment}} == 'production'"
    - name: business-hours
      expression: "{{hour}} >= 6 && {{hour}} <= 22"

  transferGroups:
    - name: extract-master-data
      description: Extract master data from ERP system
      condition: production-only
      transfers:
        - name: customer-master
          source: "{{sourceSystem}}/api/customers/export?date={{processingDate}}"
          destination: "{{targetWarehouse}}/raw/customers/{{processingDate}}.json"
          options:
            checksumValidation: true
            timeout: 1800

    - name: extract-transactional-data
      description: Extract transactional data
      dependsOn: [extract-master-data]
      transfers:
        - name: sales-transactions
          source: "{{sourceSystem}}/api/sales/export?date={{processingDate}}"
          destination: "{{targetWarehouse}}/raw/sales/{{processingDate}}.json"

    - name: data-validation
      description: Validate extracted data
      dependsOn: [extract-master-data, extract-transactional-data]
      condition: business-hours
      transfers:
        - name: run-validation
          source: "{{targetWarehouse}}/scripts/validate-data.py"
          destination: "{{targetWarehouse}}/validation/{{processingDate}}.log"

  notifications:
    onSuccess:
      - email: "{{notificationEmail}}"
        subject: "Daily financial sync completed successfully"
    onFailure:
      - email: "{{notificationEmail}}"
        subject: "Daily financial sync failed"
      - webhook: "https://alerts.corp.com/quorus/workflow-failed"
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "workflowName": "daily-financial-sync",
  "version": "2.1.0",
  "status": "PENDING",
  "executionMode": "normal",
  "createdAt": "2025-01-20T05:30:00Z",
  "scheduledStartTime": "2025-01-20T06:00:00Z",
  "tenant": "acme-corp",
  "namespace": "finance",
  "executionPlan": {
    "totalStages": 3,
    "estimatedDuration": "PT45M",
    "parallelizable": true,
    "criticalPath": ["extract-master-data", "extract-transactional-data", "data-validation"]
  },
  "variables": {
    "sourceSystem": "https://erp.corp.internal",
    "targetWarehouse": "/data-warehouse/finance",
    "processingDate": "2025-01-20",
    "environment": "production",
    "notificationEmail": "finance-ops@corp.com"
  },
  "links": {
    "self": "/api/v1/workflows/wf-exec-20250120-001",
    "status": "/api/v1/workflows/wf-exec-20250120-001/status",
    "progress": "/api/v1/workflows/wf-exec-20250120-001/progress",
    "logs": "/api/v1/workflows/wf-exec-20250120-001/logs",
    "cancel": "/api/v1/workflows/wf-exec-20250120-001"
  }
}
```

##### Get Workflow Execution Status

```bash
GET /api/v1/workflows/{executionId}
Authorization: Bearer {token}
Accept: application/json

# Example: GET /api/v1/workflows/wf-exec-20250120-001
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "workflowName": "daily-financial-sync",
  "version": "2.1.0",
  "status": "IN_PROGRESS",
  "currentStage": "extract-transactional-data",
  "createdAt": "2025-01-20T05:30:00Z",
  "startedAt": "2025-01-20T06:00:00Z",
  "lastUpdated": "2025-01-20T06:25:00Z",
  "executionMode": "normal",
  "tenant": "acme-corp",
  "namespace": "finance",
  "progress": {
    "completedStages": 1,
    "totalStages": 3,
    "progressPercentage": 33.3,
    "currentStageProgress": 75.0
  },
  "timing": {
    "elapsedTime": "PT25M",
    "estimatedTimeRemaining": "PT20M",
    "estimatedCompletionTime": "2025-01-20T06:45:00Z"
  },
  "stages": [
    {
      "name": "extract-master-data",
      "status": "COMPLETED",
      "startedAt": "2025-01-20T06:00:00Z",
      "completedAt": "2025-01-20T06:15:00Z",
      "duration": "PT15M",
      "transfers": [
        {
          "name": "customer-master",
          "jobId": "transfer-20250120-002",
          "status": "COMPLETED",
          "bytesTransferred": 52428800,
          "duration": "PT15M"
        }
      ]
    },
    {
      "name": "extract-transactional-data",
      "status": "IN_PROGRESS",
      "startedAt": "2025-01-20T06:15:00Z",
      "estimatedCompletionTime": "2025-01-20T06:35:00Z",
      "transfers": [
        {
          "name": "sales-transactions",
          "jobId": "transfer-20250120-003",
          "status": "IN_PROGRESS",
          "bytesTransferred": 78643200,
          "progressPercentage": 75.0
        }
      ]
    },
    {
      "name": "data-validation",
      "status": "PENDING",
      "dependsOn": ["extract-master-data", "extract-transactional-data"],
      "estimatedStartTime": "2025-01-20T06:35:00Z"
    }
  ],
  "variables": {
    "sourceSystem": "https://erp.corp.internal",
    "targetWarehouse": "/data-warehouse/finance",
    "processingDate": "2025-01-20",
    "environment": "production"
  },
  "links": {
    "progress": "/api/v1/workflows/wf-exec-20250120-001/progress",
    "logs": "/api/v1/workflows/wf-exec-20250120-001/logs",
    "cancel": "/api/v1/workflows/wf-exec-20250120-001"
  }
}
```

##### Get Workflow Progress (Real-time)

```bash
GET /api/v1/workflows/{executionId}/progress
Authorization: Bearer {token}

# Real-time progress stream
GET /api/v1/workflows/{executionId}/progress/stream
Accept: text/event-stream
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "timestamp": "2025-01-20T06:25:00Z",
  "status": "IN_PROGRESS",
  "currentStage": "extract-transactional-data",
  "overallProgress": {
    "completedStages": 1,
    "totalStages": 3,
    "progressPercentage": 33.3,
    "completedTransfers": 1,
    "totalTransfers": 3,
    "transferProgressPercentage": 58.3
  },
  "stageProgress": [
    {
      "stageName": "extract-master-data",
      "status": "COMPLETED",
      "progressPercentage": 100.0,
      "transfers": [
        {
          "transferName": "customer-master",
          "status": "COMPLETED",
          "progressPercentage": 100.0,
          "bytesTransferred": 52428800
        }
      ]
    },
    {
      "stageName": "extract-transactional-data",
      "status": "IN_PROGRESS",
      "progressPercentage": 75.0,
      "transfers": [
        {
          "transferName": "sales-transactions",
          "status": "IN_PROGRESS",
          "progressPercentage": 75.0,
          "bytesTransferred": 78643200,
          "transferRate": 5242880
        }
      ]
    }
  ],
  "performance": {
    "totalBytesTransferred": 131072000,
    "averageTransferRate": 4718592,
    "parallelTransfers": 1,
    "resourceUtilization": {
      "cpu": 45.2,
      "memory": 62.1,
      "network": 38.7
    }
  }
}
```

##### List Workflow Executions

```bash
# Basic listing
GET /api/v1/workflows
Authorization: Bearer {token}

# Advanced filtering
GET /api/v1/workflows?status=RUNNING,COMPLETED&tenant=acme-corp&namespace=finance&workflowName=daily-financial-sync&limit=25&sort=createdAt:desc

# Filter by execution date range
GET /api/v1/workflows?executedAfter=2025-01-20T00:00:00Z&executedBefore=2025-01-20T23:59:59Z

# Filter by duration and performance
GET /api/v1/workflows?minDuration=PT30M&maxDuration=PT2H&status=COMPLETED
```

**Response:**
```json
{
  "executions": [
    {
      "executionId": "wf-exec-20250120-001",
      "workflowName": "daily-financial-sync",
      "version": "2.1.0",
      "status": "COMPLETED",
      "tenant": "acme-corp",
      "namespace": "finance",
      "createdAt": "2025-01-20T05:30:00Z",
      "startedAt": "2025-01-20T06:00:00Z",
      "completedAt": "2025-01-20T06:42:00Z",
      "duration": "PT42M",
      "totalTransfers": 3,
      "successfulTransfers": 3,
      "failedTransfers": 0,
      "totalBytesTransferred": 157286400,
      "averageTransferRate": 4718592,
      "executionMode": "normal"
    }
  ],
  "pagination": {
    "total": 847,
    "limit": 25,
    "offset": 0,
    "hasNext": true
  },
  "aggregations": {
    "statusCounts": {
      "COMPLETED": 756,
      "FAILED": 45,
      "IN_PROGRESS": 12,
      "PENDING": 34
    },
    "averageDuration": "PT38M",
    "successRate": 94.3,
    "totalBytesTransferred": 1073741824000
  }
}
```

##### Cancel Workflow Execution

```bash
DELETE /api/v1/workflows/{executionId}
Authorization: Bearer {token}
X-Reason: "Emergency maintenance required"

# Graceful cancellation
DELETE /api/v1/workflows/{executionId}?graceful=true&waitForCurrentTransfers=true
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "status": "CANCELLING",
  "message": "Workflow cancellation initiated",
  "gracefulShutdown": true,
  "waitForCurrentTransfers": true,
  "currentTransfers": [
    {
      "transferName": "sales-transactions",
      "jobId": "transfer-20250120-003",
      "status": "IN_PROGRESS",
      "action": "COMPLETING"
    }
  ],
  "estimatedCancellationTime": "2025-01-20T06:30:00Z"
}
```

#### System Information and Monitoring

The System Information API provides comprehensive visibility into system health, performance, and operational status.

##### Service Information

```bash
GET /api/v1/info
Authorization: Bearer {token}
Accept: application/json
```

**Response:**
```json
{
  "service": {
    "name": "Quorus File Transfer System",
    "version": "2.0.1",
    "buildTime": "2025-01-15T10:30:00Z",
    "gitCommit": "a1b2c3d4e5f6",
    "environment": "production"
  },
  "api": {
    "version": "v1",
    "supportedVersions": ["v1"],
    "deprecatedVersions": [],
    "documentation": "https://docs.quorus.dev/api/v1"
  },
  "features": {
    "protocols": ["HTTP", "HTTPS", "FTP", "SFTP", "SMB"],
    "authentication": ["OAuth2", "API-Key", "mTLS", "Basic"],
    "multiTenant": true,
    "clustering": true,
    "workflows": true
  },
  "limits": {
    "maxFileSize": "10GB",
    "maxConcurrentTransfers": 1000,
    "maxWorkflowStages": 50,
    "apiRateLimit": 10000
  }
}
```

##### Health Check (Comprehensive)

```bash
# Basic health check
GET /health
Accept: application/json

# Detailed health check with dependencies
GET /health/detailed
Authorization: Bearer {token}

# Readiness probe (Kubernetes-compatible)
GET /health/ready

# Liveness probe (Kubernetes-compatible)
GET /health/live
```

**Basic Health Response:**
```json
{
  "status": "UP",
  "timestamp": "2025-01-20T06:30:00Z",
  "responseTime": "15ms"
}
```

**Detailed Health Response:**
```json
{
  "status": "UP",
  "timestamp": "2025-01-20T06:30:00Z",
  "checks": [
    {
      "name": "database",
      "status": "UP",
      "responseTime": "5ms",
      "details": {
        "connectionPool": {
          "active": 8,
          "idle": 12,
          "max": 20
        }
      }
    },
    {
      "name": "raft-cluster",
      "status": "UP",
      "details": {
        "role": "LEADER",
        "term": 42,
        "nodes": 3,
        "healthyNodes": 3,
        "lastHeartbeat": "2025-01-20T06:29:58Z"
      }
    },
    {
      "name": "transfer-agents",
      "status": "UP",
      "details": {
        "totalAgents": 15,
        "healthyAgents": 14,
        "degradedAgents": 1,
        "failedAgents": 0,
        "averageLoad": 0.65
      }
    },
    {
      "name": "storage-systems",
      "status": "UP",
      "details": {
        "primaryStorage": "UP",
        "backupStorage": "UP",
        "availableSpace": "2.5TB",
        "usedSpace": "1.2TB"
      }
    },
    {
      "name": "external-dependencies",
      "status": "DEGRADED",
      "details": {
        "corporateSSO": "UP",
        "monitoringSystem": "UP",
        "notificationService": "DEGRADED",
        "backupService": "UP"
      }
    }
  ],
  "performance": {
    "activeTransfers": 45,
    "queuedTransfers": 12,
    "transfersPerSecond": 2.3,
    "averageTransferDuration": "PT5M30S",
    "systemLoad": {
      "cpu": 45.2,
      "memory": 67.8,
      "disk": 23.1,
      "network": 34.5
    }
  }
}
```

##### Transfer Engine Health Check (v1.1)

> **New in v1.1**: Enhanced protocol-level health monitoring with detailed transfer metrics.

```bash
# Transfer engine health check
GET /api/v1/health/transfer-engine
Authorization: Bearer {token}
Accept: application/json

# Protocol-specific health
GET /api/v1/health/protocols
GET /api/v1/health/protocols/{protocolName}
```

**Transfer Engine Health Response:**
```json
{
  "status": "UP",
  "timestamp": "2026-01-11T10:30:00Z",
  "message": "Transfer engine operational",
  "protocols": [
    {
      "protocol": "http",
      "status": "UP",
      "timestamp": "2026-01-11T10:30:00Z",
      "message": "HTTP protocol healthy",
      "details": {
        "totalTransfers": 1250,
        "successRate": "98.50%",
        "averageDurationMs": 5230
      }
    },
    {
      "protocol": "sftp",
      "status": "UP",
      "timestamp": "2026-01-11T10:30:00Z",
      "details": {
        "totalTransfers": 890,
        "successRate": "99.10%",
        "averageDurationMs": 8450
      }
    },
    {
      "protocol": "ftp",
      "status": "DEGRADED",
      "timestamp": "2026-01-11T10:30:00Z",
      "message": "High failure rate detected",
      "details": {
        "totalTransfers": 234,
        "successRate": "85.00%",
        "failedTransfers": 35,
        "errorCounts": {
          "CONNECTION_TIMEOUT": 20,
          "AUTH_FAILURE": 15
        }
      }
    }
  ],
  "summary": {
    "totalProtocols": 4,
    "healthyProtocols": 3,
    "unhealthyProtocols": 1
  },
  "system": {
    "activeTransfers": 45,
    "totalBytesTransferred": "125.5 GB",
    "uptime": "PT72H30M"
  }
}
```

**Protocol Health Status Values:**
- `UP` - Protocol is healthy and operational
- `DOWN` - Protocol is not operational (0 successful transfers)
- `DEGRADED` - Protocol is operational but experiencing issues (success rate < 90%)

##### Metrics (Prometheus-compatible)

```bash
# Prometheus metrics endpoint
GET /metrics
Accept: text/plain

# JSON metrics for custom integrations
GET /api/v1/metrics
Authorization: Bearer {token}
Accept: application/json

# Specific metric categories
GET /api/v1/metrics/transfers
GET /api/v1/metrics/workflows
GET /api/v1/metrics/system
GET /api/v1/metrics/protocols  # NEW in v1.1
```

**Prometheus Metrics Sample:**
```
# HELP quorus_transfers_total Total number of transfers
# TYPE quorus_transfers_total counter
quorus_transfers_total{status="completed",tenant="acme-corp"} 1250
quorus_transfers_total{status="failed",tenant="acme-corp"} 23

# HELP quorus_transfer_duration_seconds Transfer duration in seconds
# TYPE quorus_transfer_duration_seconds histogram
quorus_transfer_duration_seconds_bucket{le="30"} 145
quorus_transfer_duration_seconds_bucket{le="60"} 234
quorus_transfer_duration_seconds_bucket{le="300"} 567
quorus_transfer_duration_seconds_bucket{le="+Inf"} 1250

# HELP quorus_cluster_nodes Number of cluster nodes by status
# TYPE quorus_cluster_nodes gauge
quorus_cluster_nodes{status="healthy"} 3
quorus_cluster_nodes{status="degraded"} 0
quorus_cluster_nodes{status="failed"} 0

# HELP quorus_agent_capacity_utilization Agent capacity utilization
# TYPE quorus_agent_capacity_utilization gauge
quorus_agent_capacity_utilization{agent="agent-east-01"} 0.75
quorus_agent_capacity_utilization{agent="agent-west-01"} 0.45
```

##### Cluster Status

```bash
GET /api/v1/cluster/status
Authorization: Bearer {token}
Accept: application/json
```

**Response:**
```json
{
  "cluster": {
    "name": "quorus-production",
    "size": 3,
    "healthyNodes": 3,
    "status": "HEALTHY"
  },
  "raft": {
    "currentTerm": 42,
    "leader": "controller-1",
    "role": "LEADER",
    "lastElection": "2025-01-19T14:22:00Z",
    "logIndex": 15847,
    "commitIndex": 15847
  },
  "nodes": [
    {
      "nodeId": "controller-1",
      "role": "LEADER",
      "status": "HEALTHY",
      "address": "controller-1:8080",
      "lastHeartbeat": "2025-01-20T06:29:58Z",
      "uptime": "P5DT14H22M",
      "version": "2.0.1"
    },
    {
      "nodeId": "controller-2",
      "role": "FOLLOWER",
      "status": "HEALTHY",
      "address": "controller-2:8080",
      "lastHeartbeat": "2025-01-20T06:29:57Z",
      "uptime": "P5DT14H20M",
      "version": "2.0.1"
    },
    {
      "nodeId": "controller-3",
      "role": "FOLLOWER",
      "status": "HEALTHY",
      "address": "controller-3:8080",
      "lastHeartbeat": "2025-01-20T06:29:59Z",
      "uptime": "P5DT14H18M",
      "version": "2.0.1"
    }
  ],
  "performance": {
    "requestsPerSecond": 125.7,
    "averageResponseTime": "45ms",
    "leaderElections": 3,
    "lastLeaderElection": "2025-01-19T14:22:00Z"
  }
}
```

### Error Handling and Response Codes

The Quorus API uses standard HTTP status codes and provides comprehensive error information to help with debugging and automated error handling.

#### HTTP Status Codes

| Status Code | Meaning | Usage |
|-------------|---------|-------|
| 200 | OK | Successful GET, PUT, PATCH requests |
| 201 | Created | Successful POST requests (resource created) |
| 202 | Accepted | Request accepted for async processing |
| 204 | No Content | Successful DELETE requests |
| 400 | Bad Request | Invalid request syntax or parameters |
| 401 | Unauthorized | Authentication required or failed |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Resource conflict (duplicate, state mismatch) |
| 422 | Unprocessable Entity | Valid syntax but semantic errors |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |
| 502 | Bad Gateway | Upstream service error |
| 503 | Service Unavailable | Service temporarily unavailable |

#### Error Response Format

All error responses follow a consistent format:

```json
{
  "error": {
    "code": "TRANSFER_FAILED",
    "message": "Transfer failed due to network timeout",
    "timestamp": "2025-01-20T10:30:00Z",
    "requestId": "req-20250120-12345",
    "details": {
      "jobId": "transfer-123",
      "sourceUri": "https://api.corp/data.zip",
      "errorType": "NETWORK_TIMEOUT",
      "retryable": true,
      "retryAfter": 300,
      "supportReference": "ERR-2025-001234"
    },
    "context": {
      "tenant": "acme-corp",
      "namespace": "finance",
      "agent": "agent-east-01",
      "operation": "file-transfer"
    },
    "suggestions": [
      "Check network connectivity to source system",
      "Verify firewall rules allow outbound connections",
      "Consider increasing timeout value for large files"
    ]
  }
}
```

#### Common Error Codes

**Authentication and Authorization Errors:**
```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "The provided authentication token is invalid or expired",
    "details": {
      "tokenType": "Bearer",
      "expiresAt": "2025-01-20T09:00:00Z",
      "retryable": false
    }
  }
}
```

**Validation Errors:**
```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": {
      "violations": [
        {
          "field": "sourceUri",
          "message": "Invalid URI format",
          "rejectedValue": "not-a-valid-uri"
        },
        {
          "field": "options.timeout",
          "message": "Timeout must be between 30 and 7200 seconds",
          "rejectedValue": 10
        }
      ]
    }
  }
}
```

**Resource Errors:**
```json
{
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": "Tenant quota exceeded for concurrent transfers",
    "details": {
      "quotaType": "CONCURRENT_TRANSFERS",
      "currentUsage": 50,
      "quotaLimit": 50,
      "retryable": true,
      "retryAfter": 300
    }
  }
}
```

**System Errors:**
```json
{
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "Transfer service temporarily unavailable",
    "details": {
      "reason": "CLUSTER_LEADER_ELECTION",
      "retryable": true,
      "retryAfter": 30,
      "estimatedRecoveryTime": "2025-01-20T10:32:00Z"
    }
  }
}
```

### Rate Limiting and Quotas

The API implements comprehensive rate limiting and quota management to ensure fair resource usage and system stability.

#### Rate Limiting Headers

All API responses include rate limiting information:

```bash
# Response headers
HTTP/1.1 200 OK
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642694400
X-RateLimit-Window: 3600
X-RateLimit-Policy: sliding-window
X-Quota-Transfers-Limit: 50
X-Quota-Transfers-Remaining: 35
X-Quota-Transfers-Reset: 1642694400
```

#### Rate Limiting Policies

**API Request Limits:**
- **Per User**: 1,000 requests per hour
- **Per Tenant**: 10,000 requests per hour
- **Per IP Address**: 5,000 requests per hour
- **Global**: 100,000 requests per hour

**Transfer Operation Limits:**
- **Concurrent Transfers**: Based on tenant quota
- **Transfer Volume**: Based on tenant quota
- **Workflow Executions**: Based on tenant quota

#### Rate Limit Exceeded Response

```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "API rate limit exceeded",
    "details": {
      "limitType": "API_REQUESTS",
      "limit": 1000,
      "window": "1 hour",
      "retryAfter": 1800,
      "resetTime": "2025-01-20T11:00:00Z"
    }
  }
}
```

#### Quota Management

```bash
# Check current quota usage
GET /api/v1/quotas/current
Authorization: Bearer {token}

# Get quota details for specific resource
GET /api/v1/quotas/transfers
GET /api/v1/quotas/storage
GET /api/v1/quotas/bandwidth
```

**Quota Response:**
```json
{
  "tenant": "acme-corp",
  "namespace": "finance",
  "quotas": {
    "concurrentTransfers": {
      "limit": 50,
      "used": 35,
      "remaining": 15,
      "utilizationPercentage": 70.0
    },
    "monthlyTransferVolume": {
      "limit": "1TB",
      "used": "750GB",
      "remaining": "250GB",
      "utilizationPercentage": 75.0,
      "resetDate": "2025-02-01T00:00:00Z"
    },
    "storageQuota": {
      "limit": "500GB",
      "used": "325GB",
      "remaining": "175GB",
      "utilizationPercentage": 65.0
    }
  }
}
```

## Administration Guide

### Cluster Management

#### Controller Cluster Setup

```bash
# Start 3-node controller cluster
docker-compose -f docker-compose-network-test.yml up -d

# Check cluster health
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health

# Check Raft status
curl http://localhost:8081/raft/status
```

#### Adding/Removing Nodes

```bash
# Add new controller node
curl -X POST http://localhost:8081/api/v1/cluster/nodes \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "controller4",
    "address": "controller4:8080"
  }'

# Remove node
curl -X DELETE http://localhost:8081/api/v1/cluster/nodes/controller4
```

### Monitoring and Logging

#### Health Monitoring

```bash
# Comprehensive health check
curl http://localhost:8080/health/detailed

# Example response
{
  "status": "UP",
  "checks": [
    {
      "name": "transfer-engine",
      "status": "UP",
      "data": {
        "activeTransfers": 5,
        "queuedTransfers": 2
      }
    },
    {
      "name": "cluster",
      "status": "UP",
      "data": {
        "role": "LEADER",
        "term": 15,
        "nodes": 3
      }
    }
  ]
}
```

#### Metrics Collection

```bash
# Prometheus metrics
curl http://localhost:8080/metrics

# Key metrics
quorus_transfers_total{status="completed"} 1250
quorus_transfers_total{status="failed"} 23
quorus_transfer_duration_seconds{quantile="0.5"} 45.2
quorus_cluster_leader_elections_total 3
quorus_tenant_quota_usage{tenant="acme-corp",resource="storage"} 0.45
```

#### Log Configuration

```properties
# logging.properties
# Root logger
.level=INFO
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler

# Console handler
java.util.logging.ConsoleHandler.level=INFO
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter

# File handler
java.util.logging.FileHandler.pattern=/var/log/quorus/quorus.%g.log
java.util.logging.FileHandler.limit=10485760
java.util.logging.FileHandler.count=10
java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter

# Package-specific levels
dev.mars.quorus.transfer.level=DEBUG
dev.mars.quorus.workflow.level=INFO
dev.mars.quorus.controller.raft.level=WARNING
```

### Backup and Recovery

#### Configuration Backup

```bash
# Backup tenant configurations
curl http://localhost:8080/api/v1/admin/backup/tenants > tenants-backup.json

# Backup workflow definitions
curl http://localhost:8080/api/v1/admin/backup/workflows > workflows-backup.yaml

# Backup cluster state
curl http://localhost:8081/api/v1/cluster/backup > cluster-backup.json
```

#### Disaster Recovery

```bash
# Restore from backup
curl -X POST http://localhost:8080/api/v1/admin/restore/tenants \
  -H "Content-Type: application/json" \
  --data-binary @tenants-backup.json

# Rebuild cluster from surviving nodes
./scripts/cluster-recovery.sh --surviving-nodes controller1,controller2
```

### Security Configuration

#### TLS Configuration

```yaml
# application.yml
quarkus:
  http:
    ssl:
      certificate:
        files: /etc/ssl/certs/quorus.crt
        key-files: /etc/ssl/private/quorus.key
    ssl-port: 8443

  oidc:
    auth-server-url: https://sso.corp.com/auth/realms/corporate
    client-id: quorus-api
    credentials:
      secret: your-client-secret
```

#### Access Control

```yaml
# rbac-config.yaml
apiVersion: quorus.dev/v1
kind: RoleBasedAccessControl

roles:
  - name: admin
    permissions:
      - "transfers:*"
      - "workflows:*"
      - "tenants:*"
      - "cluster:*"

  - name: operator
    permissions:
      - "transfers:read"
      - "transfers:create"
      - "workflows:read"
      - "workflows:execute"

  - name: user
    permissions:
      - "transfers:read:own"
      - "transfers:create:own"

bindings:
  - role: admin
    subjects:
      - "group:quorus-admins"
  - role: operator
    subjects:
      - "group:transfer-operators"
  - role: user
    subjects:
      - "group:all-users"
```

## Troubleshooting

### Common Issues

#### Transfer Failures

**Problem**: Transfers failing with network timeouts

```bash
# Check network connectivity
curl -v https://source-server.corp/test-file

# Increase timeout in transfer request
{
  "sourceUri": "https://slow-server.corp/large-file.zip",
  "destinationPath": "/storage/large-file.zip",
  "options": {
    "timeout": 1800,  # 30 minutes
    "retryAttempts": 5
  }
}
```

**Problem**: Checksum validation failures

```bash
# Verify source file integrity
curl -I https://source-server.corp/file.zip
# Check Content-MD5 or ETag headers

# Disable checksum validation if source is unreliable
{
  "sourceUri": "https://legacy-system.corp/data.csv",
  "destinationPath": "/storage/data.csv",
  "options": {
    "checksumValidation": false
  }
}
```

#### Cluster Issues

**Problem**: Leader election failures

```bash
# Check cluster status
curl http://localhost:8081/raft/status
curl http://localhost:8082/raft/status
curl http://localhost:8083/raft/status

# Check network connectivity between nodes
docker exec quorus-controller1 ping controller2
docker exec quorus-controller1 ping controller3

# Review election timeout settings
# Increase if network latency is high
ELECTION_TIMEOUT_MS=10000
```

**Problem**: Split-brain scenarios

```bash
# Check cluster quorum
curl http://localhost:8081/api/v1/cluster/quorum

# Manually trigger leader election
curl -X POST http://localhost:8081/api/v1/cluster/election

# Restart minority nodes if needed
docker restart quorus-controller3
```

#### Workflow Issues

**Problem**: Workflow validation errors

```bash
# Validate workflow syntax
java -jar quorus-workflow-2.0.jar validate --file workflow.yaml

# Check dependency cycles
java -jar quorus-workflow-2.0.jar analyze --file workflow.yaml

# Dry run to test execution plan
java -jar quorus-workflow-2.0.jar execute --file workflow.yaml --mode dry-run
```

**Problem**: Variable resolution failures

```yaml
# Ensure all variables are defined
spec:
  variables:
    sourceApi: "https://api.corp"
    targetPath: "/storage"
    # Add missing variables
    environment: "production"
    timestamp: "{{now}}"
```

### Diagnostic Commands

#### System Diagnostics

```bash
# Generate diagnostic report
curl http://localhost:8080/api/v1/admin/diagnostics > diagnostics.json

# Check system resources
curl http://localhost:8080/api/v1/admin/system/resources

# Thread dump for performance issues
curl http://localhost:8080/api/v1/admin/system/threads > threads.txt
```

#### Network Diagnostics

```bash
# Test network connectivity
./scripts/network-test-helper.sh status

# Check DNS resolution
nslookup source-server.corp
nslookup destination-server.corp

# Test bandwidth
iperf3 -c destination-server.corp -p 5201
```

### Log Analysis

#### Common Log Patterns

```bash
# Transfer failures
grep "Transfer failed" /var/log/quorus/quorus.log

# Cluster events
grep "Leader election" /var/log/quorus/quorus.log
grep "Node joined\|Node left" /var/log/quorus/quorus.log

# Performance issues
grep "Transfer rate below threshold" /var/log/quorus/quorus.log
grep "Queue full" /var/log/quorus/quorus.log
```

## Best Practices

### Transfer Optimization

#### File Size Considerations

```yaml
# For large files (>1GB)
transfers:
  - name: large-file-transfer
    source: "https://api.corp/large-dataset.zip"
    destination: "/storage/large-dataset.zip"
    options:
      chunkSize: 10485760  # 10MB chunks
      timeout: 3600        # 1 hour timeout
      retryAttempts: 5
      checksumValidation: true
```

#### Concurrent Transfers

```yaml
# Optimize parallelism based on network capacity
execution:
  strategy: parallel
  parallelism: 5  # Adjust based on bandwidth and server capacity

# Use sequential for bandwidth-limited environments
execution:
  strategy: sequential
```

#### Network Optimization

```yaml
# For high-latency networks
options:
  timeout: 1800
  retryAttempts: 3
  retryDelay: 30

# For unreliable networks
options:
  retryAttempts: 5
  retryBackoffMultiplier: 2
  checksumValidation: true
```

### Workflow Design

#### Dependency Management

```yaml
# Good: Clear dependency chains
transferGroups:
  - name: extract
    transfers: [extract-customers, extract-orders]
  - name: transform
    dependsOn: [extract]
    transfers: [transform-data]
  - name: load
    dependsOn: [transform]
    transfers: [load-warehouse]

# Avoid: Complex cross-dependencies
# This creates unnecessary complexity and potential deadlocks
```

#### Error Handling Strategy

```yaml
# Critical transfers: Fail fast
transferGroups:
  - name: critical-data
    continueOnError: false
    transfers:
      - name: financial-data
        onError:
          action: fail
          notification: "finance-team@corp.com"

# Non-critical transfers: Continue on error
transferGroups:
  - name: optional-data
    continueOnError: true
    transfers:
      - name: marketing-data
        onError:
          action: log
          notification: "marketing-team@corp.com"
```

### Security Best Practices

#### Credential Management

```yaml
# Use secure credential storage
transfers:
  - name: secure-transfer
    source: "sftp://secure-server.corp/data.zip"
    credentials:
      type: "key-based"
      keyPath: "/etc/quorus/keys/transfer-key"
      # Avoid embedding passwords in YAML
```

#### Network Security

```yaml
# Use HTTPS for external transfers
transfers:
  - name: external-api
    source: "https://partner-api.external.com/data"
    options:
      tlsVerification: true
      certificatePath: "/etc/ssl/certs/partner-ca.crt"

# Use internal protocols for internal transfers
transfers:
  - name: internal-transfer
    source: "smb://fileserver.corp.local/share/data"
    protocol: smb
```

### Monitoring and Alerting

#### Key Metrics to Monitor

```yaml
# Transfer success rate
alert: transfer_success_rate < 0.95
action: notify_operations_team

# Cluster health
alert: cluster_nodes_down > 1
action: page_on_call_engineer

# Resource utilization
alert: tenant_quota_usage > 0.9
action: notify_tenant_admin

# Performance degradation
alert: avg_transfer_duration > baseline * 2
action: investigate_performance
```

#### Operational Procedures

```bash
# Daily health check
./scripts/daily-health-check.sh

# Weekly capacity planning
./scripts/capacity-report.sh --tenant all --period week

# Monthly tenant review
./scripts/tenant-usage-report.sh --month $(date +%Y-%m)
```

### Capacity Planning

#### Sizing Guidelines

| Environment | Controllers | Agents | Concurrent Transfers |
|-------------|-------------|--------|---------------------|
| Development | 1 | 1-2 | 10 |
| Testing | 3 | 3-5 | 50 |
| Production | 3-5 | 10-50 | 500+ |
| Enterprise | 5+ | 50+ | 1000+ |

#### Resource Requirements

```yaml
# Controller nodes
resources:
  cpu: 2-4 cores
  memory: 4-8 GB
  storage: 100 GB SSD
  network: 1 Gbps

# Agent nodes
resources:
  cpu: 1-2 cores
  memory: 2-4 GB
  storage: 50 GB
  network: 1 Gbps
```

---

## Support and Resources

### Documentation
- [System Design](QUORUS_SYSTEM_DESIGN.md)
- [Implementation Plan](QUORUS_IMPLEMENTATION_PLAN.md)
- [Workflow Examples](QUORUS_WORKFLOWS_README.md)
- [Docker Testing](QUORUS-DOCKER-TESTING-README.md)

### Community
- **GitHub**: https://github.com/your-org/quorus
- **Issues**: https://github.com/your-org/quorus/issues
- **Discussions**: https://github.com/your-org/quorus/discussions

### Enterprise Support
- **Email**: support@quorus.dev
- **Documentation**: https://docs.quorus.dev
- **Training**: https://training.quorus.dev

---

# Part II: Technical Reference (Developers)

*This section contains detailed technical information, Java code examples, and implementation details primarily intended for developers and system architects.*

## System Architecture & Design

This section contains the detailed technical architecture information including:

- **Distributed System Architecture** - Controller clusters, agent fleets, and consensus mechanisms
- **Job Scheduling** - Intelligent assignment algorithms and load balancing strategies
- **State Management** - Cluster-wide state consistency and synchronization
- **Agent Fleet Management** - Real-time monitoring and health management
- **Workflow Orchestration** - Complex multi-step workflow coordination
- **Resource Allocation** - Tenant quotas and resource distribution
- **High Availability** - Raft consensus, leader election, and fault tolerance
- **Geographic Distribution** - Multi-AZ deployment and disaster recovery

*[Detailed technical content and Java examples from the main document will be consolidated here in a future update]*

## Core Functional Concepts

This section contains the detailed functional implementation including:

- **Transfer Engine Concepts** - Job lifecycle and state management
- **Protocol Support** - HTTP/HTTPS, FTP, SFTP, SMB implementations
- **Concurrent Transfers** - Parallelism and capacity management
- **Progress Tracking** - Real-time monitoring and rate calculation
- **Integrity Verification** - Checksum validation and corruption detection
- **Retry Logic** - Exponential backoff and configurable retry policies
- **Variable Resolution** - Template processing and environment substitution

*[Detailed technical content and Java examples from the main document will be consolidated here in a future update]*

## Java API Reference

This section will contain comprehensive Java API documentation including:

- **Core Classes** - TransferEngine, WorkflowOrchestrator, JobScheduler
- **Configuration APIs** - Programmatic workflow creation and management
- **Monitoring APIs** - Progress tracking and metrics collection
- **Extension Points** - Custom protocol handlers and plugins
- **Integration Examples** - Spring Boot, enterprise framework integration

*[All Java code examples from the main document will be consolidated here in a future update]*

## Advanced Configuration

This section will contain advanced technical configuration including:

- **Cluster Configuration** - Raft settings, network topology, security
- **Performance Tuning** - JVM settings, resource allocation, optimization
- **Security Configuration** - TLS, authentication, authorization, compliance
- **Monitoring Setup** - Metrics, logging, alerting, dashboards
- **Troubleshooting** - Common issues, debugging, performance analysis

*[Advanced technical configuration will be documented here in a future update]*

---

**Quorus v2.0** - Enterprise File Transfer System
Copyright © 2025 Your Organization. All rights reserved.
