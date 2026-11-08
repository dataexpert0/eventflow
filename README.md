# EventFlow

EventFlow is an event-driven order management system built with Java 25, Spring Boot 4, PostgreSQL, and RabbitMQ.

The project follows a multi-module microservices architecture, emphasizing domain-driven design, transactional consistency, asynchronous messaging, idempotent event processing, and automated integration testing.

The **Order Service** manages the order lifecycle and reliably publishes domain events using the Transactional Outbox pattern. The **Notification Service** consumes these events, persists notifications, and exposes a paginated REST API for querying them.

> **Project status:** v0.3.0 — Released | v0.4.0 — In Development  
> **Current focus:** Event-driven communication, reliable message processing, and notification management.


## Tech Stack

- Java 25
- Spring Boot 4.1.2
- Maven (multi-module)
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring JDBC
- Spring AMQP
- RabbitMQ 4
- PostgreSQL 17
- Flyway
- Jackson 3
- Jakarta Bean Validation
- springdoc-openapi / Swagger UI
- Docker Compose
- JUnit 5, Mockito, and MockMvc
- Testcontainers (PostgreSQL and RabbitMQ)
- GitHub Actions

## Project Structure

```text
eventflow/
├── .github/
│   └── workflows/
│       └── ci.yml
├── infrastructure/
│   └── rabbitmq/
│       └── configure-dlx.ps1
├── services/
│   ├── order-service/
│   │   ├── pom.xml
│   │   └── src/
│   └── notification-service/
│       ├── pom.xml
│       └── src/
├── compose.yaml
├── .env.example
├── pom.xml
└── README.md
```

## Architecture

EventFlow follows a layered, ports-and-adapters architecture with explicit separation between domain logic, application use cases, and infrastructure components.

Each microservice owns its persistence layer and communicates asynchronously through RabbitMQ.

```text
                    HTTP Client
                         |
                         v
                   Order Service
                         |
                  Application Layer
                         |
                 PostgreSQL Database
                  |             |
                Orders    Outbox Events
                                |
                                v
                         Outbox Worker
                                |
                                v
                          RabbitMQ
                                |
                                v
                      Notification Service
                                |
                         Event Consumer
                                |
                       Idempotent Processing
                                |
                                v
                         PostgreSQL
                          |          |
                  Processed Events  Notifications
                                       |
                                       v
                                Notification API
```

### Transactional Outbox

Order lifecycle operations persist business state changes and corresponding domain events within the same database transaction.

An asynchronous worker claims pending outbox events and publishes them to RabbitMQ using publisher confirmations.

The delivery model is **at-least-once**, meaning duplicate messages are possible.

### Idempotent Event Consumption

The Notification Service uses a `processed_events` table with a unique event identifier to prevent duplicate local processing.

Processed event registration and notification persistence occur within the same database transaction.

If notification persistence fails, the transaction is rolled back, allowing the event to be processed again.

### Failure Handling

Messages rejected by the Notification Service without requeueing are routed to a RabbitMQ Dead Letter Queue (DLQ).

The DLQ retains failed messages for inspection and recovery. Automatic retry scheduling is not yet implemented.

## API Documentation

The Order Service exposes a REST API documented with OpenAPI 3
and Swagger UI.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders` | Create a pending order |
| GET | `/api/v1/orders/{id}` | Retrieve an order by UUID |
| GET | `/api/v1/health` | Service health check |

When the service is running locally:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### Example request

```http
POST /api/v1/orders
Content-Type: application/json
```

```json
{
  "customerId": "d81b2d11-73f3-47b5-8764-81c2fc7d8b3a",
  "items": [
    {
      "productId": "54c64233-3f4b-47ee-a1f3-37ce9020ab10",
      "quantity": 2,
      "unitPrice": 49.90
    }
  ]
}
```


Successful creation returns HTTP `201 Created`, including the
generated order UUID and a `Location` header.

Invalid requests return HTTP `400 Bad Request`


## Order Lifecycle

EventFlow implements a state-based order lifecycle with
domain-level business rules and transactional persistence.

### State Transitions

```text
               ┌─────────┐
               │ PENDING │
               └────┬────┘
                    │
           ┌────────┴────────┐
           │                 │
       confirm()          cancel()
           │                 │
           ▼                 ▼
     ┌───────────┐     ┌───────────┐
     │ CONFIRMED │     │ CANCELLED │
     └───────────┘     └───────────┘
