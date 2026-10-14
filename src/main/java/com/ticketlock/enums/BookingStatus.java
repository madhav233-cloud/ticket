package com.ticketlock.enums;

public enum BookingStatus {
    HELD,              // Seats temporarily locked
    PENDING_PAYMENT,   // User moved to payment
    CONFIRMED,         // Payment successful
    EXPIRED,           // Hold timed out
    CANCELLED          // User or system cancelled
}
