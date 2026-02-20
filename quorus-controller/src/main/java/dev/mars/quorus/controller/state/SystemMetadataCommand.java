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

package dev.mars.quorus.controller.state;

/**
 * Description for SystemMetadataCommand
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public final class SystemMetadataCommand implements RaftCommand {

    private static final long serialVersionUID = 1L;

    public enum Type {
        SET,
        DELETE
    }

    private final Type type;
    private final String key;
    private final String value;

    private SystemMetadataCommand(Type type, String key, String value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    public static SystemMetadataCommand set(String key, String value) {
        return new SystemMetadataCommand(Type.SET, key, value);
    }

    public static SystemMetadataCommand delete(String key) {
        return new SystemMetadataCommand(Type.DELETE, key, null);
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    /**
     * Get the metadata value (for SET commands).
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "SystemMetadataCommand{" +
                "type=" + type +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