```

- Orders are initially created with PENDING status.
- Only pending orders can be confirmed or cancelled.
- Confirmation requires at least one order item.
- Confirmed and cancelled orders cannot transition
  to another state.

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders` | Create a new order |
| GET | `/api/v1/orders/{id}` | Retrieve an order |
| POST | `/api/v1/orders/{id}/confirm` | Confirm a pending order |
| POST | `/api/v1/orders/{id}/cancel` | Cancel a pending order |

### HTTP Responses

| Status | Description |
|--------|-------------|
| 200 | Operation completed successfully |
| 201 | Order created |
| 400 | Invalid request or identifier |
| 404 | Order not found |
| 409 | Invalid state transition or version conflict |

Error responses use **RFC 9457 Problem Details**.

## Concurrency Control

EventFlow implements optimistic concurrency control using
Hibernate's `@Version` mechanism.

Each persisted order maintains a version number that is
incremented when the order entity is updated.

The application also validates the version carried by
the domain aggregate before applying changes.

This provides two complementary safeguards:

1. **Stale aggregate detection:** rejects updates based
   on an outdated order version.
2. **Database-level optimistic locking:** detects
   concurrent modifications during persistence.

When a version conflict is detected, the API returns
HTTP `409 Conflict`.

### Database Migrations

Each microservice maintains its own database schema, managed independently through Flyway.

**Order Service — `eventflow_orders`**

- `V1__create_orders_tables.sql` — Initial order schema.
- `V2__add_order_version.sql` — Optimistic locking support.
- `V3__create_outbox_events.sql` — Transactional Outbox persistence.
- `V4__add_outbox_claim_columns.sql` — Worker claims and lease management.

**Notification Service — `eventflow_notifications`**

- `V1__create_processed_events.sql` — Idempotent event processing.
- `V2__create_notifications.sql` — Notification persistence and event association.

Existing migrations are preserved to maintain schema history integrity. Each service applies its own migrations during application startup.

### Notification Service API

The Notification Service exposes a paginated REST endpoint for retrieving notifications associated with an order.

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/notifications` | Retrieve paginated notifications by order ID |

**Example request:**

```http
GET /api/v1/notifications?orderId={uuid}&page=0&size=20
```

**Query parameters:**

| Parameter | Description | Default |
|---|---|---|
| `orderId` | Required order UUID | — |
| `page` | Zero-based page index | `0` |
| `size` | Number of results per page (1–100) | `20` |

Results are sorted by creation timestamp in descending order, with the notification ID used as a secondary sorting key.

The response includes `content`, `page`, `size`, `totalElements`, and `totalPages`.

The Notification Service runs locally on port `8081`.

**Security note:** Authentication and authorization are not yet implemented. The endpoint is intended for local development and testing.

## Running Locally

### Requirements

- JDK 25
- Apache Maven
- Docker Desktop or a compatible Docker Engine

### Local Infrastructure Setup

Create a `.env` file from `.env.example` and configure the local PostgreSQL and RabbitMQ credentials.

Start the infrastructure from the project root:

```powershell
docker compose up -d
```

The development environment uses two PostgreSQL databases:

- `eventflow_orders` — Order Service persistence and transactional outbox.
- `eventflow_notifications` — Processed events and notifications.

Ensure both databases exist before starting the applications. Each service applies its own Flyway migrations automatically.

### Start the Order Service

In a PowerShell terminal, load the required credentials:

```powershell
$env:POSTGRES_PASSWORD = (Get-Content .env |
    Where-Object { $_ -match '^POSTGRES_PASSWORD=' }) `
    -replace '^POSTGRES_PASSWORD=', ''

$env:RABBITMQ_PASSWORD = (Get-Content .env |
    Where-Object { $_ -match '^RABBITMQ_PASSWORD=' }) `
    -replace '^RABBITMQ_PASSWORD=', ''
```

Enable the Transactional Outbox worker:

```powershell
$env:OUTBOX_ENABLED = "true"
```

Start the service:

```powershell
mvn -pl services/order-service spring-boot:run
```

The Order Service runs at `http://localhost:8080`.

### Start the Notification Service

Open a separate PowerShell terminal and load the same PostgreSQL and RabbitMQ credentials.

Start the service:

```powershell
mvn -pl services/notification-service spring-boot:run
```

The Notification Service runs at `http://localhost:8081`.

### Configure RabbitMQ Dead-Letter Routing

With RabbitMQ running and `RABBITMQ_PASSWORD` configured, execute:

```powershell
.\infrastructure\rabbitmq\configure-dlx.ps1
```

The script creates or updates the RabbitMQ dead-letter policy.

The Notification Service declares the dead-letter exchange, queue, and binding during startup.

See the **RabbitMQ Dead Letter Queue (DLQ) Configuration** section for configuration details and verification steps.

### Verify the Services

