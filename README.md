# NetTrack — Telecom Outage Ticket Tracker

Full-stack outage ticketing: Java Spring Boot API, React dashboard, Python automation, Node.js notifier, PostgreSQL, Docker, Kubernetes, and Jenkins CI/CD.

Detailed diagrams and request flows: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

```
 alarms.csv ──► Python automation script ──► POST /api/tickets
                                                   │
 React + TS dashboard ◄──── REST/JSON ────► Java Spring Boot ticket-service ──► PostgreSQL
                                                   │
                                     Node.js notifier (CRITICAL alerts)

 All of it: Dockerized → docker-compose → Kubernetes manifests → Jenkins pipeline
```

| Part | Folder | Skill |
|---|---|---|
| 1 | `backend/` | Java 21, Spring Boot, REST, JPA, Flyway, PostgreSQL, JUnit/Mockito |
| 2 | `frontend/` | React, Hooks, TypeScript, Context, REST calls, Vitest |
| 3 | `automation/` | Python scripting, ETL, JSON/CSV, pytest |
| 4 | `notifier/` | Node.js / Express |
| 5 | `docker-compose.yml` + Dockerfiles | Docker |
| 6 | `k8s/` | Kubernetes |
| 7 | `Jenkinsfile` | Jenkins CI/CD |

## API (Part 1)

| Method | Path | What |
|---|---|---|
| GET | `/api/tickets?status=OPEN` | list (status optional) |
| GET | `/api/tickets/{id}` | one ticket |
| POST | `/api/tickets` | create — 201 new, 200 if `alarmId` already seen (idempotent) |
| PATCH | `/api/tickets/{id}/status` | change status; resolved can't reopen (409) |
| GET | `/actuator/health` | health check (used by Kubernetes) |
