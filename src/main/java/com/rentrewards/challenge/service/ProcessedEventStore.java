package com.rentrewards.challenge.service;

/**
 * Tracks which webhook events have already been processed, so that the
 * RewardsEngine can ignore duplicate deliveries from the payment processor.
 */
public class ProcessedEventStore {

    private String lastProcessedEventId;

    /**
     * @return true if this eventId has already been processed before.
     */
    public boolean isDuplicate(String eventId) {
        return eventId.equals(lastProcessedEventId);
    }

    public void markProcessed(String eventId) {
        this.lastProcessedEventId = eventId;
    }
}
