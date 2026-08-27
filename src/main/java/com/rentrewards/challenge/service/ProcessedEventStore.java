package com.rentrewards.challenge.service;

/**
 * Tracks which webhook events have already been processed, so that the
 * RewardsEngine can safely ignore duplicate deliveries from the payment
 * processor (at-least-once delivery guarantee on their side means we WILL
 * receive the same eventId more than once from time to time).
 */
public class ProcessedEventStore {

    private String lastProcessedEventId;

    /**
     * Attempts to record an event before it is processed.
     *
     * @return true when this caller claimed a new event, or false when the
     *         event had already been recorded.
     */
    public boolean recordIfNew(String eventId) {
        if (eventId.equals(lastProcessedEventId)) {
            return false;
        }

        this.lastProcessedEventId = eventId;
        return true;
    }
}
