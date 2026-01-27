# TransferRequest Bidirectional Design Discussion

**Date:** 2026-01-26  
**Status:** DRAFT - For Review  
**Context:** Discovered during abort() testing implementation  

## Coding Standards

- **No emojis in code**: Use ASCII text only in source files
- **No implementation phase references in code**: Tests and comments should not reference implementation phases
- **Pre-production**: No backward compatibility or deprecation required - breaking changes are acceptable

## Testing Standards

- **No reflection**: Tests must not use Java reflection API (`Field`, `Method`, `setAccessible`)
- **No mocking**: Tests must not use mocking frameworks (Mockito, PowerMock, EasyMock)
- **Use Testcontainers**: Integration tests must use Testcontainers for external services (SFTP, FTP, etc.)
- **Real objects with simulation**: Unit tests should use real objects with built-in simulation modes
- **Test isolation**: Each test must be independent and not rely on execution order
- **All tests must pass**: Before proceeding to the next phase, ALL existing tests must pass (0 failures, 0 errors). Tests may be skipped only when external dependencies (e.g., Docker) are unavailable, using proper conditional annotations like `@Testcontainers(disabledWithoutDocker = true)`. Never proceed with failing tests.
- **Fix broken tests immediately**: When implementing changes that cause existing tests to fail, fix those tests as part of the same change. Do not defer test fixes to later phases.
- **Update tests for API changes**: When an API changes (e.g., bidirectional support), all affected tests must be updated to use the new API correctly.

## Problem Statement

While implementing SFTP abort integration tests, we discovered a fundamental asymmetry in `TransferRequest`:

```java
// Current API
TransferRequest.builder()
    .sourceUri(URI)           // ✅ Supports any protocol: http://, ftp://, sftp://, smb://
    .destinationPath(Path)    // ❌ Local filesystem only - no remote destinations
    .build();
```

**The Issue:** Cannot create test scenarios where files are uploaded TO remote SFTP/FTP servers, only downloaded FROM them.

**Deeper Question:** Is this asymmetry intentional, or a design gap?

## Current Implementation Analysis

### What Works Today

All protocols (HTTP, FTP, SFTP, SMB) are **download-only**:
- Source: Remote URI → Destination: Local Path
- All implementations use `Files.newOutputStream()`, `Files.createDirectories()` for local filesystem writes
- No protocol supports writing to remote destinations

### Evidence from Codebase

1. **Protocol Implementations:**
   ```java
   // SftpTransferProtocol.java:147
   Files.createDirectories(request.getDestinationPath().getParent());
   downloadFile(sftpClient, sourcePath, request.getDestinationPath(), ...);
   ```

2. **No Upload Examples:**
   - Integration examples: None found
   - Test cases: All test downloads only
   - Documentation: Describes "file acquisition" scenarios

3. **Architecture Alignment:**
   - System design describes "data center to data center" and "ETL ingest"
   - Agents "pull files from remote sources"
   - Workflow examples show downloads, not uploads

## Design Options

### Option 1: Keep Download-Only (Minimal Change)

**Approach:** Accept current design, rename for clarity

```java
// Rename TransferRequest → DownloadRequest or FileAcquisitionRequest
public class DownloadRequest {
    private final URI sourceUri;        // Remote source
    private final Path destinationPath; // Local destination only
}
```

**Pros:**
- Honest naming - matches implementation
- No breaking changes to protocol logic
- Clear intent for users
- Aligns with documented use cases

**Cons:**
- API rename is breaking change
- May limit future expansion
- Less symmetric/elegant

**Test Impact:**
- Use JSch directly in setUp() to upload test files
- Or pre-populate Docker volumes with test data
- Tests bypass TransferRequest (acceptable for setup)

### Option 2: Add Full Bidirectional Support

**Approach:** Support both source and destination as URIs

```java
public class TransferRequest {
    private final URI sourceUri;           // Any: file://, http://, ftp://, sftp://, smb://
    private final URI destinationUri;      // Any: file://, ftp://, sftp://, smb://
    
    // Direction determined by URI schemes
    public boolean isDownload() {
        return destinationUri.getScheme().equals("file");
    }
    
    public boolean isUpload() {
        return !sourceUri.getScheme().equals("file") && 
               !destinationUri.getScheme().equals("file");
    }
}
```

**Pros:**
- Truly symmetric API
- Enables upload scenarios (local → remote SFTP/FTP)
- Enables relay scenarios (remote → remote)
- Future-proof design

**Cons:**
- **MAJOR breaking change** - affects all protocols
- Significant implementation work:
  - FtpTransferProtocol needs upload logic (FTPClient.storeFile)
  - SftpTransferProtocol needs upload logic (ChannelSftp.put)
  - SmbTransferProtocol needs upload logic
  - HttpTransferProtocol needs POST/PUT support
