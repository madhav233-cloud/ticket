package com.ticketlock.service;

import com.ticketlock.dto.BookingResponse;
import com.ticketlock.dto.CreateBookingRequest;
import com.ticketlock.entity.*;
import com.ticketlock.enums.BookingStatus;
import com.ticketlock.enums.EventStatus;
import com.ticketlock.enums.SeatStatus;
import com.ticketlock.exception.BadRequestException;
import com.ticketlock.exception.ResourceNotFoundException;
import com.ticketlock.exception.SeatUnavailableException;
import com.ticketlock.repository.BookingRepository;
import com.ticketlock.repository.EventRepository;
import com.ticketlock.event.BookingEvent;
import com.ticketlock.kafka.BookingEventProducer;
import com.ticketlock.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserService userService;
    private final SeatService seatService;
    private final SeatLockService seatLockService;
    private final BookingEventProducer bookingEventProducer;

    @Value("${ticketlock.seat-hold-duration-minutes:8}")
    private int holdDurationMinutes;

    @Value("${ticketlock.max-seats-per-booking:10}")
    private int maxSeatsPerBooking;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        Optional<Booking> existing = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent hit for key={}", request.getIdempotencyKey());
            return toResponse(existing.get());
        }

        if (request.getSeatIds().size() > maxSeatsPerBooking) {
            throw new BadRequestException("Cannot book more than " + maxSeatsPerBooking + " seats at once");
        }

        User user = userService.getUserEntity(request.getUserId());
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.getEventId()));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BadRequestException("Event is not open for booking");
        }

        List<Seat> seats = seatService.getSeatsByIds(request.getSeatIds());

        for (Seat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException(
                        "Seat " + seat.getSeatNumber() + " is not available (status=" + seat.getStatus() + ")");
            }
            if (!seat.getTicketType().getEvent().getId().equals(event.getId())) {
                throw new BadRequestException("Seat " + seat.getSeatNumber() + " does not belong to this event");
            }
        }

        List<Long> seatIds = seats.stream().map(Seat::getId).collect(Collectors.toList());
        String lockToken = seatLockService.tryLockSeats(seatIds);

        if (lockToken == null) {
            throw new SeatUnavailableException(
                    "One or more seats are currently being booked by another user. Please try different seats.");
        }

        try {
            for (Seat seat : seats) {
                Seat managed = seatRepository.findById(seat.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seat.getId()));
                if (managed.getStatus() != SeatStatus.AVAILABLE) {
                    throw new SeatUnavailableException("Seat " + managed.getSeatNumber() + " is no longer available");
                }
                managed.setStatus(SeatStatus.HELD);
                seatRepository.save(managed);
            }

            BigDecimal totalAmount = seats.stream()
                    .map(s -> s.getTicketType().getPrice())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(holdDurationMinutes);

            Booking booking = Booking.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .lockToken(lockToken)
                    .status(BookingStatus.HELD)
                    .totalAmount(totalAmount)
                    .holdExpiresAt(holdExpiresAt)
                    .user(user)
                    .event(event)
                    .build();

            List<BookingSeat> bookingSeats = new ArrayList<>();
            for (Seat seat : seats) {
                BookingSeat bs = BookingSeat.builder()
                        .booking(booking)
                        .seat(seat)
                        .price(seat.getTicketType().getPrice())
                        .build();
                bookingSeats.add(bs);
            }
            booking.setBookingSeats(bookingSeats);

            seats.stream()
                    .map(Seat::getTicketType)
                    .distinct()
                    .forEach(tt -> {
                        long unavailable = seatRepository.countByTicketTypeIdAndStatus(tt.getId(), SeatStatus.HELD)
                                + seatRepository.countByTicketTypeIdAndStatus(tt.getId(), SeatStatus.BOOKED);
                        tt.setAvailableSeats(tt.getTotalSeats() - (int) unavailable);
                    });

            Booking saved = bookingRepository.save(booking);
            log.info("Booking created: ref={}, seats={}, lockToken={}",
                    saved.getBookingReference(), seatIds.size(), lockToken);

            bookingEventProducer.publish(BookingEvent.EventType.BOOKING_CREATED, saved);
            return toResponse(saved);

        } catch (Exception e) {
            seatLockService.releaseLocks(seatIds, lockToken);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingByReference(String reference) {
        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + reference));
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponse confirmBooking(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingReference));

        if (booking.getStatus() != BookingStatus.HELD && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Booking cannot be confirmed from status: " + booking.getStatus());
        }

        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Booking hold has expired");
        }

        List<Long> seatIds = new ArrayList<>();
        for (BookingSeat bs : booking.getBookingSeats()) {
            Seat seat = bs.getSeat();
            seat.setStatus(SeatStatus.BOOKED);
            seatRepository.save(seat);
            seatIds.add(seat.getId());
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);

        if (booking.getLockToken() != null) {
            seatLockService.releaseLocks(seatIds, booking.getLockToken());
        }

        log.info("Booking confirmed: ref={}", saved.getBookingReference());
        bookingEventProducer.publish(BookingEvent.EventType.BOOKING_CONFIRMED, saved);
        return toResponse(saved);
    }

    @Transactional
    public BookingResponse cancelBooking(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingReference));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BadRequestException("Cannot cancel a confirmed booking");
        }
        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking is already " + booking.getStatus());
        }

        List<Long> seatIds = new ArrayList<>();
        for (BookingSeat bs : booking.getBookingSeats()) {
            Seat seat = bs.getSeat();
            seat.setStatus(SeatStatus.AVAILABLE);
            seatRepository.save(seat);
            seatIds.add(seat.getId());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);

        if (booking.getLockToken() != null) {
            seatLockService.releaseLocks(seatIds, booking.getLockToken());
        }

        log.info("Booking cancelled: ref={}", saved.getBookingReference());
        bookingEventProducer.publish(BookingEvent.EventType.BOOKING_CANCELLED, saved);
        return toResponse(saved);
    }

    private BookingResponse toResponse(Booking booking) {
        List<BookingResponse.BookedSeatInfo> seatInfos = booking.getBookingSeats().stream()
                .map(bs -> BookingResponse.BookedSeatInfo.builder()
                        .seatId(bs.getSeat().getId())
                        .seatNumber(bs.getSeat().getSeatNumber())
                        .rowLabel(bs.getSeat().getRowLabel())
                        .price(bs.getPrice())
                        .build())
                .collect(Collectors.toList());

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .holdExpiresAt(booking.getHoldExpiresAt())
                .userId(booking.getUser().getId())
                .userName(booking.getUser().getName())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .seats(seatInfos)
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
