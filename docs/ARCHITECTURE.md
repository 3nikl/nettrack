# NetTrack Architecture

NetTrack turns network alarms into outage tickets that engineers track and resolve on a dashboard.

- **Status legend:** ✅ built · 🔜 planned (part number in brackets)

---

## 1. System overview

```mermaid
flowchart LR
    subgraph Users
        ENG["Network engineer<br/>(browser)"]
    end

    subgraph Frontend
        UI["React + TypeScript dashboard 🔜 [2]"]
    end

    subgraph Backend
        API["ticket-service<br/>Java 23 · Spring Boot ✅ [1]"]
        NOTIFY["notifier<br/>Node.js · Express 🔜 [4]"]
    end

    subgraph Data
        DB[("PostgreSQL ✅ [1]")]
    end

    subgraph Automation
        FEED["Network alarm feed<br/>alarms.csv"]
        PY["Python alarm importer 🔜 [3]"]
    end

    ENG -->|clicks| UI
    UI <-->|"REST · JSON"| API
    API <-->|"SQL via JPA"| DB
    FEED --> PY
    PY -->|"POST /api/tickets"| API
    API -->|"CRITICAL ticket"| NOTIFY
    NOTIFY -->|alert| ONCALL["On-call engineer"]
```

Every box above runs as a **Docker container** 🔜 [5], managed by **Kubernetes** 🔜 [6], and shipped by a **Jenkins** pipeline 🔜 [7].

| Component | Language / tool | Job | Port |
|---|---|---|---|
| React dashboard | TypeScript, React, Vite | Shows tickets, lets engineers change status | 5173 |
| ticket-service | Java 23, Spring Boot 3.3, Maven | Owns tickets: rules, validation, storage | 8080 |
| PostgreSQL | SQL, Flyway migrations | Stores tickets permanently | 5432 |
| Alarm importer | Python 3.11 | Reads alarm files, creates tickets (ETL) | — (script) |
| notifier | Node.js, Express | Sends alerts for CRITICAL tickets | 3001 |

---

## 2. Inside ticket-service (the layers)

```mermaid
flowchart TB
    REQ["HTTP request<br/>PATCH /api/tickets/1/status"] --> C
    C["TicketController<br/>routes URL → method, validates JSON (@Valid)"] --> S
    S["TicketService<br/>business rules, @Transactional"] --> R
    R["TicketRepository<br/>Spring Data JPA writes the SQL"] --> H
    H["Hibernate<br/>maps Ticket.java ↔ tickets table"] --> DB[("PostgreSQL")]
    C -. "exception thrown" .-> X["GlobalExceptionHandler<br/>→ 400 / 404 / 409 JSON"]
    S -. "exception thrown" .-> X
```

| Layer | File | Rule of thumb |
|---|---|---|
| Controller | `TicketController.java` | HTTP only: URLs, status codes, DTOs. No business logic. |
| DTOs | `TicketDtos.java` | The JSON contract. Validation annotations live here. |
| Service | `TicketService.java` | All business rules. Easy to unit test with a mocked repository. |
| Repository | `TicketRepository.java` | Database access. Method names become SQL. |
| Entity | `Ticket.java` | One object = one row in `tickets`. |
| Errors | `GlobalExceptionHandler.java` | Turns exceptions into clean HTTP errors. |
| Config | `application.yml`, `WebConfig.java` | Env-var driven settings, CORS. |
| Schema | `db/migration/V1__create_tickets.sql` | Flyway runs it once, on startup. |

---

## 3. Flow: engineer resolves a ticket (full stack)

```mermaid
sequenceDiagram
    actor E as Engineer
    participant UI as React dashboard
    participant C as TicketController
    participant S as TicketService
    participant R as TicketRepository
    participant DB as PostgreSQL

    E->>UI: clicks "Resolve" on ticket #1
    UI->>C: PATCH /api/tickets/1/status {"status":"RESOLVED"}
    C->>C: @Valid checks body
    C->>S: updateStatus(1, RESOLVED)
    S->>R: findById(1)
    R->>DB: SELECT * FROM tickets WHERE id = 1
    DB-->>R: row
    S->>S: rule: resolved tickets can't reopen
    S->>R: save(ticket)
    R->>DB: UPDATE tickets SET status='RESOLVED' ... WHERE id = 1
    DB-->>R: OK (transaction commits)
    S-->>C: updated Ticket
    C-->>UI: 200 OK + TicketResponse JSON
    UI->>UI: setState → row re-renders as RESOLVED
```