- Complicates abort() logic (different for upload vs download)
- May not align with documented use cases
- Risk: breaks existing consumers

**Implementation Complexity:**
- Each protocol needs bidirectional support
- Different connection patterns for upload vs download
- Different abort() mechanisms
- Different progress tracking
- Different retry logic

### Option 3: Separate Request Types (Explicit Direction)

**Approach:** Create distinct types for different operations

```java
// Download (current behavior)
public class DownloadRequest {
    private final URI sourceUri;        // Remote source
    private final Path destinationPath; // Local destination
}

// Upload (new capability)
public class UploadRequest {
    private final Path sourcePath;      // Local source
    private final URI destinationUri;   // Remote destination (ftp://, sftp://)
}

// Factory/Builder can create appropriate type
TransferRequestFactory.download(uri, path);
TransferRequestFactory.upload(path, uri);
```

**Pros:**
- Clear intent - no ambiguity
- Can evolve separately
- Existing code unchanged (DownloadRequest = current TransferRequest)
- Upload is opt-in capability
- Different validation rules for each type

**Cons:**
- More classes to maintain
- Protocols need to handle both types
- Common interface needed for polymorphism

## Business Use Case Analysis

### Does Quorus Need Uploads?

**Potential Upload Scenarios:**
1. **Backup to remote**: Agent uploads local files to remote FTP/SFTP backup server
2. **Data publishing**: Agent pushes processed files to external partners
3. **Archival**: Upload completed data to remote long-term storage
4. **ETL output**: Push transformed data to external systems

**Current Documentation Says:**
- "Internal corporate network file transfers"
- "Data center to data center" (could be bidirectional?)
- "Application to application data synchronization" (bidirectional?)
- "ETL pipeline data movement" (typically ingest, but could export?)

**Question for Stakeholders:**
Are these legitimate Quorus use cases, or should uploads be out-of-scope?

## Impact Assessment

### If We Keep Download-Only

**Code Changes:**
- Minimal: Optionally rename TransferRequest → DownloadRequest
- Document the constraint clearly
- Add validation to reject non-file destination schemes

**Test Changes:**
- Use workarounds for test setup (JSch direct upload or volume pre-population)
- Document test pattern

**User Impact:**
- None if behavior is as expected
- Clear limitation if they try to upload

### If We Add Bidirectional Support

**Code Changes:**
- **HIGH IMPACT** - Every protocol implementation affected:
  - `HttpTransferProtocol`: Add POST/PUT upload support
  - `FtpTransferProtocol`: Add FTPClient.storeFile() path
  - `SftpTransferProtocol`: Add ChannelSftp.put() path
  - `SmbTransferProtocol`: Add SMB write path
  - `SimpleTransferEngine`: Handle both directions
  - `abort()`: Different logic for upload vs download

**Test Changes:**
- Major: Need upload test coverage for all protocols
- New TestContainers scenarios
- Bidirectional integration tests

**Migration Path:**
- Deprecate old API, support both
- Or: Break compatibility, require migration

**Timeline:**
- Estimate: 2-3 weeks for full implementation + testing

## Questions for Tomorrow's Discussion

1. **Strategic Direction:**
   - Is Quorus intentionally download-only, or should it support bidirectional transfers?
   - Do documented use cases require upload capability?

2. **If Download-Only:**
   - Should we rename TransferRequest to reflect this?
   - How do we handle test scenarios that need file uploads?
   - Document as permanent limitation or temporary gap?

3. **If Bidirectional:**
   - Option 2 (symmetric URI) or Option 3 (separate request types)?
   - What's the migration strategy for existing code?
   - What's the implementation timeline and priority?
   - Should this block abort() testing, or use workaround?

4. **Immediate Decision:**
   - For abort() integration tests: Use JSch workaround or Docker volume pre-population?
   - Should we proceed with Option 3 (JSch in setUp) to unblock testing?

## Recommendation

**Short-term (This Week):**
- Use Option 3 from test options: JSch directly in setUp() to create test files
- Allows abort() testing to proceed without API changes
- Add TODO comment explaining the limitation

**Long-term (Q1 2026):**
- Make strategic decision on bidirectional support
- If needed, implement Option 3 (separate UploadRequest/DownloadRequest types)
- Maintain backward compatibility during transition

## Related Files

- `TransferRequest.java` - Current implementation
- `SftpTransferProtocol.java` - Download-only implementation
- `SftpAbortIntegrationTest.java` - Test that revealed this issue
- `QUORUS_SYSTEM_DESIGN.md` - Use case documentation

---

## Implementation Plan for Option 2 (Bidirectional URIs)

### Test-Driven Development Approach

