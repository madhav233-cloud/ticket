# TicketLock

**High-Concurrency Event Ticketing Platform**

A production-style backend system that safely sells event tickets under heavy concurrent load **without overselling seats**.

Built with Java 21, Spring Boot 3, Redis, and Kafka.

---

## Why This Project Matters

Most ticket booking demos only implement CRUD.  
TicketLock focuses on the hard problem:

> How do you let thousands of users try to book the same seats at the same time — and still guarantee that no seat is sold twice?

---

## Features

| Feature                         | Implementation |
|---------------------------------|----------------|
| Temporary seat holds            | 8-minute TTL holds |
| Distributed locking             | Redis `SET NX EX` |
| Database concurrency control    | JPA `@Version` optimistic locking |
| Idempotent booking API          | Client-supplied idempotency key |
| Automatic hold expiry           | Scheduler every 30 seconds |
| Event-driven side effects       | Kafka (`booking-events`) |
| All-or-nothing multi-seat lock  | Rollback on partial failure |
| Safe lock release               | Lua script + stored lock token |

---

## Tech Stack

- **Java 21** + **Spring Boot 3.3**
- **Spring Data JPA** + H2 / PostgreSQL
- **Redis 7** – distributed locks
- **Apache Kafka** – booking lifecycle events
- **Docker Compose** – local infrastructure
- **Maven**

---

## Architecture

```
                    ┌──────────────┐
                    │    Client    │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  REST APIs   │
                    └──────┬───────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
   Idempotency       Redis Lock        DB (@Version)
   Check             (SET NX EX)       Optimistic Lock
          │                │                │
          └────────────────┼────────────────┘
                           │
                    ┌──────▼───────┐
                    │   Booking    │
                    │  State Machine│
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │    Kafka     │
                    │ booking-events│
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ Notification │
                    │   Consumer   │
                    └──────────────┘
```

### Booking State Machine

```
AVAILABLE → HELD → CONFIRMED
               ↘
                EXPIRED / CANCELLED → AVAILABLE
```

---

## Quick Start

### 1. Start infrastructure
```bash
docker compose up -d
```
This starts:
- Redis (port 6379)
- Zookeeper (port 2181)
- Kafka (port 9092)

### 2. Run the application
```bash
mvn spring-boot:run
```

App runs on **http://localhost:8080**

### 3. (Optional) Use PostgreSQL
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

---

## API Overview

### Users
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/users` | Create user |
| GET | `/api/users/{id}` | Get user |

### Events
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/events` | Create event + ticket types + seats |
| GET | `/api/events` | List published events |
| GET | `/api/events/{id}` | Get event |
| POST | `/api/events/{id}/publish` | Publish event |
| GET | `/api/events/{id}/ticket-types` | List ticket types |

### Seats
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/seats/ticket-type/{id}` | All seats |
| GET | `/api/seats/ticket-type/{id}/available` | Available seats only |

### Bookings
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/bookings` | Hold seats (create booking) |
| GET | `/api/bookings/{id}` | Get by ID |
| GET | `/api/bookings/reference/{ref}` | Get by reference |
| GET | `/api/bookings/user/{userId}` | User bookings |
| POST | `/api/bookings/{ref}/confirm` | Confirm after payment |
| POST | `/api/bookings/{ref}/cancel` | Cancel & release seats |

---

## End-to-End Example

```bash
# 1. Create user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Rahul Sharma","email":"rahul@example.com"}'

# 2. Create event
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Coldplay Concert 2026",
    "venue": "DY Patil Stadium",
    "city": "Mumbai",
    "startTime": "2026-12-15T19:00:00",
    "organizerId": 1,
    "ticketTypes": [
      {"name": "Silver", "price": 2500.00, "totalSeats": 40},
      {"name": "Gold", "price": 5000.00, "totalSeats": 20},
      {"name": "VIP", "price": 12000.00, "totalSeats": 10}
    ]
  }'

# 3. Publish event
curl -X POST http://localhost:8080/api/events/1/publish

# 4. View available seats
curl http://localhost:8080/api/seats/ticket-type/1/available

# 5. Book seats
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "eventId": 1,
    "seatIds": [1, 2, 3],
    "idempotencyKey": "booking-001"
  }'

# 6. Confirm booking
curl -X POST http://localhost:8080/api/bookings/TKL-XXXXXXXX/confirm
```

---

## Concurrency Test

A simple script is included to prove the system does not oversell:

```bash
./load-test.sh
```

It fires multiple concurrent booking requests for the **same seat**.  
Expected result:
- **Exactly 1** request succeeds (`201`)
- All others receive `409 Conflict`

This is the core demonstration of the project’s value.

---

## Kafka Events

| Event | When |
|-------|------|
| `BOOKING_CREATED` | Seats successfully held |
| `BOOKING_CONFIRMED` | Payment confirmed |
| `BOOKING_CANCELLED` | User cancelled |
| `BOOKING_EXPIRED` | Hold timed out (scheduler) |

Topic: `booking-events`

---

## Project Structure

```
com.ticketlock
├── config/          Redis, Kafka, Scheduling
├── controller/      REST endpoints
├── dto/             Request / Response objects
├── entity/          JPA entities
├── enums/           Status enums
├── event/           Kafka event model
├── exception/       Global error handling
├── kafka/           Producer + Consumer
├── repository/      Spring Data repositories
└── service/         Business logic + locking + expiry
```

---

## Design Highlights (Interview Talking Points)

1. **Dual-layer concurrency control**  
   Redis distributed lock (cross-instance) + DB optimistic locking (data integrity).

2. **All-or-nothing multi-seat booking**  
   If any seat cannot be locked, all acquired locks are released.

3. **Idempotency**  
   Safe retries without duplicate bookings.

4. **Automatic resource cleanup**  
   Expired holds are released by a scheduled job; Redis TTL acts as a safety net.

5. **Event-driven side effects**  
   Notifications are decoupled via Kafka so the booking path stays fast and reliable.

---

## Resume Bullets

- Built a high-concurrency event ticketing platform that prevents overselling under concurrent load using Redis distributed locks and database optimistic locking.
- Implemented temporary seat holds with automatic expiry and idempotent booking APIs for reliable reservation handling.
- Designed an event-driven booking lifecycle with Kafka for asynchronous notifications on create, confirm, cancel, and expiry events.
- Achieved safe multi-seat booking with all-or-nothing lock acquisition and zero double bookings in concurrent load tests.

---

## License

MIT
