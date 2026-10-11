package com.nettrack.ticket;

import com.nettrack.ticket.TicketDtos.CreateTicketRequest;
import com.nettrack.ticket.TicketDtos.TicketResponse;
import com.nettrack.ticket.TicketDtos.UpdateStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// CONTROLLER: maps HTTP requests -> service calls. Keep it thin; no business logic here.
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    // GET /api/tickets            -> all
    // GET /api/tickets?status=OPEN -> filtered
    @GetMapping
    public List<TicketResponse> list(@RequestParam(required = false) TicketStatus status) {
        return service.list(status).stream().map(TicketResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return TicketResponse.from(service.get(id));
    }

    // 201 Created for a new ticket, 200 OK if it already existed (idempotent replay)
    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest req) {
        var result = service.create(req);
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TicketResponse.from(result.ticket()));
    }

    // PATCH = partial update (only status changes)
    @PatchMapping("/{id}/status")
    public TicketResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return TicketResponse.from(service.updateStatus(id, req.status()));
    }
}