**Core Principle:** Write tests to support and validate changes at every step in each phase.

- **Before implementation**: Write failing tests that define expected behavior
- **During implementation**: Tests guide development and catch regressions
- **After implementation**: Tests validate correctness and serve as documentation
- **Continuous validation**: Run full test suite after each commit
- **Coverage requirement**: Minimum 90% code coverage for new bidirectional features

**Testing Strategy:**
1. **Unit tests** for each new method/class (direction detection, validation, etc.)
2. **Integration tests** for each protocol's bidirectional support
3. **Regression tests** to ensure backward compatibility
4. **End-to-end tests** for complete upload/download workflows
5. **Performance tests** to validate upload speeds match download speeds

### Phase 1: Core API Changes

#### 1.1 TransferRequest Class Refactoring
**File:** `quorus-core/src/main/java/dev/mars/quorus/core/TransferRequest.java`

**Current State:**
```java
private final URI sourceUri;           // Remote: http://, ftp://, sftp://, smb://
private final String destinationPath;  // Local filesystem only - stored as String
public Path getDestinationPath() { return Path.of(destinationPath); }
```

**Target State:**
```java
private final URI sourceUri;      // Any: file://, http://, ftp://, sftp://, smb://
private final URI destinationUri; // Any: file://, ftp://, sftp://, smb://

// Helper methods for direction detection
public boolean isDownload() {
    return "file".equalsIgnoreCase(destinationUri.getScheme());
}

public boolean isUpload() {
    return "file".equalsIgnoreCase(sourceUri.getScheme()) &&
           !"file".equalsIgnoreCase(destinationUri.getScheme());
}

public boolean isRemoteToRemote() {
    return !"file".equalsIgnoreCase(sourceUri.getScheme()) &&
           !"file".equalsIgnoreCase(destinationUri.getScheme());
}

public TransferDirection getDirection() {
    if (isDownload()) return TransferDirection.DOWNLOAD;
    if (isUpload()) return TransferDirection.UPLOAD;
    return TransferDirection.REMOTE_TO_REMOTE;
}

// Backward compatibility
@Deprecated
public Path getDestinationPath() {
    if (!"file".equalsIgnoreCase(destinationUri.getScheme())) {
        throw new UnsupportedOperationException(
            "getDestinationPath() only valid for file:// destinations. Use getDestinationUri()");
    }
    return Path.of(destinationUri.getPath());
}
```

**Changes Required:**
- Add new `destinationUri` field
- Deprecate `destinationPath` field (keep for backward compatibility during migration)
- Update Builder to accept both `destinationPath(Path)` and `destinationUri(URI)`
- Builder should convert Path to `file://` URI internally
- Add validation to ensure at least one endpoint is `file://`
- Add JSON serialization support for new field
- Update equals/hashCode if needed

**Migration Strategy:**
- Keep old `destinationPath` field with `@Deprecated` annotation
- New code uses `destinationUri`
- Builder accepts both during transition period
- Document deprecation timeline (Q1 2026 → Q2 2026 removal)

**Phase 1 Tests:**
- ✅ Unit test `TransferDirection` enum values and logic
- ✅ Unit test `isDownload()`, `isUpload()`, `isRemoteToRemote()` methods
- ✅ Unit test `getDirection()` with all URI scheme combinations
- ✅ Unit test builder backward compatibility (both `destinationPath` and `destinationUri`)
- ✅ Unit test validation logic (at least one `file://` endpoint)
- ✅ Regression test: All existing `TransferRequest` tests pass unchanged

#### 1.2 New TransferDirection Enum
**File:** `quorus-core/src/main/java/dev/mars/quorus/core/TransferDirection.java` (NEW)

```java
public enum TransferDirection {
    DOWNLOAD,          // Remote → Local (file://)
    UPLOAD,            // Local (file://) → Remote
    REMOTE_TO_REMOTE   // Remote → Remote (relay/proxy)
}
```

**Purpose:** Explicit direction tracking for routing logic, metrics, and validation.

---

### Phase 2: Protocol Implementation Updates

Each protocol needs bidirectional logic. All protocols currently only implement download.

#### 2.1 SftpTransferProtocol Changes
**File:** `quorus-core/src/main/java/dev/mars/quorus/protocol/SftpTransferProtocol.java`

**Current Implementation:**
- Only `downloadFile()` using `ChannelSftp.get()`
- Writes to local filesystem: `Files.newOutputStream()`

**Required Changes:**

