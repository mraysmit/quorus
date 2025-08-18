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

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for parsing and validating YAML workflow definitions.
 * Provides methods for parsing individual workflows and building dependency graphs.
 */
public interface WorkflowDefinitionParser {
    
    /**
     * Parses a YAML file into a WorkflowDefinition.
     * 
     * @param yamlFile the path to the YAML file
     * @return the parsed workflow definition
     * @throws WorkflowParseException if parsing fails
     */
    WorkflowDefinition parse(Path yamlFile) throws WorkflowParseException;
    
    /**
     * Parses a YAML string into a WorkflowDefinition.
     * 
     * @param yamlContent the YAML content as a string
     * @return the parsed workflow definition
     * @throws WorkflowParseException if parsing fails
     */
    WorkflowDefinition parseFromString(String yamlContent) throws WorkflowParseException;
    
    /**
     * Validates a workflow definition for semantic correctness.
     * 
     * @param definition the workflow definition to validate
     * @return validation result with any errors or warnings
     */
    ValidationResult validate(WorkflowDefinition definition);
    
    /**
     * Builds a dependency graph from multiple workflow definitions.
     * 
     * @param definitions the list of workflow definitions
     * @return the dependency graph
     * @throws WorkflowParseException if dependency resolution fails
     */
    DependencyGraph buildDependencyGraph(List<WorkflowDefinition> definitions) throws WorkflowParseException;
    
    /**
     * Validates the YAML schema before parsing.
     * 
     * @param yamlContent the YAML content to validate
     * @return validation result
     */
    ValidationResult validateSchema(String yamlContent);
}
