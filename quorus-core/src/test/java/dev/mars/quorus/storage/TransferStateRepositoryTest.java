package dev.mars.quorus.storage;

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

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransferStateRepositoryTest {

    @Test
    void testSaveLoadAndRemoveTransferState() {
        TransferStateRepository repository = new TransferStateRepository();
        TransferJob job = createJob("job-1");
        job.start();
        job.updateProgress(25);

        repository.saveTransferState(job);

        assertTrue(repository.hasTransferState("job-1"));
        assertEquals(1, repository.getTransferStateCount());

        TransferStateRepository.TransferState loaded = repository.loadTransferState("job-1");
        assertNotNull(loaded);
        assertEquals("job-1", loaded.getJobId());
        assertEquals(TransferStatus.IN_PROGRESS, loaded.getStatus());
        assertEquals(25L, loaded.getBytesTransferred());
        assertEquals(100L, loaded.getTotalBytes());

        Map<String, TransferStateRepository.TransferState> allStates = repository.getAllTransferStates();
        assertEquals(1, allStates.size());
        assertThrows(UnsupportedOperationException.class, () -> allStates.put("x", loaded));

        repository.removeTransferState("job-1");
        assertFalse(repository.hasTransferState("job-1"));
        assertNull(repository.loadTransferState("job-1"));
        assertEquals(0, repository.getTransferStateCount());
    }

    @Test
    void testClearAll() {
        TransferStateRepository repository = new TransferStateRepository();
        TransferJob job1 = createJob("job-a");
        TransferJob job2 = createJob("job-b");

        repository.saveTransferState(job1);
        repository.saveTransferState(job2);
        assertEquals(2, repository.getTransferStateCount());

        repository.clearAll();
        assertEquals(0, repository.getTransferStateCount());
    }

    @Test
    void testCleanupOldTransfersRemovesOnlyTerminalOldStates() throws InterruptedException {
        TransferStateRepository repository = new TransferStateRepository();

        TransferJob terminal = createJob("job-terminal");
        terminal.start();
        terminal.updateProgress(100);
        terminal.complete("abc123");

        TransferJob active = createJob("job-active");
        active.start();
        active.updateProgress(50);

        repository.saveTransferState(terminal);
        repository.saveTransferState(active);

        Thread.sleep(2);
        repository.cleanupOldTransfers(0);

        assertFalse(repository.hasTransferState("job-terminal"));
        assertTrue(repository.hasTransferState("job-active"));
    }

    @Test
    void testTransferStateProgressAndToString() {
        Instant now = Instant.now();
        TransferStateRepository.TransferState state = new TransferStateRepository.TransferState(
                "job-x",
                TransferStatus.IN_PROGRESS,
                30,
                120,
                now,
                now,
                null,
                null
        );

        assertEquals(0.25, state.getProgressPercentage(), 0.0001);
        assertTrue(state.toString().contains("job-x"));

        TransferStateRepository.TransferState capped = new TransferStateRepository.TransferState(
                "job-y",
                TransferStatus.COMPLETED,
                200,
                100,
                now,
                now,
                "checksum",
                null
        );
        assertEquals(1.0, capped.getProgressPercentage(), 0.0001);

        TransferStateRepository.TransferState zeroTotal = new TransferStateRepository.TransferState(
                "job-z",
                TransferStatus.PENDING,
                10,
                0,
                now,
                now,
                null,
                "none"
        );
        assertEquals(0.0, zeroTotal.getProgressPercentage(), 0.0001);
    }

    private static TransferJob createJob(String requestId) {
        TransferRequest request = TransferRequest.builder()
                .requestId(requestId)
                .sourceUri(URI.create("sftp://source.example/data.bin"))
                .destinationUri(Path.of("target", requestId + ".bin").toAbsolutePath().toUri())
                .expectedSize(100)
                .build();
        return new TransferJob(request);
    }
}