**New Methods:**
```java
private TransferResult performSftpUpload(TransferRequest request, ProgressTracker tracker) {
    // Read from local file:// source
    Path localSource = Path.of(request.getSourceUri().getPath());
    
    // Connect to SFTP server from destinationUri
    SftpConnectionInfo destInfo = parseSftpUri(request.getDestinationUri());
    SftpClient client = new SftpClient(destInfo);
    
    try {
        client.connect();
        long bytesTransferred = client.uploadFile(localSource, destInfo.path, tracker);
        // ... build result
    } finally {
        client.disconnect();
    }
}

// Add to SftpClient inner class
long uploadFile(Path localPath, String remotePath, ProgressTracker tracker) 
        throws IOException, SftpException {
    
    long fileSize = Files.size(localPath);
    long bytesTransferred = 0;
    
    try (InputStream input = Files.newInputStream(localPath);
         BufferedInputStream bufferedInput = new BufferedInputStream(input, DEFAULT_BUFFER_SIZE)) {
        
        SftpProgressMonitor monitor = createProgressMonitor(tracker);
        sftpChannel.put(bufferedInput, remotePath, monitor, ChannelSftp.OVERWRITE);
        bytesTransferred = fileSize;
    }
    
    return bytesTransferred;
}
```

**Routing Logic in `transfer()` method:**
```java
@Override
public TransferResult transfer(TransferRequest request, TransferContext context) {
    logger.info("Starting SFTP transfer for job: " + context.getJobId());
    
    TransferDirection direction = request.getDirection();
    
    switch (direction) {
        case DOWNLOAD:
            return performSftpDownload(request, progressTracker);
        case UPLOAD:
            return performSftpUpload(request, progressTracker);
        case REMOTE_TO_REMOTE:
            return performSftpRelay(request, progressTracker); // Future
        default:
            throw new TransferException("Unsupported direction: " + direction);
    }
}
```

**Abort Logic Updates:**
- Upload abort: Same mechanism (force close ChannelSftp)
- Need to test abort during `sftpChannel.put()` operations

**Phase 2.1 Tests (SFTP):**
- ✅ Unit test `performSftpUpload()` with mocked SftpClient
- ✅ Unit test `uploadFile()` method in SftpClient
- ✅ Integration test: Upload 1KB file to TestContainers SFTP server
- ✅ Integration test: Upload 10MB file with progress tracking
- ✅ Integration test: Upload 100MB file (performance validation)
- ✅ Integration test: Abort during upload (interrupt `put()` operation)
- ✅ Integration test: Upload with invalid credentials (error handling)
- ✅ Integration test: Upload to non-existent directory (auto-create validation)
- ✅ Regression test: All existing SFTP download tests still pass

---

#### 2.2 FtpTransferProtocol Changes
**File:** `quorus-core/src/main/java/dev/mars/quorus/protocol/FtpTransferProtocol.java`

**Current Implementation:**
- Custom FTPClient implementation using raw sockets
- Only `downloadFile()` with RETR command

**Required Changes:**

