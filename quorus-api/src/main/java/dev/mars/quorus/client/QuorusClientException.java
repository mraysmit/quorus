/*
 * Copyright 2024 Quorus Project
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

package dev.mars.quorus.client;

/**
 * Exception thrown by the Quorus client when API operations fail.
 */
public class QuorusClientException extends Exception {

    /**
     * Create a new QuorusClientException with a message.
     * 
     * @param message Error message
     */
    public QuorusClientException(String message) {
        super(message);
    }

    /**
     * Create a new QuorusClientException with a message and cause.
     * 
     * @param message Error message
     * @param cause Underlying cause
     */
    public QuorusClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new QuorusClientException with a cause.
     * 
     * @param cause Underlying cause
     */
    public QuorusClientException(Throwable cause) {
        super(cause);
    }
}
