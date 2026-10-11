package com.nettrack.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

// DTOs (Data Transfer Objects) = the JSON shapes of the API.
// We never expose the Entity directly: the API contract and the DB table can change independently.
public final class TicketDtos {
    private TicketDtos() {}

    public record CreateTicketRequest(
            @Size(max = 64) String alarmId,
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotBlank @Size(max = 64) String region,
            @NotNull Severity severity) {}

    public record UpdateStatusRequest(@NotNull TicketStatus status) {}

    public record TicketResponse(
            Long id, String alarmId, String title, String description, String region,
            Severity severity, TicketStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

        static TicketResponse from(Ticket t) {
            return new TicketResponse(t.getId(), t.getAlarmId(), t.getTitle(), t.getDescription(),
                    t.getRegion(), t.getSeverity(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