Create an order using the Order Service REST API.

Once the outbox worker publishes the corresponding event and the Notification Service processes it, retrieve its notifications:

```http
GET /api/v1/notifications?orderId={uuid}&page=0&size=20
```

The response contains the persisted notifications and pagination metadata.

**Security note:** Local credentials must not be committed to version control. Authentication and authorization are not yet implemented for the Notification Service API.

### Health Endpoint

```http
GET /api/v1/health HTTP/1.1
Host: localhost:8080
```

Expected response:

```json
{
  "service": "order-service",
  "status": "UP"
}
```

> This endpoint currently returns a static status and does not verify external dependencies.

### RabbitMQ Dead Letter Queue (DLQ) Configuration

The Notification Service uses a **Dead Letter Queue (DLQ)** to retain messages rejected during event consumption.

Dead-letter routing is configured through a **RabbitMQ policy** rather than hardcoded queue arguments. This approach allows routing configuration to be updated without deleting or recreating existing durable queues.

#### Prerequisites

- Docker Desktop with Docker Compose.
- RabbitMQ Management API accessible on port `15672`.
- `RABBITMQ_PASSWORD` environment variable configured.

#### Setup

Start the infrastructure:

```powershell
docker compose up -d
```

Ensure that `RABBITMQ_PASSWORD` is available in the current PowerShell session, then apply the dead-letter policy:

```powershell
.\infrastructure\rabbitmq\configure-dlx.ps1
```

The script creates or updates the `eventflow-notifications-dlx` policy with the following configuration:

| Property | Value |
|---|---|
| Source queue | `eventflow.notifications.orders.q` |
| Dead Letter Exchange (DLX) | `eventflow.notifications.dlx` |
| Dead Letter Queue (DLQ) | `eventflow.notifications.orders.dlq` |
| Dead-letter routing key | `notifications.orders.dead` |
| Policy priority | `1` |

The DLX, DLQ, and their binding are declared by the Notification Service during application startup.

#### Verification

Open the RabbitMQ Management UI at `http://localhost:15672` and verify that:

1. The `eventflow-notifications-dlx` policy is applied to the source queue.
2. The dead-letter exchange and queue exist.
3. Messages rejected without requeue are routed to `eventflow.notifications.orders.dlq`.

#### Failure Handling

The consumer is configured to reject failed messages without requeueing them to the source queue.

When the dead-letter configuration is active, rejected messages are routed to the DLQ for inspection and further handling.

**Note:** Dead-lettered messages are not automatically retried or reprocessed. Recovery requires explicit intervention or a dedicated retry mechanism.

## Testing

EventFlow includes automated unit and integration tests covering:

- Domain invariants and order lifecycle transitions
- Application use cases and REST API contracts
- Optimistic concurrency control
- Transactional Outbox persistence and publishing
- RabbitMQ event delivery and publisher confirmations
- Idempotent event consumption
- Dead Letter Queue routing
- Transaction rollback and notification consistency
- Paginated notification queries
- PostgreSQL integration testing with Testcontainers

Run the complete test suite:

```bash
mvn clean verify
```

Docker must be available when executing integration tests that use Testcontainers.

GitHub Actions provides automated Maven verification for the project.

## Roadmap

- [x] Maven multi-module foundation
- [x] Spring Boot Order Service
- [x] PostgreSQL persistence and Flyway migrations
- [x] Order lifecycle REST API
- [x] Optimistic concurrency control
- [x] Transactional Outbox pattern
- [x] RabbitMQ event publishing and consumption
- [x] Notification Service
- [x] Idempotent event processing
- [x] Dead Letter Queue
- [x] Notification persistence and paginated REST API
- [x] Automated integration testing with Testcontainers
- [x] GitHub Actions CI workflow
- [ ] Automated retry and dead-letter recovery
- [ ] Authentication and authorization
- [ ] Inventory Service
- [ ] Containerized application services
- [ ] Observability with Prometheus and Grafana

## Current Scope

**Order Service**

- Order creation and retrieval
- Order confirmation and cancellation
- Domain-level business invariants
- Optimistic concurrency control
- PostgreSQL persistence
- Transactional Outbox event publishing
- REST API and OpenAPI documentation

**Notification Service**

- RabbitMQ event consumption
- Idempotent event processing
- Transactional notification persistence
- Dead Letter Queue routing
- Paginated notification retrieval API

**Current limitations**

- Notifications are persisted internally; email and push delivery are not yet implemented.
- Automatic retries and dead-letter recovery are not implemented.
- Authentication and authorization are not yet available.
- Both application services currently require separate startup procedures in the local environment.

## License

No license has been specified yet.
  
