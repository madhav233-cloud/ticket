package com.ticketlock.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateBookingRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotEmpty(message = "At least one seat must be selected")
    private List<Long> seatIds;

    /**
     * Client-generated unique key for idempotency.
     * Send the same key on retries to avoid duplicate bookings.
     */
    @NotNull(message = "Idempotency key is required")
    private String idempotencyKey;
}
