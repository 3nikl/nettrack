package com.nettrack.ticket;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

// ENTITY: a Java class that maps 1:1 to a database table row.
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB generates the id (BIGSERIAL)
    private Long id;

    @Column(name = "alarm_id", unique = true)
    private String alarmId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String region;

    @Enumerated(EnumType.STRING) // store "HIGH" not 2 — survives reordering the enum
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // JPA lifecycle hooks: run automatically before INSERT / UPDATE
    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    protected Ticket() {} // JPA needs a no-arg constructor

    public Ticket(String alarmId, String title, String description, String region, Severity severity) {
        this.alarmId = alarmId;
        this.title = title;
        this.description = description;
        this.region = region;
        this.severity = severity;
    }

    public Long getId() { return id; }
    public String getAlarmId() { return alarmId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRegion() { return region; }
    public Severity getSeverity() { return severity; }
    public TicketStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(TicketStatus status) { this.status = status; }
}
