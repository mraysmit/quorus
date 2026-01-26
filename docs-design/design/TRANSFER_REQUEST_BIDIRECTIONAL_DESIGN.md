# TransferRequest Bidirectional Design Discussion

**Date:** 2026-01-26  
**Status:** DRAFT - For Review  
**Context:** Discovered during abort() testing implementation  

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

## Next Steps

1. Review this document
2. Decide on strategic direction
3. Choose implementation option if bidirectional needed
4. Update architecture review document
5. Create implementation tasks if approved

---

**Note:** This was discovered during abort() functionality testing. The test requires uploading a large file to SFTP server first, then downloading and aborting it. Current API doesn't support the upload part.