**New Upload Method:**
```java
long uploadFile(String remotePath, Path localPath, ProgressTracker tracker) 
        throws IOException {
    
    long fileSize = Files.size(localPath);
    tracker.setTotalBytes(fileSize);
    
    // Enter passive mode
    sendCommand("PASV");
    String pasvResponse = readResponse();
    Socket dataSocket = parsePasvResponse(pasvResponse);
    
    // Send STOR command
    sendCommand("STOR " + remotePath);
    String response = readResponse();
    if (!response.startsWith("150")) {
        throw new IOException("Failed to initiate upload: " + response);
    }
    
    // Upload file data
    try (InputStream input = Files.newInputStream(localPath);
         BufferedInputStream bufferedInput = new BufferedInputStream(input);
         OutputStream dataOutput = dataSocket.getOutputStream();
         BufferedOutputStream bufferedOutput = new BufferedOutputStream(dataOutput)) {
        
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int bytesRead;
        long bytesTransferred = 0;
        
        while ((bytesRead = bufferedInput.read(buffer)) != -1) {
            bufferedOutput.write(buffer, 0, bytesRead);
            bytesTransferred += bytesRead;
            tracker.updateProgress(bytesTransferred);
            
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Upload cancelled");
            }
        }
        
        bufferedOutput.flush();
    }
    
    dataSocket.close();
    
    // Read completion response
    response = readResponse();
    if (!response.startsWith("226")) {
        throw new IOException("Upload failed: " + response);
    }
  Phase 2.2 Tests (FTP):**
- ✅ Unit test STOR command implementation
- ✅ Unit test passive mode data connection for upload
- ✅ Integration test: Upload to TestContainers FTP server (`stilliard/pure-ftpd`)
- ✅ Integration test: Upload with PASV mode
- ✅ Integration test: Upload with active mode
- ✅ Integration test: Abort during upload
- ✅ Integration test: Large file upload (100MB+)
- ✅ Regression test: All existing FTP download tests still pass

**Testing Requirements:**
- TestContainers with FTP server (e.g., `stilliard/pure-ftpd`)
- Upload tests with various file sizes
- Abort during upload

---

#### 2.3 HttpTransferProtocol Changes
**File:** `quorus-core/src/main/java/dev/mars/quorus/protocol/HttpTransferProtocol.java`

**Current Implementation:**
- Uses Vert.x WebClient for reactive GET requests
- Only downloads (GET)

**Required Changes:**

**New Upload Method (POST/PUT):**
```java
private io.vertx.core.Future<TransferResult> performHttpUpload(
        TransferRequest request, TransferContext context) {
    
    Path localSource = Path.of(request.getSourceUri().getPath());
    String uploadUrl = request.getDestinationUri().toString();
    
    return vertx.fileSystem().readFile(localSource.toString())
        .compose(buffer -> {
            // Determine HTTP method from metadata or default to POST
            String method = request.getMetadata().getOrDefault("httpMethod", "POST");
            
            if ("POST".equalsIgnoreCase(method)) {
                return webClient.postAbs(uploadUrl)
                    .timeout(READ_TIMEOUT_MS)
                    .sendBuffer(buffer);
            } else if ("PUT".equalsIgnoreCase(method)) {
                return webClient.putAbs(uploadUrl)
                    .timeout(READ_TIMEOUT_MS)
                    .sendBuffer(buffer);
            } else {
                return io.vertx.core.Future.failedFuture(
                    new TransferException("Unsupported HTTP method: " + method));
            }
        })
        .compose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return io.vertx.core.Future.failedFuture(
                    new TransferException("Upload failed: HTTP " + response.statusCode()));
            }
            
            // Build success result
            return io.vertx.core.Future.succeededFuture(
                TransferResult.builder()
                    .requestId(request.getRequestId())
                    .finalStatus(TransferStatus.COMPLETED)
                    .bytesTransferred(Files.size(localSource))
                    // ... rest of result
                    .build()
            );
        });
}
```

**Phase 2.3 Tests (HTTP):**
- ✅ Unit test `performHttpUpload()` method
- ✅ Integration test: POST upload to Vert.x test server
- ✅ Integration test: PUT upload to Vert.x test server
- ✅ Integration test: Upload with custom HTTP headers (via metadata)
- ✅ Integration test: Upload with authentication (Bearer token)
- ✅ Integration test: HTTP 4xx/5xx error handling
- ✅ Integration test: Timeout during upload
- ✅ Regression test: All existing HTTP download tests still pass
**Testing Requirements:**
- Mock HTTP server in tests (Vert.x test server or WireMock)
- POST upload tests
- PUT upload tests
- Response status validation

---

#### 2.4 SmbTransferProtocol Changes
**File:** `quorus-core/src/main/java/dev/mars/quorus/protocol/SmbTransferProtocol.java`

**Current Implementation:**
- Uses UNC paths and Java NIO Files API
- Only downloads

**Required Changes:**

**Upload Method:**
```java
private long uploadFile(Path localSource, Path uncDestination, ProgressTracker tracker) 
        throws IOException {
    
    long fileSize = Files.size(localSource);
    tracker.setTotalBytes(fileSize);
    
    // Ensure remote directory exists (if permissions allow)
    try {
        Files.createDirectories(uncDestination.getParent());
    } catch (IOException e) {
        logger.warning("Could not create remote directory: " + e.getMessage());
    }
    
    try (InputStream input = Files.newInputStream(localSource);
         OutputStream output = Files.newOutputStream(uncDestination,
             StandardOpenOption.CREATE, StandardOpenOption.WRITE);
         BufferedInputStream bufferedInput = new BufferedInputStream(input);
         BufferedOutputStream bufferedOutput = new BufferedOutputStream(output)) {
        
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int bytesRead;
        long bytesTransferred = 0;
        
        while ((bytesRead = bufferedInput.read(buffer)) != -1) {
            bufferedOutput.write(buffer, 0, bytesRead);
            bytesTransferred += bytesRead;
            tracker.updateProgress(bytesTransferred);
            
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Upload cancelled");
            }
        }
        
        bufferedOutput.flush();
    }
    
    return fileSize;
}
``Phase 2.4 Tests (SMB):**
- ✅ Unit test `uploadFile()` method
- ✅ Integration test: Upload to Docker Samba server (Linux)
- ✅ Integration test: Upload to Windows network share (Windows CI)
- ✅ Integration test: Permission denied scenarios
- ✅ Integration test: Directory auto-creation on upload
- ✅ Regression test: All existing SMB download tests still pas
**Testing Requirements:**
- Windows environment or Docker with Samba server
- Permission testing
- Network share upload tests

---

### Phase 3: Engine and Factory Updates

