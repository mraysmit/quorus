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

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;

import java.io.Serializable;

public class TransferJobCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        CREATE,
        UPDATE_STATUS,
        DELETE
    }

    private final Type type;
    private final String jobId;
    private final TransferJob transferJob;
    private final TransferStatus status;

    private TransferJobCommand(Type type, String jobId, TransferJob transferJob, TransferStatus status) {
        this.type = type;
        this.jobId = jobId;
        this.transferJob = transferJob;
        this.status = status;
    }

    public static TransferJobCommand create(TransferJob transferJob) {
        return new TransferJobCommand(Type.CREATE, transferJob.getJobId(), transferJob, null);
    }

    public static TransferJobCommand updateStatus(String jobId, TransferStatus status) {
        return new TransferJobCommand(Type.UPDATE_STATUS, jobId, null, status);
    }

    public static TransferJobCommand delete(String jobId) {
        return new TransferJobCommand(Type.DELETE, jobId, null, null);
    }

    public Type getType() {
        return type;
    }

    public String getJobId() {
        return jobId;
    }

    public TransferJob getTransferJob() {
        return transferJob;
    }

    /**
     * Get the status (for UPDATE_STATUS commands).
     */
    public TransferStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "TransferJobCommand{" +
                "type=" + type +
                ", jobId='" + jobId + '\'' +
                ", status=" + status +
                '}';
    }
}
