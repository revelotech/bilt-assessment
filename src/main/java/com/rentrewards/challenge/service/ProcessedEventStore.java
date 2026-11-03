package com.rentrewards.challenge.service;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which webhook events have already been processed, so that the
 * RewardsEngine can ignore duplicate deliveries from the payment processor.
 *
 * <p>Every claimed eventId is remembered (not just the most recent one), so
 * retries that arrive out of order are still detected. Claiming is a single
 * atomic check-and-set, so two workers receiving the same event at the same
 * time cannot both proceed.
 */
public class ProcessedEventStore {

    private final Set<String> claimedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * Atomically claims an eventId for processing.
     *
     * @return true if the caller now owns this event and must process it;
     *         false if it was already claimed (a duplicate delivery).
     */
    public boolean tryClaim(String eventId) {
        return claimedEventIds.add(Objects.requireNonNull(eventId, "eventId"));
    }

    /**
     * Releases a claim whose processing failed, so a later retry of the
     * same event can still be processed instead of being lost.
     */
    public void release(String eventId) {
        claimedEventIds.remove(eventId);
    }

    /**
     * @return true if this eventId has already been claimed.
     */
    public boolean isDuplicate(String eventId) {
        return claimedEventIds.contains(eventId);
    }
}
