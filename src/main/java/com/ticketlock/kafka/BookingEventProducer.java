package com.ticketlock.kafka;

import com.ticketlock.config.KafkaConfig;
import com.ticketlock.entity.Booking;
import com.ticketlock.event.BookingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventProducer {

    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;

    public void publish(BookingEvent.EventType eventType, Booking booking) {
        BookingEvent event = BookingEvent.builder()
                .eventType(eventType)
                .bookingReference(booking.getBookingReference())
                .bookingId(booking.getId())
                .userId(booking.getUser().getId())
                .userEmail(booking.getUser().getEmail())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .seatNumbers(booking.getBookingSeats().stream()
                        .map(bs -> bs.getSeat().getSeatNumber())
                        .collect(Collectors.toList()))
                .timestamp(LocalDateTime.now())
                .build();

        // Key by bookingReference for partition ordering per booking
        CompletableFuture<SendResult<String, BookingEvent>> future =
                kafkaTemplate.send(KafkaConfig.BOOKING_EVENTS_TOPIC, booking.getBookingReference(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} for booking {}", eventType, booking.getBookingReference(), ex);
            } else {
                log.info("Published {} for booking {} → partition={}, offset={}",
                        eventType,
                        booking.getBookingReference(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
