# San Martino — backend services

Monorepo Maven multi-module per il backend di **"Le cantine di San Martino"** (Arce, FR).

## Stack

- Java 26 (non-LTS, bleeding edge)
- Spring Boot 4.0.6 / Spring Framework 7
- PostgreSQL + Flyway
- API-first con OpenAPI 3 + openapi-generator
- Build immagini via `spring-boot:build-image` (Buildpacks) → GHCR
- Testing: JUnit 5 + Testcontainers + MockMvc

## Topologia target

```
gateway (Spring Cloud Gateway + Resilience4j)
  ├─→ events-service (Postgres)
  ├─→ stands-service (Postgres)
  ├─→ saga-orchestrator (Postgres)
  └─→ notifications-service (Kafka consumer + FCM)
infra: Keycloak • Kafka • Grafana/Loki/Tempo/Prometheus
```

## Stato attuale

- ✅ `events-service` — scaffold completo (in corso)
- ⬜ `stands-service`
- ⬜ `gateway`
- ⬜ `saga-orchestrator`
- ⬜ `notifications-service`

## Sviluppo locale

```bash
# 1. Avvia infra (Postgres) in background
docker compose up -d postgres

# 2. Builda tutto
./mvnw clean install

# 3. Avvia un singolo servizio
cd events-service
../mvnw spring-boot:run

# 4. Builda l'immagine Docker (locale, no push)
../mvnw spring-boot:build-image

# 5. OpenAPI UI (springdoc-openapi)
# http://localhost:8081/swagger-ui.html
```

## Layout repo

```
san-martino-services/
├── pom.xml                  parent BOM + plugin config centralizzata
├── api/                     OpenAPI spec — fonte di verità dei contratti
│   └── events-api.yaml
├── docker-compose.yml       dev infra (Postgres, in futuro Kafka/Keycloak)
└── events-service/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/ginogipsy/sanmartino/events/
        │   │   ├── EventsApplication.java
        │   │   ├── api/         controller che implementa l'API generata
        │   │   ├── domain/      entity JPA + repository
        │   │   └── service/     business logic
        │   └── resources/
        │       ├── application.yaml
        │       └── db/migration/V1__init.sql
        └── test/...
```
