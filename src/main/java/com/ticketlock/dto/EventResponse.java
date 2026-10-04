package com.ticketlock.dto;

import com.ticketlock.enums.EventStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String venue;
    private String city;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private EventStatus status;
    private Long organizerId;
    private String organizerName;
    private List<TicketTypeResponse> ticketTypes;
    private LocalDateTime createdAt;
}
