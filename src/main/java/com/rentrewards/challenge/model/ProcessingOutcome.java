package com.rentrewards.challenge.model;

/**
 * Distinguishes why a payment produced the returned point total.
 */
public enum ProcessingOutcome {
    AWARDED,
    DUPLICATE,
    CAPPED
}
