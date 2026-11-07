
# EventFlow

EventFlow is an order management platform built with Java, Spring Boot, and PostgreSQL, designed to evolve into an event-driven architecture using Apache Kafka.

> **Project status:** v0.2.0 — Released.
> The Order Service supports order creation and retrieval,
> PostgreSQL persistence, REST APIs, and OpenAPI documentation.
> Kafka-based communication and additional microservices
> are planned for future releases.


## Tech Stack

- Java 25
- Spring Boot 4.1.2
- Maven (multi-module)
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway
- Jakarta Bean Validation
- springdoc-openapi / Swagger UI
- Docker Compose
- JUnit 5, Mockito, and MockMvc
- Testcontainers
- GitHub Actions

## Project Structure

```text
eventflow/
├── .github/workflows/ci.yml
├── services/
│   └── order-service/
│       ├── pom.xml
│       └── src/
├── compose.yaml
├── .env.example
├── pom.xml
└── README.md
```

## Architecture

EventFlow follows a layered, ports-and-adapters architecture,
separating business rules from application orchestration and
infrastructure concerns.

```text
HTTP Client
    |
    v
OrderController
    |
    v
Application Use Cases
  - CreateOrderUseCase
  - GetOrderUseCase
    |
    v
Domain
  - Order / OrderItem
  - OrderRepository (port)
    |
    v
JpaOrderRepositoryAdapter
    |
    v
Spring Data JPA / Hibernate
    |
    v
PostgreSQL
```

The domain model is independent of Spring Data JPA and
database-specific implementations.

Flyway manages database schema migrations, while Testcontainers
provides isolated PostgreSQL instances for integration testing.

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

Invalid requests return HTTP `400 Bad Request`.


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

Database schema changes are managed with Flyway.

- `V1__create_orders_tables.sql` — Initial schema.
- `V2__add_order_version.sql` — Order version column.

Existing migrations are preserved to maintain
schema history integrity.

## Running Locally

### Requirements

- JDK 25
- Apache Maven
- Docker Desktop or a compatible Docker Engine

### Database setup

Create a `.env` file from `.env.example` and define a local
`POSTGRES_PASSWORD`.


#### Windows PowerShell

From the project root:

```powershell
docker compose up -d

$env:POSTGRES_PASSWORD = (Get-Content .env |
    Where-Object { $_ -match '^POSTGRES_PASSWORD=' }) `
    -replace '^POSTGRES_PASSWORD=', ''

mvn -pl services/order-service spring-boot:run
```

The `.env` file must remain untracked and must never
contain credentials intended for production.

The database is available locally on port `5432`.

### Start the Order Service

Ensure the PostgreSQL container is running and the
`POSTGRES_PASSWORD` environment variable is configured.

From the project root:

```bash
mvn -pl services/order-service spring-boot:run
```

The service starts at http://localhost:8080.

On Windows, follow the PowerShell instructions above
to configure the database and start the application.

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


## Testing

EventFlow includes automated tests covering:

- Domain invariants and order lifecycle rules
- Application use cases
- Domain-to-JPA entity mapping
- REST controller behavior and request validation
- PostgreSQL persistence with Testcontainers
- End-to-end application flows using MockMvc
- OpenAPI documentation generation

Run the complete test suite:

```bash
mvn clean verify
```

The v0.2.0 release candidate has 40 automated tests
passing locally.

Docker must be running for PostgreSQL integration tests.
Testcontainers automatically starts an isolated database.

GitHub Actions runs Maven verification on pull requests
targeting `main` and on pushes to `main`.

## Roadmap

- [x] Maven multi-module foundation
- [x] Spring Boot order service bootstrap
- [x] Health endpoint and integration test
- [x] GitHub Actions CI
- [x] PostgreSQL persistence and database migrations
- [x] Order management REST endpoints
- [ ] Inventory and notification services
- [ ] Kafka event-driven communication
- [ ] Order Service container image
- [ ] Observability with Prometheus and Grafana

## Current Scope

The Order Service currently supports:

- Creating orders with validated items
- Retrieving orders by UUID
- Persisting order aggregates in PostgreSQL
- Structured validation error responses
- OpenAPI documentation

Order updates, deletion, confirmation and cancellation
endpoints are not yet available.

These capabilities are planned for future milestones.

## License

No license has been specified yet.
  
