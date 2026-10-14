package com.ticketlock.dto;

import com.ticketlock.enums.SeatStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeatResponse {
    private Long id;
    private String seatNumber;
    private String rowLabel;
    private SeatStatus status;
}
