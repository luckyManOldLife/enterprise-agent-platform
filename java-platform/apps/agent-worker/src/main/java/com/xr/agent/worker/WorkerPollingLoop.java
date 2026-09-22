package com.xr.agent.worker;

import java.time.Duration;
import java.util.Objects;
import java.util.function.IntConsumer;

final class WorkerPollingLoop {

    private final IntConsumer processor;
    private final int batchSize;
    private final Duration pollInterval;
    private final Sleeper sleeper;
    private final Runnable failureReporter;
    private volatile boolean running;

    WorkerPollingLoop(
            IntConsumer processor,
            int batchSize,
            Duration pollInterval,
            Sleeper sleeper,
            Runnable failureReporter) {
        this.processor = Objects.requireNonNull(processor, "processor");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        this.pollInterval = pollInterval;
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter");
    }

    WorkerPollingLoop(IntConsumer processor, int batchSize, Duration pollInterval) {
        this(processor, batchSize, pollInterval, Thread::sleep, () ->
                System.err.println("agent-worker polling failed"));
    }

    void runUntilStopped() {
        running = true;
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                processor.accept(batchSize);
            } catch (RuntimeException exception) {
                failureReporter.run();
            }
            if (!running) {
                break;
            }
            try {
                sleeper.sleep(pollInterval);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        running = false;
    }

    void stop() {
        running = false;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
