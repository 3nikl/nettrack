package com.nettrack.ticket;

import com.nettrack.common.NotFoundException;
import com.nettrack.ticket.TicketDtos.CreateTicketRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

// SERVICE: business rules live here. Controller = HTTP stuff, Repository = DB stuff, Service = the logic.
@Service
public class TicketService {

    private final TicketRepository repo;

    // Constructor injection: Spring passes in the repository for us (Dependency Injection).
    // In tests we pass a Mockito fake instead — that's WHY we inject.
    public TicketService(TicketRepository repo) {
        this.repo = repo;
    }

    public List<Ticket> list(TicketStatus status) {
        return status == null ? repo.findAllByOrderByCreatedAtDesc()
                              : repo.findByStatusOrderByCreatedAtDesc(status);
    }

    public Ticket get(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Ticket " + id + " not found"));
    }

    // IDEMPOTENT create: the alarm system may send the same alarm twice (retries, network blips).
    // If we've seen this alarmId, return the existing ticket instead of making a duplicate.
    // (The UNIQUE constraint in the DB is the final safety net if two requests race.)
    @Transactional
    public CreateResult create(CreateTicketRequest req) {
        if (req.alarmId() != null) {
            var existing = repo.findByAlarmId(req.alarmId());
            if (existing.isPresent()) {
                return new CreateResult(existing.get(), false);
            }
        }
        var ticket = new Ticket(req.alarmId(), req.title(), req.description(), req.region(), req.severity());
        return new CreateResult(repo.save(ticket), true);
    }

    // @Transactional: load + change + save happen as ONE unit — all or nothing.
    @Transactional
    public Ticket updateStatus(Long id, TicketStatus status) {
        var ticket = get(id);
        if (ticket.getStatus() == TicketStatus.RESOLVED && status != TicketStatus.RESOLVED) {
            throw new IllegalStateException("Resolved tickets cannot be reopened");
        }
        ticket.setStatus(status);
        return repo.save(ticket);
    }

    public record CreateResult(Ticket ticket, boolean created) {}
}
