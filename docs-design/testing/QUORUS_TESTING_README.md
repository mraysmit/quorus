# Quorus Testing Guide

## Command Syntax Reference

### Stream Redirection: `2>&1`

In PowerShell/Bash, there are two output streams:
- **Stream 1 (stdout)**: Standard output - normal command output
- **Stream 2 (stderr)**: Standard error - error messages and warnings

| Syntax | Meaning |
|--------|---------|
| `2>&1` | Redirect stderr (2) to stdout (1) - combines all output into one stream |
| `>` | Redirect stdout to file (overwrites) |
| `>>` | Redirect stdout to file (appends) |
| `2>` | Redirect stderr only to file |

**Why use `2>&1`?**  
Maven outputs some messages to stderr (like `[INFO]`, `[WARNING]`). Without `2>&1`, `tee` / `Tee-Object` only captures stdout, missing important log lines.

### Pipeline: `|`

The pipe operator sends the output of one command as input to another:

**PowerShell:**
```powershell
mvn test | Tee-Object -FilePath file.log
#         ↑
#         Output of 'mvn test' flows into 'Tee-Object'
```

**Linux/Bash:**
```bash
mvn test | tee file.log
#         ↑
#         Output of 'mvn test' flows into 'tee'
```

### Tee-Object (PowerShell) / tee (Linux)

Splits output to both:
1. The console (so you can watch it)
2. A file (so you can review it later)

**PowerShell:**
```powershell
command | Tee-Object -FilePath "path/to/file.log"
```

**Linux/Bash:**
```bash
command | tee path/to/file.log
```

### Variable Interpolation / Command Substitution

**PowerShell — `$()`:**
```powershell
"logs/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
#           ↑
#           Becomes: logs/test-20260206-143022.log
```

**Linux/Bash — `$()`:**
```bash
"logs/test-$(date +'%Y%m%d-%H%M%S').log"
#           ↑
#           Becomes: logs/test-20260206-143022.log
```

---

## Running Tests with Console and File Output

### Option 1: Output to target directory (Maven convention, gitignored)

**PowerShell:**
```powershell
mvn test 2>&1 | Tee-Object -FilePath target/test-output.log
```

**Linux/Bash:**
```bash
mvn test 2>&1 | tee target/test-output.log
```

### Option 2: Output to logs directory

**PowerShell:**
```powershell
mvn test 2>&1 | Tee-Object -FilePath logs/test-output.log
```

**Linux/Bash:**
```bash
mvn test 2>&1 | tee logs/test-output.log
```

### Option 3: Timestamped output in logs directory

**PowerShell:**
```powershell
mvn test 2>&1 | Tee-Object -FilePath "logs/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

**Linux/Bash:**
```bash
mvn test 2>&1 | tee "logs/test-$(date +'%Y%m%d-%H%M%S').log"
```

## Running Specific Module Tests

**PowerShell:**
```powershell
# Single module
mvn test -pl quorus-core 2>&1 | Tee-Object -FilePath target/test-core.log

# Multiple modules
mvn test -pl quorus-core,quorus-controller 2>&1 | Tee-Object -FilePath target/test-output.log
```

**Linux/Bash:**
```bash
# Single module
mvn test -pl quorus-core 2>&1 | tee target/test-core.log

# Multiple modules
mvn test -pl quorus-core,quorus-controller 2>&1 | tee target/test-output.log
```

## Excluding Flaky Tests

**PowerShell:**
```powershell
mvn test -Dgroups='!flaky' 2>&1 | Tee-Object -FilePath target/test-stable.log
```

**Linux/Bash:**
```bash
mvn test -Dgroups='!flaky' 2>&1 | tee target/test-stable.log
```

## Running Only Negative Tests

**PowerShell:**
```powershell
mvn test -Dgroups='negative' 2>&1 | Tee-Object -FilePath target/test-negative.log
```

**Linux/Bash:**
```bash
mvn test -Dgroups='negative' 2>&1 | tee target/test-negative.log
```

## Running Tests with Coverage

**PowerShell:**
```powershell
mvn test jacoco:report 2>&1 | Tee-Object -FilePath target/test-coverage.log
```

**Linux/Bash:**
```bash
mvn test jacoco:report 2>&1 | tee target/test-coverage.log
```

## Background Test Execution with Live Tail

**PowerShell:**

Terminal 1 - Start tests in background:
```powershell
Start-Process -NoNewWindow -FilePath mvn -ArgumentList "test" -RedirectStandardOutput target/test-output.log -RedirectStandardError target/test-errors.log
```

Terminal 2 - Tail the log:
```powershell
Get-Content -Path target/test-output.log -Wait -Tail 100
```

**Linux/Bash:**

Terminal 1 - Start tests in background:
```bash
nohup mvn test > target/test-output.log 2> target/test-errors.log &
```

Terminal 2 - Tail the log:
```bash
tail -f target/test-output.log
```

## Quick Build Without Tests

**PowerShell:**
```powershell
mvn compile -pl quorus-core,quorus-controller,quorus-api,quorus-agent -q
```

**Linux/Bash:**
```bash
mvn compile -pl quorus-core,quorus-controller,quorus-api,quorus-agent -q
```
