package com.rentrewards.challenge.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which webhook events have already been processed, so that the
 * RewardsEngine can ignore duplicate deliveries from the payment processor.
 */
public class ProcessedEventStore {

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * Atomically claims an event, including while its processing is in progress.
     * @return true only for the worker that first registers this eventId.
     */
    public boolean registerIfAbsent(String eventId) {
        return processedEventIds.add(eventId);
    }

    /** Allows a later retry when processing failed before points were credited. */
    public void release(String eventId) {
        processedEventIds.remove(eventId);
    }
}
