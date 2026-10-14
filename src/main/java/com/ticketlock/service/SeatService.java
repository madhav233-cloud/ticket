package com.ticketlock.service;

import com.ticketlock.dto.SeatResponse;
import com.ticketlock.entity.Seat;
import com.ticketlock.enums.SeatStatus;
import com.ticketlock.exception.ResourceNotFoundException;
import com.ticketlock.repository.SeatRepository;
import com.ticketlock.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final TicketTypeRepository ticketTypeRepository;

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsByTicketType(Long ticketTypeId) {
        if (!ticketTypeRepository.existsById(ticketTypeId)) {
            throw new ResourceNotFoundException("Ticket type not found with id: " + ticketTypeId);
        }

        return seatRepository.findByTicketTypeId(ticketTypeId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getAvailableSeats(Long ticketTypeId) {
        if (!ticketTypeRepository.existsById(ticketTypeId)) {
            throw new ResourceNotFoundException("Ticket type not found with id: " + ticketTypeId);
        }

        return seatRepository.findByTicketTypeIdAndStatus(ticketTypeId, SeatStatus.AVAILABLE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Seat> getSeatsByIds(List<Long> seatIds) {
        List<Seat> seats = seatRepository.findAllByIdIn(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("One or more seats not found");
        }
        return seats;
    }

    private SeatResponse toResponse(Seat seat) {
        return SeatResponse.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .rowLabel(seat.getRowLabel())
                .status(seat.getStatus())
                .build();
    }
}
