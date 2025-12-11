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

module dev.mars.quorus.examples {
    requires java.logging;
    requires transitive dev.mars.quorus.core;
    requires transitive dev.mars.quorus.workflow;
    requires transitive dev.mars.quorus.tenant;

    exports dev.mars.quorus.examples;
    exports dev.mars.quorus.examples.util;
}

