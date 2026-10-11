package com.nettrack.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

// REPOSITORY: Spring writes the SQL for you from the method NAME.
// findByStatusOrderByCreatedAtDesc -> SELECT * FROM tickets WHERE status=? ORDER BY created_at DESC
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);
    List<Ticket> findAllByOrderByCreatedAtDesc();
    Optional<Ticket> findByAlarmId(String alarmId);
}
