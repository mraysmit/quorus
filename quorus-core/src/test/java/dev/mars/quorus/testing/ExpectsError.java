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

package dev.mars.quorus.testing;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method that intentionally triggers error-level log output.
 * <p>
 * When applied, the {@link ExpectsErrorExtension} prints clear visual banners
 * around the test execution so that ERROR logs from protocol adapters and other
 * production code are immediately recognisable as planned test behaviour — not
 * real failures.
 * <p>
 * Example output:
 * <pre>
 * ========================================================================
 *   EXPECTED ERROR TEST: testFtpsConnectionTimeout
 *   Reason: Verifies TransferException on connection refused
 *   Errors below are INTENTIONAL — testing error handling paths
 * ========================================================================
 * ... protocol ERROR logs appear here ...
 * ========================================================================
 *   END EXPECTED ERROR TEST: testFtpsConnectionTimeout -- PASSED
 * ========================================================================
 * </pre>
 * <p>
 * Usage:
 * <pre>
 * {@literal @}Test
 * {@literal @}ExpectsError("Verifies TransferException on connection refused")
 * void testFtpsConnectionTimeout() {
 *     assertThrows(TransferException.class, () -&gt; protocol.transfer(request, context));
 * }
 * </pre>
 *
 * @see ExpectsErrorExtension
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ExpectsErrorExtension.class)
public @interface ExpectsError {
    /**
     * Brief description of what error is expected and why.
     * Displayed in the banner so log readers understand the intent.
     */
    String value() default "This test intentionally triggers error-level log output";
}
