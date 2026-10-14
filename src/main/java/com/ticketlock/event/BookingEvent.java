package com.ticketlock.event;

import com.ticketlock.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Event published to Kafka when booking state changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent {

    public enum EventType {
        BOOKING_CREATED,
        BOOKING_CONFIRMED,
        BOOKING_CANCELLED,
        BOOKING_EXPIRED
    }

    private EventType eventType;
    private String bookingReference;
    private Long bookingId;
    private Long userId;
    private String userEmail;
    private Long eventId;
    private String eventTitle;
    private BookingStatus status;
    private BigDecimal totalAmount;
    private List<String> seatNumbers;
    private LocalDateTime timestamp;
}
