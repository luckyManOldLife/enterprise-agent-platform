package com.xr.agent.worker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerPollingLoopTest {

    @Test
    void continuesPollingAfterABatchFailureAndStopsCleanly() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger reportedFailures = new AtomicInteger();
        CountDownLatch successfulRetry = new CountDownLatch(1);
        WorkerPollingLoop loop = new WorkerPollingLoop(
                ignored -> {
                    if (invocations.incrementAndGet() == 1) {
                        throw new IllegalStateException("database unavailable");
                    }
                    successfulRetry.countDown();
                },
                10,
                Duration.ofMillis(1),
                duration -> Thread.sleep(duration),
                reportedFailures::incrementAndGet);

        Thread thread = Thread.ofVirtual().start(loop::runUntilStopped);
        assertTrue(successfulRetry.await(1, TimeUnit.SECONDS));
        loop.stop();
        thread.join(1_000);

        assertEquals(1, reportedFailures.get());
        assertTrue(invocations.get() >= 2);
        assertTrue(!thread.isAlive());
    }
}
