# EventFlow

Event-driven order management system built with Java, Spring Boot, and Apache Kafka.

> **Project status:** v0.1.0 — Foundation. The event-driven architecture, database persistence, and Kafka integration are planned for future releases.

## Tech Stack

- Java 25
- Spring Boot 4.1.2
- Maven (multi-module)
- JUnit 5 and Spring MockMvc
- GitHub Actions

## Project Structure

```text
eventflow/
├── .github/workflows/ci.yml
├── services/
│   └── order-service/
│       ├── pom.xml
│       └── src/
├── pom.xml
└── README.md
```

## Getting Started

### Prerequisites

- JDK 25
- Maven 3.9+
- Git

### Build and Test

From the repository root:

```bash
mvn clean verify
```

### Run the Order Service

```bash
mvn -pl services/order-service spring-boot:run
```

The application starts on port `8080` by default.

### Health Endpoint

```http
GET http://localhost:8080/api/v1/health
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

The project includes a Spring Boot integration test using JUnit 5 and MockMvc to validate the health endpoint response.

The GitHub Actions CI workflow runs Maven verification on pull requests targeting `main` and pushes to `main`.

## Roadmap

- [x] Maven multi-module foundation
- [x] Spring Boot order service bootstrap
- [x] Health endpoint and integration test
- [x] GitHub Actions CI
- [ ] PostgreSQL persistence and database migrations
- [ ] Order management REST endpoints
- [ ] Inventory and notification services
- [ ] Kafka event-driven communication
- [ ] Docker containerization
- [ ] Observability with Prometheus and Grafana

## License

No license has been specified yet.
  