#### 3.1 SimpleTransferEngine Changes
**File:** `quorus-core/src/main/java/dev/mars/quorus/transfer/SimpleTransferEngine.java`

**Current State:**
- Routes transfers to protocols based on `request.getProtocol()`
- No direction awareness

**Required Changes:**
- Update metrics tracking to include direction (download vs upload)
- Log transfer direction for observability
- Validate that at least one URI is `file://`

**New Validation:**
```java
private void validateTransferRequest(TransferRequest request) throws TransferException {
    URI source = request.getSourceUri();
    URI dest = request.getDestinationUri();
    
    // At least one must be file://
    if (!"file".equalsIgnoreCase(source.getScheme()) &&
        !"file".equalsIgnoreCase(dest.getScheme())) {
        throw new TransferException(request.getRequestId(),
            "At least one endpoint must be file:// (local filesystem). " +
            "Remote-to-remote transfers not yet supported.");
    }
    
    // Both can't be file:// (use Files.copy instead)
    if ("file".equalsIgnoreCase(source.getScheme()) &&
        "file".equalsIgnoreCase(dest.getScheme())) {
        throw new TransferException(request.getRequestId(),
            "Use Files.copy() for local file-to-file operations");
    }
}
```

**Metrics Updates:**
```java
// Track direction in metrics
String direction = request.getDirection().name();
protocolMetrics.computeIfAbsent(
   

**Phase 3 Tests (Engine/Factory):**
- ✅ Unit test `validateTransferRequest()` with all URI combinations
- ✅ Unit test protocol selection for uploads vs downloads
- ✅ Unit test metrics tracking includes direction
- ✅ Integration test: End-to-end upload workflow via engine
- ✅ Integration test: Mixed download/upload batch operations
- ✅ Regression test: All existing SimpleTransferEngine tests pass protocol + "-" + direction,
    k -> new TransferMetrics(protocol, direction)
);
```

---

#### 3.2 ProtocolFactory Changes
**File:** `quorus-core/src/main/java/dev/mars/quorus/protocol/ProtocolFactory.java`

**Current State:**
- Determines protocol from `sourceUri.getScheme()`

**Required Changes:**
- Update protocol selection to consider direction
- For uploads, use `destinationUri.getScheme()` to determine protocol
- For downloads, use `sourceUri.getScheme()` (existing behavior)

**Updated Logic:**
```java
public TransferProtocol getProtocol(TransferRequest request) {
    TransferDirection direction = request.getDirection();
    
    String protocolScheme;
    switch (direction) {
        case DOWNLOAD:
            protocolScheme = request.getSourceUri().getScheme();
            break;
        case UPLOAD:
            protocolScheme = request.getDestinationUri().getScheme();
            break;
        case REMOTE_TO_REMOTE:
            throw new UnsupportedOperationException(
                "Remote-to-remote transfers not yet implemented");
        default:
            throw new IllegalArgumentException("Unknown direction: " + direction);
    }
    
    // Find protocol that handles this scheme
    for (TransferProtocol protocol : protocols) {
        if (protocol.canHandle(request)) {
            return protocol;
        }
    }
    
    throw new IllegalArgumentException("No protocol found for: " + protocolScheme);
}
```

---

### Phase 4: Test Updates

#### 4.1 Existing Test Migration
**Files to Update:**
- `quorus-core/src/test/java/dev/mars/quorus/core/TransferRequestTest.java`
- All integration tests in `quorus-core/src/test/java/dev/mars/quorus/integration/`
- All examples in `quorus-integration-examples/`

**Migration Pattern:**
```java
// Old (deprecated but still works)
TransferRequest request = TransferRequest.builder()
    .sourceUri(URI.create("sftp://server/file.dat"))
    .destinationPath(Paths.get("/local/file.dat"))
    .build();

// New (preferred)
TransferRequest request = TransferRequest.builder()
    .sourceUri(URI.create("sftp://server/file.dat"))
    .destinationUri(URI.create("file:///local/file.dat"))
    .build();
```

**Builder Backward Compatibility:**
```java
// In TransferRequest.Builder
public Builder destinationPath(Path path) {
    // Convert Path to file:// URI automatically
    this.destinationUri = path.toUri();
    return this;
}
```

---

#### 4.2 New Upload Integration Tests

**File:** `quorus-core/src/test/java/dev/mars/quorus/integration/UploadIntegrationTest.java` (NEW)

**Test Cases:**
1. **SFTP Upload** - Upload local file to TestContainers SFTP server
2. **FTP Upload** - Upload local file to TestContainers FTP server
3. **HTTP Upload (POST)** - Upload to mock HTTP endpoint
4. **HTTP Upload (PUT)** - Upload to mock HTTP endpoint
5. **SMB Upload** - Upload to Samba share (if Windows/Docker available)
6. **Upload Progress Tracking** - Verify progress during upload
7. **Upload Abort** - Test aborting upload mid-stream
8. **Large File Upload** - Test with 100MB+ files

