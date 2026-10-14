package com.ticketlock.controller;

import com.ticketlock.dto.*;
import com.ticketlock.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse event = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Event created successfully", event));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable Long id) {
        EventResponse event = eventService.getEventById(id);
        return ResponseEntity.ok(ApiResponse.ok(event));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getPublishedEvents() {
        List<EventResponse> events = eventService.getPublishedEvents();
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    @GetMapping("/city/{city}")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEventsByCity(
            @PathVariable String city) {
        List<EventResponse> events = eventService.getEventsByCity(city);
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<EventResponse>> publishEvent(@PathVariable Long id) {
        EventResponse event = eventService.publishEvent(id);
        return ResponseEntity.ok(ApiResponse.ok("Event published successfully", event));
    }

    @GetMapping("/{id}/ticket-types")
    public ResponseEntity<ApiResponse<List<TicketTypeResponse>>> getTicketTypes(
            @PathVariable Long id) {
        List<TicketTypeResponse> ticketTypes = eventService.getTicketTypesForEvent(id);
        return ResponseEntity.ok(ApiResponse.ok(ticketTypes));
    }
}