---

## 4. Flow: network alarm becomes a ticket (automation + idempotency)

```mermaid
sequenceDiagram
    participant F as alarms.csv
    participant P as Python importer
    participant API as ticket-service
    participant DB as PostgreSQL
    participant N as notifier

    P->>F: read rows (Extract)
    P->>P: clean + map fields (Transform)
    loop each alarm
        P->>API: POST /api/tickets {alarmId, title, region, severity} (Load)
        API->>DB: SELECT ... WHERE alarm_id = ?
        alt alarm not seen before
            API->>DB: INSERT ticket
            API-->>P: 201 Created
            opt severity = CRITICAL
                API->>N: POST /notify
            end
        else alarm already imported
            API-->>P: 200 OK (existing ticket, no duplicate)
        end
    end
    P->>P: print summary (created / duplicates / failed)
```

**Why idempotency:** the importer may crash halfway and be re-run, or retry after a timeout. Re-sending an alarm must never create a second ticket. The service checks `alarm_id` first, and the database `UNIQUE` constraint is the final safety net.

---

## 5. Data model

```mermaid
erDiagram
    TICKETS {
        BIGSERIAL id PK
        VARCHAR alarm_id UK "from the alarm system; nullable"
        VARCHAR title "required"
        TEXT description
        VARCHAR region "e.g. NJ-MIDDLETOWN"
        VARCHAR severity "LOW | MEDIUM | HIGH | CRITICAL"
        VARCHAR status "OPEN | IN_PROGRESS | RESOLVED"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
```

Index `idx_tickets_status_created (status, created_at DESC)` matches the dashboard's main query: open tickets, newest first.

### Ticket lifecycle

```mermaid
stateDiagram-v2
    [*] --> OPEN: created
    OPEN --> IN_PROGRESS: engineer picks it up
    IN_PROGRESS --> RESOLVED: fixed
    OPEN --> RESOLVED: fixed quickly
    RESOLVED --> [*]
    note right of RESOLVED: cannot be reopened (409 Conflict)
```

---

## 6. CI/CD pipeline (Jenkins) 🔜 [7]

```mermaid
flowchart LR
    A["git push / Pull Request"] --> B["Jenkins: Checkout"]
    B --> C["Test<br/>mvnw test · npm test · pytest"]
    C -->|any test fails| STOP["❌ Pipeline stops<br/>PR blocked"]
    C -->|all pass| D["Build Docker images<br/>tagged with git SHA"]
    D --> E["Push to image registry"]
    E --> F["Deploy to Kubernetes<br/>rolling update"]
    F --> G["Health check /actuator/health"]
    G -->|unhealthy| RB["Rollback<br/>kubectl rollout undo"]
```

---

## 7. Deployment on Kubernetes 🔜 [6]

```mermaid
flowchart TB
    U["Browser"] --> ING["Ingress<br/>routes / → frontend, /api → backend"]
    ING --> FE["Service: frontend"] --> FEP["Pods: React (nginx) ×2"]
    ING --> BE["Service: ticket-service"] --> BEP["Pods: Spring Boot ×3"]
    BEP --> DBS["Service: postgres"] --> DBP["Pod: PostgreSQL + volume"]
    BEP --> NS["Service: notifier"] --> NP["Pod: Node.js ×1"]
    CM["ConfigMap: DB_URL, CORS"] -.-> BEP
    SEC["Secret: DB_PASSWORD"] -.-> BEP
```

- **Deployment** keeps the requested number of pods running and replaces crashed ones.
- **Service** gives pods one stable name (`ticket-service:8080`) and load-balances between them.
- **Probes** call `/actuator/health`; a pod gets traffic only when healthy.

---

## 8. Running locally (Part 1)

```bash
# Postgres (one time)
psql postgres -c "CREATE ROLE nettrack LOGIN PASSWORD 'nettrack';"
psql postgres -c "CREATE DATABASE nettrack OWNER nettrack;"

# Backend
cd backend
./mvnw test             # run tests
./mvnw spring-boot:run  # start API on :8080

# Try it
curl -X POST localhost:8080/api/tickets -H 'Content-Type: application/json' \
  -d '{"alarmId":"ALM-1001","title":"Fiber cut","region":"NJ-MIDDLETOWN","severity":"CRITICAL"}'
curl localhost:8080/api/tickets
```
