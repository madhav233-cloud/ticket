package com.ticketlock.kafka;

import com.ticketlock.config.KafkaConfig;
import com.ticketlock.event.BookingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Simulates a Notification Service that reacts to booking events.
 * In a real system this would send emails / push notifications / SMS.
 */
@Component
@Slf4j
public class BookingEventConsumer {

    @KafkaListener(
            topics = KafkaConfig.BOOKING_EVENTS_TOPIC,
            groupId = "ticketlock-notification-group",
            containerFactory = "bookingEventKafkaListenerContainerFactory"
    )
    public void handleBookingEvent(BookingEvent event) {
        switch (event.getEventType()) {
            case BOOKING_CREATED -> log.info(
                    "📧 [NOTIFICATION] Booking held for user={} | ref={} | seats={} | amount={} | expires soon",
                    event.getUserEmail(), event.getBookingReference(),
                    event.getSeatNumbers(), event.getTotalAmount());

            case BOOKING_CONFIRMED -> log.info(
                    "✅ [NOTIFICATION] Booking CONFIRMED for user={} | ref={} | event='{}' | seats={}",
                    event.getUserEmail(), event.getBookingReference(),
                    event.getEventTitle(), event.getSeatNumbers());

            case BOOKING_CANCELLED -> log.info(
                    "❌ [NOTIFICATION] Booking CANCELLED for user={} | ref={}",
                    event.getUserEmail(), event.getBookingReference());

            case BOOKING_EXPIRED -> log.info(
                    "⏰ [NOTIFICATION] Booking EXPIRED for user={} | ref={} | seats released",
                    event.getUserEmail(), event.getBookingReference());
        }
    }
}
