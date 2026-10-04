package com.ticketlock.service;

import com.ticketlock.entity.Booking;
import com.ticketlock.entity.BookingSeat;
import com.ticketlock.entity.Seat;
import com.ticketlock.enums.BookingStatus;
import com.ticketlock.enums.SeatStatus;
import com.ticketlock.repository.BookingRepository;
import com.ticketlock.event.BookingEvent;
import com.ticketlock.kafka.BookingEventProducer;
import com.ticketlock.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Periodically finds expired HELD bookings and releases resources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;
    private final BookingEventProducer bookingEventProducer;

    /**
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void expireHeldBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredBookings = bookingRepository
                .findByStatusAndHoldExpiresAtBefore(BookingStatus.HELD, now);

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Found {} expired booking(s) to clean up", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            try {
                expireBooking(booking);
            } catch (Exception e) {
                log.error("Failed to expire booking ref={}", booking.getBookingReference(), e);
            }
        }
    }

    private void expireBooking(Booking booking) {
        List<Long> seatIds = booking.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .collect(Collectors.toList());

        // 1. Release seats back to AVAILABLE
        for (BookingSeat bs : booking.getBookingSeats()) {
            Seat seat = bs.getSeat();
            if (seat.getStatus() == SeatStatus.HELD) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seatRepository.save(seat);
            }
        }

        // 2. Update booking status
        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        // 3. Release Redis locks using stored token
        if (booking.getLockToken() != null) {
            seatLockService.releaseLocks(seatIds, booking.getLockToken());
        }

        log.info("Expired booking ref={}, released {} seat(s)",
                booking.getBookingReference(), seatIds.size());

        bookingEventProducer.publish(BookingEvent.EventType.BOOKING_EXPIRED, booking);
    }
}

