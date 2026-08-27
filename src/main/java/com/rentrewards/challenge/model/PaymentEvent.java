package com.rentrewards.challenge.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a rent/mortgage payment event received from the external
 * payment processor via webhook. The processor guarantees at-least-once
 * delivery, meaning the SAME event (same eventId) can be delivered more
 * than once, potentially out of order relative to other events.
 */
public class PaymentEvent {

    private final String eventId;
    private final String memberId;
    private final BigDecimal amount;
    private final boolean paidWithLinkedAccount;
    private final LocalDate paymentDate;

    public PaymentEvent(String eventId, String memberId, BigDecimal amount,
                         boolean paidWithLinkedAccount, LocalDate paymentDate) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.paidWithLinkedAccount = paidWithLinkedAccount;
        this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate");
    }

    public String getEventId() {
        return eventId;
    }

    public String getMemberId() {
        return memberId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isPaidWithLinkedAccount() {
        return paidWithLinkedAccount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }
}
