package com.ticketlock.entity;

import com.ticketlock.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_user", columnList = "user_id"),
        @Index(name = "idx_booking_status", columnList = "status"),
        @Index(name = "idx_booking_idempotency", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String bookingReference;

    /**
     * Used to make booking requests idempotent.
     * Client should send the same key on retries.
     */
    @Column(nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    /**
     * Redis lock token used when seats were held.
     * Needed for safe lock release on confirm / cancel / expiry.
     */
    private String lockToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.HELD;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** When the temporary hold expires */
    private LocalDateTime holdExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (bookingReference == null) {
            bookingReference = "TKL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
