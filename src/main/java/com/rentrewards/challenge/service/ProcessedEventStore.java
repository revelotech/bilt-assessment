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
     * Atomically claims the eventId.
     *
     * @return true only for the first caller; false means a duplicate.
     */
    public boolean markIfNew(String eventId) {
        return processedEventIds.add(eventId);
    }
}
