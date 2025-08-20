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

public interface WorkflowDefinitionParser {
    
    WorkflowDefinition parse(Path yamlFile) throws WorkflowParseException;
    
    WorkflowDefinition parseFromString(String yamlContent) throws WorkflowParseException;
    
    ValidationResult validate(WorkflowDefinition definition);
    
    DependencyGraph buildDependencyGraph(List<WorkflowDefinition> definitions) throws WorkflowParseException;
    
    /**
     * Validates the YAML schema before parsing.
     * 
     * @param yamlContent the YAML content to validate
     * @return validation result
     */
    ValidationResult validateSchema(String yamlContent);
}
