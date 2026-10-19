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

import dev.mars.quorus.core.TransferRequest;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The transfer {@code options} a workflow may set (decision DR-Q1). Each option maps to a field of
 * the {@link TransferRequest} that the transfer engine or every protocol adapter acts on, so no
 * option is accepted and then ignored:
 *
 * <ul>
 *   <li>{@code checksum}: the expected SHA-256 of the transferred file, as 64 hexadecimal digits.
 *       Every adapter verifies it and fails the transfer on a mismatch. It becomes the request's
 *       expected checksum, in lower case, which is the form the adapters compute.</li>
 *   <li>{@code expectedSize}: the file's size in bytes, a whole number of zero or more. It becomes the
 *       request's expected size, which the engine reports as the transfer's total for progress.</li>
 * </ul>
 *
 * <p>Any other key fails validation. A value is checked when the workflow is validated if it is a
 * literal, and when the request is built if it is a variable reference; the same rules apply both
 * times.</p>
 */
final class TransferOptions {

    static final String CHECKSUM = "checksum";
    static final String EXPECTED_SIZE = "expectedSize";

    /** The defined option keys, in the order they are documented. */
    static final List<String> KEYS = List.of(CHECKSUM, EXPECTED_SIZE);

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]+");

    private TransferOptions() {
    }

    /**
     * Adds an error to {@code result} for each unknown key and each literal value that breaks its
     * rule. A string value that still holds a variable reference is checked when the request is built.
     *
     * @param path the field path of the transfer, for example {@code spec.transferGroups[0].transfers[1]}
     */
    static void validate(String path, Map<String, Object> options, ValidationResult result) {
        for (Map.Entry<String, Object> option : options.entrySet()) {
            String field = path + ".options." + option.getKey();
            Object value = option.getValue();
            if (!KEYS.contains(option.getKey())) {
                result.addError(field, "Unknown transfer option '" + option.getKey() + "'; the options are "
                        + String.join(" and ", KEYS));
            } else if (!(value instanceof String text && hasReference(text))) {
                String problem = problemWith(option.getKey(), value);
                if (problem != null) {
                    result.addError(field, problem);
                }
            }
        }
    }

    /**
     * Sets the request fields that the resolved options name.
     *
     * @throws IllegalArgumentException if a key is unknown or a value breaks its rule
     */
    static void apply(Map<String, Object> options, TransferRequest.Builder request) {
        for (Map.Entry<String, Object> option : options.entrySet()) {
            String key = option.getKey();
            Object value = option.getValue();
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown transfer option '" + key + "'");
            }
            String problem = problemWith(key, value);
            if (problem != null) {
                throw new IllegalArgumentException("Transfer option '" + key + "': " + problem);
            }
            if (CHECKSUM.equals(key)) {
                request.expectedChecksum(((String) value).toLowerCase(Locale.ROOT));
            } else {
                request.expectedSize(Long.parseLong(value.toString()));
            }
        }
    }

    /** Why {@code value} is not a valid value for the defined option {@code key}, or null if it is. */
    private static String problemWith(String key, Object value) {
        if (CHECKSUM.equals(key)) {
            return value instanceof String text && SHA256_HEX.matcher(text).matches()
                    ? null : "checksum must be a SHA-256 digest of 64 hexadecimal digits, but was '" + value + "'";
        }
        boolean wholeNumber = value instanceof Integer || value instanceof Long
                ? ((Number) value).longValue() >= 0
                : value instanceof String text && WHOLE_NUMBER.matcher(text).matches() && fitsInLong(text);
        return wholeNumber ? null : "expectedSize must be a whole number of bytes, zero or more, but was '" + value + "'";
    }

    private static boolean fitsInLong(String digits) {
        try {
            Long.parseLong(digits);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasReference(String text) {
        return text.contains("{{");
    }
}
