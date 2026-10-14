package com.ticketlock.service;

import com.ticketlock.dto.*;
import com.ticketlock.entity.*;
import com.ticketlock.enums.EventStatus;
import com.ticketlock.enums.SeatStatus;
import com.ticketlock.exception.BadRequestException;
import com.ticketlock.exception.ResourceNotFoundException;
import com.ticketlock.repository.EventRepository;
import com.ticketlock.repository.SeatRepository;
import com.ticketlock.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final SeatRepository seatRepository;
    private final UserService userService;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        User organizer = userService.getUserEntity(request.getOrganizerId());

        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .venue(request.getVenue())
                .city(request.getCity())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(EventStatus.DRAFT)
                .organizer(organizer)
                .build();

        // Create ticket types + seats
        if (request.getTicketTypes() != null) {
            for (CreateTicketTypeRequest ttReq : request.getTicketTypes()) {
                TicketType ticketType = TicketType.builder()
                        .name(ttReq.getName())
                        .description(ttReq.getDescription())
                        .price(ttReq.getPrice())
                        .totalSeats(ttReq.getTotalSeats())
                        .availableSeats(ttReq.getTotalSeats())
                        .event(event)
                        .build();

                // Auto-generate seats
                List<Seat> seats = generateSeats(ticketType, ttReq.getTotalSeats());
                ticketType.setSeats(seats);
                event.addTicketType(ticketType);
            }
        }

        Event saved = eventRepository.save(event);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getPublishedEvents() {
        return eventRepository.findByStatus(EventStatus.PUBLISHED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByCity(String city) {
        return eventRepository.findByCityIgnoreCaseAndStatus(city, EventStatus.PUBLISHED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventResponse publishEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        if (event.getTicketTypes().isEmpty()) {
            throw new BadRequestException("Cannot publish event without ticket types");
        }

        event.setStatus(EventStatus.PUBLISHED);
        return toResponse(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<TicketTypeResponse> getTicketTypesForEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return ticketTypeRepository.findByEventId(eventId)
                .stream()
                .map(this::toTicketTypeResponse)
                .collect(Collectors.toList());
    }

    // --------------- Helpers ---------------

    private List<Seat> generateSeats(TicketType ticketType, int totalSeats) {
        List<Seat> seats = new ArrayList<>();
        int seatsPerRow = 20;
        char row = 'A';

        for (int i = 1; i <= totalSeats; i++) {
            int seatInRow = ((i - 1) % seatsPerRow) + 1;
            if (seatInRow == 1 && i > 1) {
                row++;
            }
            String seatNumber = row + String.valueOf(seatInRow);

            Seat seat = Seat.builder()
                    .seatNumber(seatNumber)
                    .rowLabel(String.valueOf(row))
                    .status(SeatStatus.AVAILABLE)
                    .ticketType(ticketType)
                    .build();
            seats.add(seat);
        }
        return seats;
    }

    private EventResponse toResponse(Event event) {
        List<TicketTypeResponse> ttResponses = event.getTicketTypes().stream()
                .map(this::toTicketTypeResponse)
                .collect(Collectors.toList());

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .venue(event.getVenue())
                .city(event.getCity())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .organizerId(event.getOrganizer().getId())
                .organizerName(event.getOrganizer().getName())
                .ticketTypes(ttResponses)
                .createdAt(event.getCreatedAt())
                .build();
    }

    private TicketTypeResponse toTicketTypeResponse(TicketType tt) {
        return TicketTypeResponse.builder()
                .id(tt.getId())
                .name(tt.getName())
                .description(tt.getDescription())
                .price(tt.getPrice())
                .totalSeats(tt.getTotalSeats())
                .availableSeats(tt.getAvailableSeats())
                .build();
    }
}
