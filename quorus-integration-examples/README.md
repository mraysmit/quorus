# Quorus Integration Examples

This module contains self-contained examples demonstrating the Quorus file transfer system capabilities. Each example showcases different aspects of the system and can be run independently.

## Available Examples

### 1. BasicTransferExample
**File:** `BasicTransferExample.java`  
**Main Class:** `dev.mars.quorus.examples.BasicTransferExample`

Demonstrates fundamental Quorus capabilities:
- Basic HTTP file transfer
- Real-time progress monitoring
- Multiple concurrent transfers
- Error handling and retry mechanisms
- Performance metrics and checksum verification

**Features Showcased:**
- ✅ HTTP/HTTPS file downloads
- ✅ Progress tracking with rate calculation
- ✅ SHA-256 integrity verification
- ✅ Concurrent transfer handling
- ✅ Retry logic for failed transfers
- ✅ Comprehensive error reporting

## Running the Examples

### Prerequisites
- Java 21 or higher
- Maven 3.6 or higher
- Internet connection (examples use httpbin.org for test files)

### Running from Command Line

#### Run the Basic Transfer Example:
```bash
# From the project root
mvn exec:java -pl quorus-integration-examples

# Or with specific main class
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicTransferExample"
```

#### Run with Maven compile first:
```bash
mvn compile exec:java -pl quorus-integration-examples
```

### Running from IDE
1. Import the project as a Maven project
2. Navigate to `quorus-integration-examples/src/main/java/dev/mars/quorus/examples/`
3. Right-click on `BasicTransferExample.java`
4. Select "Run BasicTransferExample.main()"

## Example Output

When you run the BasicTransferExample, you'll see output similar to:

```
INFO: === Quorus Basic Transfer Example ===
INFO: Demonstrating fundamental file transfer capabilities
INFO: Configuration loaded: QuorusConfiguration{maxConcurrentTransfers=10, maxRetryAttempts=3, checksumAlgorithm='SHA-256', metricsEnabled=true}

INFO: --- Basic Transfer Example ---
INFO: Starting transfer: https://httpbin.org/bytes/2048
INFO: Monitoring transfer progress...
INFO:   Progress: 50.0% (1024/2048 bytes)
INFO:   Progress: 100.0% (2048/2048 bytes)
INFO: Transfer Results:
INFO:   Status: COMPLETED
INFO:   Success: ✓
INFO:   Bytes transferred: 2048
INFO:   Duration: 1.23s
INFO:   Average rate: 1.67 KB/s
INFO:   SHA-256 checksum: a1b2c3d4e5f6...

INFO: --- Multiple Files Example ---
INFO: Submitting transfer 1: 512 bytes
INFO: Submitting transfer 2: 1024 bytes
INFO: Submitting transfer 3: 4096 bytes
INFO: Waiting for all transfers to complete...
INFO: Transfer 1 result: SUCCESS (512 bytes)
INFO: Transfer 2 result: SUCCESS (1024 bytes)
INFO: Transfer 3 result: SUCCESS (4096 bytes)

INFO: --- Error Handling Example ---
INFO: Starting transfer that will fail (404 error)...
INFO: Error handling result:
INFO:   Status: FAILED
INFO:   Error: Transfer failed after 3 attempts
INFO:   Retry attempts were made as expected

INFO: Shutting down transfer engine...
INFO: === Example completed ===
```

## Generated Files

The examples will create a `downloads/` directory in your current working directory with the following files:
- `basic-example.bin` - 2KB test file
- `multi-file-512b.bin` - 512 byte test file
- `multi-file-1024b.bin` - 1KB test file
- `multi-file-4096b.bin` - 4KB test file
- `error-example.bin` - Will not be created (404 error example)

## Understanding the Examples

### Basic Transfer Flow
1. **Configuration** - Load system configuration
2. **Engine Initialization** - Create transfer engine with specified parameters
3. **Request Creation** - Build transfer request with source URL and destination
4. **Transfer Submission** - Submit request and get future for result
5. **Progress Monitoring** - Monitor transfer progress in real-time
6. **Result Processing** - Handle completion and display metrics
7. **Cleanup** - Shutdown engine gracefully

### Key Components Demonstrated
- **TransferEngine** - Main interface for file transfers
- **TransferRequest** - Immutable request object with builder pattern
- **TransferResult** - Comprehensive result with metrics and status
- **QuorusConfiguration** - System configuration management
- **Progress Tracking** - Real-time transfer monitoring

## Next Steps

After running these examples, you can:
1. Explore the `quorus-core` module to understand the implementation
2. Modify the examples to transfer your own files
3. Experiment with different configuration parameters
4. Build your own applications using the Quorus API

## Troubleshooting

### Common Issues
- **Network connectivity**: Examples require internet access to httpbin.org
- **File permissions**: Ensure write permissions in the current directory
- **Java version**: Requires Java 21 or higher
- **Maven version**: Requires Maven 3.6 or higher

### Getting Help
- Check the logs for detailed error messages
- Verify network connectivity to httpbin.org
- Ensure proper Java and Maven versions
- Review the main project documentation in the parent directory