**Example Test:**
```java
@Test
void testSftpUpload() throws Exception {
    // Arrange
    Path localFile = createTestFile(tempDir, "upload-test.dat", 5 * 1024 * 1024);
    
    TransferRequest request = TransferRequest.builder()
        .sourceUri(localFile.toUri())  // file:///tmp/...
        .destinationUri(URI.create("sftp://testuser:testpass@" + 
                                   sftpHost + ":" + sftpPort + 
                                   "/upload/uploaded-file.dat"))
        .build();
    
    // Act
    Future<TransferResult> future = engine.submitTransfer(request);
    TransferResult result = future.get(30, TimeUnit.SECONDS);
    
    // Assert
    assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
    assertEquals(5 * 1024 * 1024, result.getBytesTransferred());
    
    // Verify file exists on SFTP server
    assertTrue(sftpFileExists("/upload/uploaded-file.dat"));
}
```

---

#### 4.3 Update SftpAbortIntegrationTest

**File:** `quorus-core/src/test/java/dev/mars/quorus/protocol/SftpAbortIntegrationTest.java`

**Current Issue:** Needs to upload file to SFTP server first (the discovery issue!)

**Solution with Bidirectional API:**
```java
@BeforeEach
void setUp(Vertx vertx) throws Exception {
    // ...existing setup...
    
    // NOW we can use TransferRequest to upload the test file!
    createTestFileOnSftpServer();
}

private void createTestFileOnSftpServer() throws Exception {
    // Create local 10MB test file
    Path localTestFile = tempDir.resolve("large-file.bin");
    byte[] data = new byte[10 * 1024 * 1024];
    for (int i = 0; i < data.length; i++) {
        data[i] = (byte) (i % 256);
    }
    Files.write(localTestFile, data);
    
    // Upload using TransferRequest (bidirectional API)
    TransferRequest uploadRequest = TransferRequest.builder()
        .requestId("setup-upload-test-file")
        .sourceUri(localTestFile.toUri())  // file://...
        .destinationUri(URI.create("sftp://testuser:testpass@" + 
                                   sftpHost + ":" + sftpPort + 
                                   "/upload/large-file.bin"))
        .build();
    
    Future<TransferResult> uploadFuture = engine.submitTransfer(uploadRequest);
    TransferResult uploadResult = uploadFuture.get(30, TimeUnit.SECONDS);
    
    assertEquals(TransferStatus.COMPLETED, uploadResult.getFinalStatus());
    logger.info("Uploaded 10MB test file using TransferRequest API");
}
```

**This eliminates the JSch workaround and proves the API works correctly!**

---

### Phase 5: Documentation Updates

#### 5.1 API Documentation
**Files to Update:**
- `docs/QUORUS_USER_GUIDE.md`
- `docs/QUORUS_INTEGRATION_EXAMPLES_README.md`
- JavaDoc in `TransferRequest.java`

**New Sections:**
- "Bidirectional Transfers"
- "Upload Examples"
- "Direction Detection"
- "Migration Guide from 1.x to 2.x"

---

#### 5.2 Example Code Updates
**Files to Update:**
- `quorus-integration-examples/.../BasicTransferExample.java`
- `quorus-integration-examples/.../EnterpriseProtocolExample.java`
- All workflow examples

**Add New Examples:**
- `UploadExample.java` - Demonstrates upload capabilities
- `BidirectionalWorkflow.java` - Workflow with both downloads and uploads

---

### Phase 6: Breaking Change Management

> **NOTE:** Quorus is pre-production software. No backward compatibility or deprecation is required.
> Breaking changes are acceptable to achieve the cleanest API design.
> All `@Deprecated` annotations have been removed from the codebase.

#### 6.1 ~~Deprecation Strategy~~ (Not Applicable)

Since Quorus is pre-production:
- No `@Deprecated` annotations needed
- No phased migration timelines required
- Breaking changes implemented directly for clean API
- Old API patterns removed immediately when obsolete

---

#### 6.2 ~~Migration Script~~ (Not Applicable)

Not required for pre-production software.

---

### Phase 7: Performance and Security Considerations

#### 7.1 Upload Security
- **Authentication:** Validate credentials in destination URI
- **Authorization:** Check user permissions before upload
- **Validation:** Verify file size limits before starting upload
- **Path Traversal:** Sanitize remote paths to prevent `../../etc/passwd` attacks

**Add Validation:**
```java
private void validateRemotePath(String remotePath) throws TransferException {
    if (remotePath.contains("..")) {
        throw new TransferException("Path traversal not allowed: " + remotePath);
    }
    if (remotePath.startsWith("/etc/") || remotePath.startsWith("/root/")) {
        throw new TransferException("System directory access denied: " + remotePath);
    }
}
```

