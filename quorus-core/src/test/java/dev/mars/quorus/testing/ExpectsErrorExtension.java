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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * JUnit 5 extension that prints clear visual banners around tests annotated
 * with {@link ExpectsError}, making intentional error logs immediately
 * distinguishable from real failures in test output.
 *
 * @see ExpectsError
 */
public class ExpectsErrorExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(ExpectsErrorExtension.class);

    private static final String TOP    = "========================================================================";
    private static final String BOTTOM = "========================================================================";

    @Override
    public void beforeEach(ExtensionContext context) {
        Method method = context.getRequiredTestMethod();
        ExpectsError annotation = method.getAnnotation(ExpectsError.class);
        if (annotation == null) {
            return;
        }

        String testName = method.getName();
        String reason = annotation.value();

        log.warn("\n{}\n  EXPECTED ERROR TEST: {}\n  Reason: {}\n  Errors below are INTENTIONAL — testing error handling paths\n{}",
                TOP, testName, reason, BOTTOM);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Method method = context.getRequiredTestMethod();
        ExpectsError annotation = method.getAnnotation(ExpectsError.class);
        if (annotation == null) {
            return;
        }

        String testName = method.getName();
        boolean passed = context.getExecutionException().isEmpty();
        String outcome = passed ? "PASSED" : "FAILED";

        log.warn("\n{}\n  END EXPECTED ERROR TEST: {} -- {}\n{}",
                TOP, testName, outcome, BOTTOM);
    }
}
