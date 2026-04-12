package com.solesonic.mcp.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressReporterTest {

    @Test
    void emitClampsToZeroWhenRequestedPercentIsBelowZero() {
        AtomicInteger emittedPercent = new AtomicInteger(-1);
        ProgressReporter progressReporter = new ProgressReporter((percent, _) -> emittedPercent.set(percent));

        progressReporter.emit(-10, "below zero");

        assertEquals(0, emittedPercent.get());
    }

    @Test
    void emitClampsToOneHundredWhenRequestedPercentIsAboveOneHundred() {
        AtomicInteger emittedPercent = new AtomicInteger(-1);
        ProgressReporter progressReporter = new ProgressReporter((percent, _) -> emittedPercent.set(percent));

        progressReporter.emit(999, "above one hundred");

        assertEquals(100, emittedPercent.get());
    }

    @Test
    void emitIsMonotonicWhenLaterRequestedPercentIsLower() {
        List<Integer> emitted = new ArrayList<>();
        ProgressReporter progressReporter = new ProgressReporter((percent, _) -> emitted.add(percent));

        progressReporter.emit(50, "first");
        progressReporter.emit(5, "second");

        assertEquals(2, emitted.size());
        assertEquals(50, emitted.getFirst());
        assertEquals(50, emitted.get(1));
    }

    @Test
    void emitRemainsMonotonicUnderConcurrentExecution() throws Exception {
        List<Integer> emitted = new ArrayList<>();
        Object lock = new Object();
        ProgressReporter progressReporter = new ProgressReporter((percent, message) -> {
            synchronized (lock) {
                emitted.add(percent);
            }
        });

        ExecutorService executorService = Executors.newFixedThreadPool(8);
        int threadCount = 40;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int index = 0; index < threadCount; index++) {
            final int requestedPercent = (index * 7) % 101;
            futures.add(executorService.submit(() -> {
                startLatch.await();
                progressReporter.emit(requestedPercent, "parallel");
                return null;
            }));
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));

        int previous = 0;
        List<Integer> monotonicCheck;

        synchronized (lock) {
            monotonicCheck = new ArrayList<>(emitted);
        }

        monotonicCheck.sort(Integer::compareTo);

        for (Integer current : monotonicCheck) {
            assertTrue(current >= previous);
                previous = current;
        }
    }
}