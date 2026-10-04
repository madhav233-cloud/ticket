package com.ticketlock.controller;

import com.ticketlock.dto.ApiResponse;
import com.ticketlock.dto.BookingResponse;
import com.ticketlock.dto.CreateBookingRequest;
import com.ticketlock.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * Create a new booking (holds seats temporarily).
     * Requires an Idempotency-Key style field in the body.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Booking created successfully. Complete payment before hold expires.", booking));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingById(@PathVariable Long id) {
        BookingResponse booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    @GetMapping("/reference/{reference}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingByReference(
            @PathVariable String reference) {
        BookingResponse booking = bookingService.getBookingByReference(reference);
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getBookingsByUser(
            @PathVariable Long userId) {
        List<BookingResponse> bookings = bookingService.getBookingsByUser(userId);
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    /** Confirm booking after payment */
    @PostMapping("/{reference}/confirm")
    public ResponseEntity<ApiResponse<BookingResponse>> confirmBooking(
            @PathVariable String reference) {
        BookingResponse booking = bookingService.confirmBooking(reference);
        return ResponseEntity.ok(ApiResponse.ok("Booking confirmed successfully", booking));
    }

    /** Cancel a held booking and release seats */
    @PostMapping("/{reference}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable String reference) {
        BookingResponse booking = bookingService.cancelBooking(reference);
        return ResponseEntity.ok(ApiResponse.ok("Booking cancelled, seats released", booking));
    }
}
