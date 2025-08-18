# Quorus File Transfer System

Enterprise-grade file transfer system with progress tracking, integrity verification, and robust error handling.

## Project Structure

This is a multi-module Maven project with the following structure:

```
quorus/
├── quorus-core/                    # Core transfer engine implementation
│   ├── src/main/java/dev/mars/quorus/
│   │   ├── core/                   # Domain models (TransferJob, TransferRequest, etc.)
│   │   ├── transfer/               # Transfer engine and progress tracking
│   │   ├── protocol/               # Protocol implementations (HTTP/HTTPS)
│   │   ├── storage/                # File management and checksum calculation
│   │   ├── config/                 # Configuration management
│   │   └── core/exceptions/        # Exception hierarchy
│   └── src/test/java/              # Comprehensive unit and integration tests
├── quorus-integration-examples/    # Self-contained usage examples
│   ├── src/main/java/dev/mars/quorus/examples/
│   │   └── BasicTransferExample.java
│   └── README.md                   # Examples documentation
├── docs/                           # Project documentation
├── scripts/                        # Utility scripts
└── pom.xml                         # Parent POM
```

## Modules

### quorus-core
The core implementation of the Quorus file transfer system.

**Key Features:**
- ✅ HTTP/HTTPS file transfers
- ✅ Progress tracking with rate calculation and ETA
- ✅ SHA-256 integrity verification
- ✅ Concurrent transfer support (configurable)
- ✅ Retry mechanisms with exponential backoff
- ✅ Comprehensive error handling
- ✅ Thread-safe operations
- ✅ Extensible protocol architecture

**Dependencies:** None (pure Java implementation)

### quorus-integration-examples
Self-contained examples demonstrating system usage.

**Includes:**
- BasicTransferExample - Comprehensive demonstration of core features
- WorkflowValidationExample - Demonstrates validation with intentional failure tests
- ComplexWorkflowExample - Advanced workflow features and dependency management
- InternalNetworkTransferExample - Corporate network transfer scenarios
- Clear error handling with distinction between expected and unexpected failures

**Dependencies:** quorus-core

## Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.6 or higher
- Internet connection (for examples)

### Build the Project
```bash
# Clone and build
git clone <repository-url>
cd quorus
mvn clean compile
```

### Run Tests
```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl quorus-core
```

### Run Examples
```bash
# Run the basic transfer example
mvn exec:java -pl quorus-integration-examples

# Or with compilation
mvn compile exec:java -pl quorus-integration-examples
```

## Usage

### Basic Usage
```java
import dev.mars.quorus.config.QuorusConfiguration;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;

// Initialize
QuorusConfiguration config = new QuorusConfiguration();
TransferEngine engine = new SimpleTransferEngine(
    config.getMaxConcurrentTransfers(),
    config.getMaxRetryAttempts(),
    config.getRetryDelayMs()
);

// Create transfer request
TransferRequest request = TransferRequest.builder()
    .sourceUri(URI.create("https://example.com/file.zip"))
    .destinationPath(Paths.get("downloads/file.zip"))
    .protocol("http")
    .build();

// Execute transfer
CompletableFuture<TransferResult> future = engine.submitTransfer(request);
TransferResult result = future.get();

// Check result
if (result.isSuccessful()) {
    System.out.println("Transfer completed: " + result.getBytesTransferred() + " bytes");
} else {
    System.err.println("Transfer failed: " + result.getErrorMessage().orElse("Unknown error"));
}

// Cleanup
engine.shutdown(10);
```

### Progress Monitoring
```java
// Monitor transfer progress
String jobId = request.getRequestId();
while (true) {
    TransferJob job = engine.getTransferJob(jobId);
    if (job == null || job.getStatus().isTerminal()) {
        break;
    }
    
    System.out.printf("Progress: %.1f%% (%d/%d bytes)%n",
        job.getProgressPercentage() * 100,
        job.getBytesTransferred(),
        job.getTotalBytes());
    
    Thread.sleep(1000);
}
```

## Architecture

### Design Principles
- **Modular Architecture**: Clean separation between core engine and examples
- **Interface-Based Design**: Extensible protocol and storage abstractions
- **Thread Safety**: Concurrent operations with atomic updates
- **Immutable Domain Objects**: Builder patterns for request/result objects
- **Comprehensive Error Handling**: Custom exception hierarchy with retry logic

### Key Components
- **TransferEngine**: Main interface for file transfer operations
- **TransferProtocol**: Pluggable protocol implementations (HTTP/HTTPS)
- **ProgressTracker**: Real-time progress monitoring with rate calculation
- **ChecksumCalculator**: File integrity verification (SHA-256)
- **QuorusConfiguration**: Flexible configuration management

## Testing

The project includes comprehensive testing:
- **60 tests** with 100% success rate
- **Unit tests** for all core components
- **Integration tests** with real HTTP transfers
- **Error scenario testing** including retry mechanisms
- **Concurrent operation testing**

### Test Coverage
- Core domain models: ~95%
- Transfer components: ~85%
- Protocol layer: ~80%
- Overall estimated coverage: ~75-80%

## Development Status

### ✅ Milestone 1.1: Basic Transfer Engine (COMPLETE)
- Single-node file transfer with HTTP/HTTPS
- Basic file integrity verification (SHA-256)
- Transfer progress tracking
- Simple retry mechanism
- **All success criteria met**

### 🚧 Upcoming Milestones
- **Milestone 1.2**: Chunked Transfer with Resumability
- **Milestone 1.3**: Basic Service Architecture
- **Milestone 1.4**: Basic Monitoring & Health Checks

## Configuration

The system supports configuration through:
- Property files (`quorus.properties`)
- System properties (`-Dquorus.transfer.max.concurrent=5`)
- Environment variables
- Programmatic configuration

### Key Configuration Options
- `quorus.transfer.max.concurrent` - Maximum concurrent transfers (default: 10)
- `quorus.transfer.max.retries` - Maximum retry attempts (default: 3)
- `quorus.transfer.retry.delay.ms` - Retry delay in milliseconds (default: 1000)
- `quorus.file.checksum.algorithm` - Checksum algorithm (default: SHA-256)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

## Support

- Check the examples in `quorus-integration-examples/`
- Review the comprehensive test suite in `quorus-core/src/test/`
- See implementation documentation in `docs/`
