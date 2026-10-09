package com.rentrewards.challenge.service;

import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessedEventStoreTest {

    private ProcessedEventStore store;

    @BeforeEach
    void setUp() {
        store = new ProcessedEventStore();
    }

    @Test
    void isDuplicateReturnsFalseForUnseenEvent() {
        assertFalse(store.isDuplicate("evt-1"));
    }

    @Test
    void markProcessedMakesEventADuplicate() {
        store.markProcessed("evt-1");
        assertTrue(store.isDuplicate("evt-1"));
    }

    @Test
    void remembersMultipleDistinctEventsOutOfOrder() {
        store.markProcessed("evt-1");
        store.markProcessed("evt-2");
        store.markProcessed("evt-3");

        assertTrue(store.isDuplicate("evt-1"));
        assertTrue(store.isDuplicate("evt-2"));
        assertTrue(store.isDuplicate("evt-3"));
        assertFalse(store.isDuplicate("evt-4"));
    }

    @Test
    void tryRecordReturnsTrueForFirstAttemptAndFalseSubsequently() {
        assertTrue(store.tryRecord("evt-1"));
        assertFalse(store.tryRecord("evt-1"));
        assertTrue(store.isDuplicate("evt-1"));
    }

    @Test
    void tryRecordIsThreadSafeUnderHighConcurrency() throws Exception {
        int threads = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successfulRecords = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (store.tryRecord("evt-concurrent-test")) {
                        successfulRecords.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();

        for (Future<?> future : futures) {
            future.get(2, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertEquals(1, successfulRecords.get(), "Exactamente 1 hilo debe registrar el evento con éxito");
        assertTrue(store.isDuplicate("evt-concurrent-test"));
    }
}
