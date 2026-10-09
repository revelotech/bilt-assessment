package com.rentrewards.challenge.service;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which webhook events have already been processed, so that the
 * RewardsEngine can ignore duplicate deliveries from the payment processor.
 */
public class ProcessedEventStore {

    private final Set<String> processedEventIds = new HashSet<>();

    /**
     * @return true if this eventId has already been processed before.
     */
    public synchronized boolean isDuplicate(String eventId) {
        return processedEventIds.contains(eventId);
    }

    public synchronized void markProcessed(String eventId) {
        processedEventIds.add(eventId);
    }
}
