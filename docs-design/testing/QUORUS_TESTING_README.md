# Quorus Testing Guide

## Command Syntax Reference

### Stream Redirection: `2>&1`

In PowerShell/Shell, there are two output streams:
- **Stream 1 (stdout)**: Standard output - normal command output
- **Stream 2 (stderr)**: Standard error - error messages and warnings

| Syntax | Meaning |
|--------|---------|
| `2>&1` | Redirect stderr (2) to stdout (1) - combines all output into one stream |
| `>` | Redirect stdout to file (overwrites) |
| `>>` | Redirect stdout to file (appends) |
| `2>` | Redirect stderr only to file |

**Why use `2>&1`?**  
Maven outputs some messages to stderr (like `[INFO]`, `[WARNING]`). Without `2>&1`, `Tee-Object` only captures stdout, missing important log lines.

### Pipeline: `|`

The pipe operator sends the output of one command as input to another:
```powershell
mvn test | Tee-Object -FilePath file.log
#         ↑
#         Output of 'mvn test' flows into 'Tee-Object'
```

### Tee-Object

`Tee-Object` splits output to both:
1. The console (so you can watch it)
2. A file (so you can review it later)

```powershell
command | Tee-Object -FilePath "path/to/file.log"
```

### Variable Interpolation: `$()`

Inside double-quoted strings, `$()` executes a command and inserts the result:
```powershell
"logs/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
#           ↑
#           Becomes: logs/test-20260206-143022.log
```

---

## Running Tests with Console and File Output

### Option 1: Output to target directory (Maven convention, gitignored)
```powershell
mvn test 2>&1 | Tee-Object -FilePath target/test-output.log
```

### Option 2: Output to logs directory
```powershell
mvn test 2>&1 | Tee-Object -FilePath logs/test-output.log
```

### Option 3: Timestamped output in logs directory
```powershell
mvn test 2>&1 | Tee-Object -FilePath "logs/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
```

## Running Specific Module Tests

```powershell
# Single module
mvn test -pl quorus-core 2>&1 | Tee-Object -FilePath target/test-core.log

# Multiple modules
mvn test -pl quorus-core,quorus-controller 2>&1 | Tee-Object -FilePath target/test-output.log
```

## Excluding Flaky Tests

```powershell
mvn test -Dgroups='!flaky' 2>&1 | Tee-Object -FilePath target/test-stable.log
```

## Running Only Negative Tests

```powershell
mvn test -Dgroups='negative' 2>&1 | Tee-Object -FilePath target/test-negative.log
```

## Running Tests with Coverage

```powershell
mvn test jacoco:report 2>&1 | Tee-Object -FilePath target/test-coverage.log
```

## Background Test Execution with Live Tail

Terminal 1 - Start tests in background:
```powershell
Start-Process -NoNewWindow -FilePath mvn -ArgumentList "test" -RedirectStandardOutput target/test-output.log -RedirectStandardError target/test-errors.log
```

Terminal 2 - Tail the log:
```powershell
Get-Content -Path target/test-output.log -Wait -Tail 100
```

## Quick Build Without Tests

```powershell
mvn compile -pl quorus-core,quorus-controller,quorus-api,quorus-agent -q
```
