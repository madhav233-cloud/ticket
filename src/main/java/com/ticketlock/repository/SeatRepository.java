package com.ticketlock.repository;

import com.ticketlock.entity.Seat;
import com.ticketlock.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByTicketTypeId(Long ticketTypeId);

    List<Seat> findByTicketTypeIdAndStatus(Long ticketTypeId, SeatStatus status);

    @Query("SELECT s FROM Seat s WHERE s.id IN :seatIds")
    List<Seat> findAllByIdIn(@Param("seatIds") List<Long> seatIds);

    long countByTicketTypeIdAndStatus(Long ticketTypeId, SeatStatus status);
}
