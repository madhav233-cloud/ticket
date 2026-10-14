package com.ticketlock.repository;

import com.ticketlock.entity.Event;
import com.ticketlock.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatus(EventStatus status);
    List<Event> findByOrganizerId(Long organizerId);
    List<Event> findByCityIgnoreCaseAndStatus(String city, EventStatus status);
}
