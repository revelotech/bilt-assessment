package com.rentrewards.challenge.service;

/**
 * Tracks which webhook events have already been processed, so that the
 * RewardsEngine can ignore duplicate deliveries from the payment processor.
 */
public class ProcessedEventStore {

    private final java.util.Set<String> processedEventIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * @return true if this eventId has already been processed before.
     */
    public boolean isDuplicate(String eventId) {
        return processedEventIds.contains(eventId);
    }

    public void markProcessed(String eventId) {
        processedEventIds.add(eventId);
    }

    /**
     * Atomically records the eventId if it has not been processed yet.
     *
     * @return true if this is the first time the event is recorded (not a duplicate),
     *         false if it was already recorded previously.
     */
    public boolean tryRecord(String eventId) {
        return processedEventIds.add(eventId);
    }
}
