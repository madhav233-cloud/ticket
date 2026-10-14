package com.ticketlock.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TicketTypeResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer totalSeats;
    private Integer availableSeats;
}
