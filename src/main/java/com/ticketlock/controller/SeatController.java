package com.ticketlock.controller;

import com.ticketlock.dto.ApiResponse;
import com.ticketlock.dto.SeatResponse;
import com.ticketlock.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /** Get all seats for a ticket type */
    @GetMapping("/ticket-type/{ticketTypeId}")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeatsByTicketType(
            @PathVariable Long ticketTypeId) {
        List<SeatResponse> seats = seatService.getSeatsByTicketType(ticketTypeId);
        return ResponseEntity.ok(ApiResponse.ok(seats));
    }

    /** Get only AVAILABLE seats for a ticket type */
    @GetMapping("/ticket-type/{ticketTypeId}/available")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> getAvailableSeats(
            @PathVariable Long ticketTypeId) {
        List<SeatResponse> seats = seatService.getAvailableSeats(ticketTypeId);
        return ResponseEntity.ok(ApiResponse.ok(seats));
    }
}
