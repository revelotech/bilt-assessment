package com.rentrewards.challenge.service;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks every webhook event that has already been processed, so that the
 * RewardsEngine can ignore duplicate deliveries from the payment processor,
 * including retries that arrive out of order.
 *
 * RewardsEngine holds this store's monitor for its whole check-and-award
 * flow, so engines sharing a store are serialized against each other.
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
