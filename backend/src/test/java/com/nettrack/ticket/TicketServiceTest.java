package com.nettrack.ticket;

import com.nettrack.common.NotFoundException;
import com.nettrack.ticket.TicketDtos.CreateTicketRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// UNIT TEST: tests TicketService alone. The repository is a Mockito MOCK (a fake),
// so no database is needed and the test runs in milliseconds.
class TicketServiceTest {

    private TicketRepository repo;
    private TicketService service;

    @BeforeEach
    void setUp() {
        repo = mock(TicketRepository.class);
        service = new TicketService(repo);
    }

    @Test
    void createsNewTicketWhenAlarmIsNew() {
        var req = new CreateTicketRequest("ALM-1", "Fiber cut", null, "NJ-MIDDLETOWN", Severity.CRITICAL);
        when(repo.findByAlarmId("ALM-1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0)); // "save returns what you gave it"

        var result = service.create(req);

        assertThat(result.created()).isTrue();
        assertThat(result.ticket().getTitle()).isEqualTo("Fiber cut");
        verify(repo).save(any());
    }

    @Test
    void duplicateAlarmReturnsExistingTicketAndDoesNotSave() {
        var existing = new Ticket("ALM-1", "Fiber cut", null, "NJ-MIDDLETOWN", Severity.CRITICAL);
        when(repo.findByAlarmId("ALM-1")).thenReturn(Optional.of(existing));

        var result = service.create(
                new CreateTicketRequest("ALM-1", "Fiber cut", null, "NJ-MIDDLETOWN", Severity.CRITICAL));

        assertThat(result.created()).isFalse();
        assertThat(result.ticket()).isSameAs(existing);
        verify(repo, never()).save(any()); // the idempotency guarantee
    }

    @Test
    void cannotReopenResolvedTicket() {
        var t = new Ticket(null, "Outage", null, "NJ", Severity.LOW);
        t.setStatus(TicketStatus.RESOLVED);
        when(repo.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.updateStatus(1L, TicketStatus.OPEN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingTicketThrowsNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }
}
