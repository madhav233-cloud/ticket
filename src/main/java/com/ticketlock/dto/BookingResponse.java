package com.ticketlock.dto;

import com.ticketlock.enums.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BookingResponse {
    private Long id;
    private String bookingReference;
    private BookingStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime holdExpiresAt;
    private Long userId;
    private String userName;
    private Long eventId;
    private String eventTitle;
    private List<BookedSeatInfo> seats;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class BookedSeatInfo {
        private Long seatId;
        private String seatNumber;
        private String rowLabel;
        private BigDecimal price;
    }
}
