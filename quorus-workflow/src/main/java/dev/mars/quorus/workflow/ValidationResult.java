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

package dev.mars.quorus.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ValidationResult {
    
    private final List<ValidationIssue> errors;
    private final List<ValidationIssue> warnings;
    
    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }
    
    public ValidationResult(List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        this.errors = new ArrayList<>(errors != null ? errors : List.of());
        this.warnings = new ArrayList<>(warnings != null ? warnings : List.of());
    }
    
    public void addError(String message) {
        errors.add(new ValidationIssue(ValidationIssue.Severity.ERROR, message));
    }
    
    public void addError(String fieldPath, String message) {
        errors.add(new ValidationIssue(ValidationIssue.Severity.ERROR, fieldPath, message));
    }
    
    public void addError(int lineNumber, String fieldPath, String message) {
        errors.add(new ValidationIssue(ValidationIssue.Severity.ERROR, lineNumber, fieldPath, message));
    }
    
    public void addWarning(String message) {
        warnings.add(new ValidationIssue(ValidationIssue.Severity.WARNING, message));
    }
    
    public void addWarning(String fieldPath, String message) {
        warnings.add(new ValidationIssue(ValidationIssue.Severity.WARNING, fieldPath, message));
    }
    
    public void addWarning(int lineNumber, String fieldPath, String message) {
        warnings.add(new ValidationIssue(ValidationIssue.Severity.WARNING, lineNumber, fieldPath, message));
    }
    
    public List<ValidationIssue> getErrors() {
        return List.copyOf(errors);
    }
    
    public List<ValidationIssue> getWarnings() {
        return List.copyOf(warnings);
    }
    
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public int getErrorCount() {
        return errors.size();
    }
    
    public int getWarningCount() {
        return warnings.size();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult{");
        sb.append("valid=").append(isValid());
        sb.append(", errors=").append(errors.size());
        sb.append(", warnings=").append(warnings.size());
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Represents a single validation issue (error or warning).
     */
    public static class ValidationIssue {
        
        public enum Severity {
            ERROR, WARNING
        }
        
        private final Severity severity;
        private final int lineNumber;
        private final String fieldPath;
        private final String message;
        
        public ValidationIssue(Severity severity, String message) {
            this(severity, -1, null, message);
        }
        
        public ValidationIssue(Severity severity, String fieldPath, String message) {
            this(severity, -1, fieldPath, message);
        }
        
        public ValidationIssue(Severity severity, int lineNumber, String fieldPath, String message) {
            this.severity = Objects.requireNonNull(severity, "Severity cannot be null");
            this.lineNumber = lineNumber;
            this.fieldPath = fieldPath;
            this.message = Objects.requireNonNull(message, "Message cannot be null");
        }
        
        public Severity getSeverity() {
            return severity;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
        
        public String getFieldPath() {
            return fieldPath;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValidationIssue that = (ValidationIssue) o;
            return lineNumber == that.lineNumber &&
                   severity == that.severity &&
                   Objects.equals(fieldPath, that.fieldPath) &&
                   Objects.equals(message, that.message);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(severity, lineNumber, fieldPath, message);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(severity.name());
            
            if (lineNumber > 0) {
                sb.append(" (line ").append(lineNumber).append(")");
            }
            
            if (fieldPath != null) {
                sb.append(" [").append(fieldPath).append("]");
            }
            
            sb.append(": ").append(message);
            
            return sb.toString();
        }
    }
}