---

## Implementation Status

**Last Updated:** 2026-01-27

| Phase | Description | Duration | Priority | Status |
|-------|-------------|----------|----------|--------|
| 1. Core API | TransferRequest refactor, Direction enum | 2 days | **CRITICAL** | ✅ **COMPLETE** |
| 2.1 SFTP Upload | Upload method, routing, tests | 3 days | **HIGH** | ✅ **COMPLETE** |
| 2.2 FTP Upload | Upload method, routing, tests | 3 days | **HIGH** | ✅ **COMPLETE** |
| 2.3 HTTP Upload | POST/PUT support, tests | 2 days | **HIGH** | ✅ **COMPLETE** |
| 2.4 SMB Upload | Upload method, tests | 2 days | MEDIUM | ✅ **COMPLETE** |
| 3. Engine/Factory | Direction routing, validation | 1 day | **HIGH** | ✅ **COMPLETE** |
| 4. Test Updates | Migration, new upload tests | 3 days | **HIGH** | ✅ **COMPLETE** (905 tests pass) |
| 5. Documentation | Guide, examples, JavaDoc | 2 days | **HIGH** | ⏳ **PENDING** |
| 6. Migration Tools | Deprecation, scripts | 1 day | MEDIUM | N/A (Pre-production) |
| 7. Security/Perf | Validation, optimization | 2 days | **HIGH** | ⏳ **PENDING** |

**Overall Progress: ~80% Complete**

### Completed Work Summary

- **Phase 1**: `TransferRequest` now supports `destinationUri`, `TransferDirection` enum added
- **Phase 2**: All 4 protocols (SFTP, FTP, HTTP, SMB) support uploads with 62 upload tests passing
- **Phase 3**: `SimpleTransferEngine` and `ProtocolFactory` updated with direction-aware routing and metrics
- **Phase 4**: 905 tests passing in quorus-core module

### Pending Work

**Phase 5 - Documentation:**
- Update `docs/QUORUS_USER_GUIDE.md` with bidirectional transfer examples
- Update `docs/QUORUS_INTEGRATION_EXAMPLES_README.md` with upload documentation
- Create upload example code in `quorus-integration-examples/`

**Phase 7 - Security/Performance:**
- Add path traversal validation for remote paths
- Add system directory access prevention
- Performance benchmarking for uploads vs downloads

---

### Rollout Strategy

1. **Week 1**: Core API + SFTP upload (proves concept)
2. **Week 2**: FTP + HTTP uploads (completes main protocols)
3. **Week 3**: SMB + engine updates + test migration
4. **Week 4**: Documentation + examples + final integration testing

---

### Success Criteria

✅ All existing tests pass without modifica
✅ **Test coverage ≥90% for all new bidirectional code**
✅ **All phases include passing tests before proceeding to next phase**
✅ **Continuous integration passes at every commit**
✅ **Zero test flakiness in upload/download tests**tion (backward compatibility)
✅ New upload integration tests pass for SFTP, FTP, HTTP
✅ `SftpAbortIntegrationTest` uses TransferRequest to create test files (no JSch workaround)
✅ Zero security vulnerabilities in upload paths
✅ Upload performance matches download performance (throughput)
✅ Documentation complete with migration guide
✅ At least 3 upload examples in integration examples
✅ Code review approval from 2+ senior engineers
✅ Performance testing with files up to 1GB

---

### Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking changes affect production | **HIGH** | Phased deprecation, extensive testing, backward compatibility during v2.x |
| Upload security vulnerabilities | **HIGH** | Security review, path validation, authentication checks |
| Performance degradation | MEDIUM | Benchmark tests, buffer size optimization |
| Protocol-specific edge cases | MEDIUM | Extensive integration testing per protocol |
| Documentation gaps | LOW | Mandatory doc review before release |

---

## Next Steps

1. ✅ Review this implementation plan
2. ✅ Phase 1 (Core API changes) - **COMPLETE**
3. ✅ Phase 2 (Protocol uploads) - **COMPLETE**
4. ✅ Phase 3 (Engine/Factory) - **COMPLETE**
5. ✅ Phase 4 (Test updates) - **COMPLETE** (905 tests passing)
6. ⏳ Phase 5 (Documentation) - **PENDING**
7. ⏳ Phase 7 (Security/Performance) - **PENDING**

---

**Note:** This was discovered during abort() functionality testing. The test requires uploading a large file to SFTP server first, then downloading and aborting it. Current API doesn't support the upload part. **With this implementation plan, we fix the fundamental design flaw and unlock full bidirectional transfer capabilities.**
