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

/**
 * Exception thrown when workflow parsing or validation fails.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-18
 * @version 1.0
 */
public class WorkflowParseException extends Exception {
    
    private final String workflowName;
    private final int lineNumber;
    private final String fieldPath;
    
    public WorkflowParseException(String message) {
        super(message);
        this.workflowName = null;
        this.lineNumber = -1;
        this.fieldPath = null;
    }
    
    public WorkflowParseException(String message, Throwable cause) {
        super(message, cause);
        this.workflowName = null;
        this.lineNumber = -1;
        this.fieldPath = null;
    }
    
    public WorkflowParseException(String workflowName, String message) {
        super(message);
        this.workflowName = workflowName;
        this.lineNumber = -1;
        this.fieldPath = null;
    }
    
    public WorkflowParseException(String workflowName, String message, Throwable cause) {
        super(message, cause);
        this.workflowName = workflowName;
        this.lineNumber = -1;
        this.fieldPath = null;
    }
    
    public WorkflowParseException(String workflowName, int lineNumber, String fieldPath, String message) {
        super(message);
        this.workflowName = workflowName;
        this.lineNumber = lineNumber;
        this.fieldPath = fieldPath;
    }
    
    public WorkflowParseException(String workflowName, int lineNumber, String fieldPath, String message, Throwable cause) {
        super(message, cause);
        this.workflowName = workflowName;
        this.lineNumber = lineNumber;
        this.fieldPath = fieldPath;
    }
    
    public String getWorkflowName() {
        return workflowName;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
    
    public String getFieldPath() {
        return fieldPath;
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        
        if (workflowName != null) {
            sb.append("Workflow '").append(workflowName).append("': ");
        }
        
        if (lineNumber > 0) {
            sb.append("Line ").append(lineNumber).append(": ");
        }
        
        if (fieldPath != null) {
            sb.append("Field '").append(fieldPath).append("': ");
        }
        
        sb.append(super.getMessage());
        
        return sb.toString();
    }
}
