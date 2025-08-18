# Intentional Failures in Quorus Examples

This document explains how intentional test failures are handled in the Quorus integration examples to avoid confusion between expected validation failures and actual bugs.

## Overview

Several examples in this project intentionally test failure scenarios to demonstrate validation capabilities. These are **not errors** - they are expected behaviors that show the system is working correctly.

## How to Identify Intentional vs Actual Failures

### ✅ Expected/Intentional Failures
- Marked with `✓ EXPECTED:` in the output
- Clearly documented in code comments as "INTENTIONAL FAILURE TEST"
- Do **not** print stack traces (only error messages)
- Indicate the validation system is working correctly

### ❌ Unexpected/Actual Failures  
- Marked with `✗ UNEXPECTED:` in the output
- Print full stack traces for debugging
- Indicate real bugs or problems that need investigation

## Examples with Intentional Failures

### WorkflowValidationExample

This example specifically tests validation capabilities:

1. **Invalid YAML Syntax Test** - Tests parser's ability to reject malformed YAML
2. **Missing Required Fields Test** - Tests validation of required workflow fields  
3. **Circular Dependencies Test** - Tests detection of circular dependency graphs
4. **Missing Dependencies Test** - Tests detection of references to non-existent dependencies
5. **Variable Resolution Test** - Tests detection of undefined variables

**Expected Output Pattern:**
```
2. Testing invalid YAML syntax (INTENTIONAL FAILURE TEST)...
   ✓ EXPECTED: Correctly caught YAML syntax error: Invalid YAML syntax at line 6
```

**What NOT to see:**
```
   ✗ UNEXPECTED: Should have failed but didn't - this indicates a bug!
```

## Error Handling Improvements

### Before
- All exceptions printed stack traces, including intentional test failures
- No clear distinction between expected and unexpected failures
- Confusing output that made users think there were bugs

### After  
- Intentional failures clearly marked as `EXPECTED`
- Stack traces only for unexpected errors
- Clear documentation in code and output
- Dedicated `TestResultLogger` utility for consistent messaging

## Logging Configuration

The examples now include:
- `logging.properties` for controlling log levels
- `TestResultLogger` utility for consistent error reporting
- Clear separation between test output and actual errors

## Running Examples

When running examples, you should see:
- Clear section headers indicating intentional failure tests
- `✓ EXPECTED:` for successful validation (catching bad input)
- `✓ EXPECTED:` for successful operations  
- `✗ UNEXPECTED:` only for real problems

## For Developers

When adding new validation tests:

1. **Mark intentional failures clearly:**
   ```java
   /**
    * Tests invalid input handling.
    * This is an INTENTIONAL FAILURE TEST - the system should reject invalid input.
    */
   ```

2. **Use TestResultLogger:**
   ```java
   try {
       // Test code that should fail
       TestResultLogger.logUnexpectedResult("Should have failed but didn't");
   } catch (ExpectedException e) {
       TestResultLogger.logExpectedFailure("Correctly caught error", e);
   }
   ```

3. **Don't print stack traces for expected failures:**
   ```java
   // ❌ Don't do this for intentional failures
   e.printStackTrace();
   
   // ✅ Do this instead
   TestResultLogger.logExpectedFailure("Expected error occurred", e);
   ```

## Summary

The key principle is: **If it's intentional, don't make it look scary!**

- Expected validation failures = Good (system working correctly)
- Unexpected errors = Bad (real problems to investigate)
- Clear labeling helps users understand what they're seeing
