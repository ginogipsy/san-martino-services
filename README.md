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
- ✅ CI/CD GitHub Actions — vedi [docs/ci-cd.md](docs/ci-cd.md)

## Sviluppo locale

```bash
# 1. Avvia Vault e popola i secret KV — PREREQUISITO dei test:
#    ogni application.yaml dichiara `spring.config.import: vault://`
#    non opzionale, quindi senza Vault il contesto Spring non parte.
docker compose up -d vault vault-init

# 2. Builda e testa tutto (i database dei test li fornisce Testcontainers)
./mvnw clean verify

# 3. Avvia un singolo servizio
cd events-service
../mvnw spring-boot:run

# 4. Builda l'immagine Docker (locale, no push)
../mvnw spring-boot:build-image

# 5. OpenAPI UI (springdoc-openapi)
# http://localhost:8081/swagger-ui.html
```

> Su Windows con Docker Desktop 29.x, Testcontainers attualmente non riesce a
> raggiungere il daemon: i test che ne dipendono si validano solo in CI.
> Diagnosi completa in [docs/ci-cd.md](docs/ci-cd.md).

## Documentazione

| Documento | Contenuto |
|---|---|
| [docs/ci-cd.md](docs/ci-cd.md) | pipeline GitHub Actions, cache monorepo, publishing GHCR, docs generate |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | regole di codice, ID Sonar rilevanti, pattern architetturali |
| [docs/VAULT_INTEGRATION.md](docs/VAULT_INTEGRATION.md) | integrazione Spring Cloud Vault |
| [docs/CLAUDE.md](docs/CLAUDE.md) | riepilogo di stato e avanzamento del progetto |
| [docs/api/](docs/api/) | reference OpenAPI **generato** — non editare a mano |
| [CLAUDE.md](CLAUDE.md) | istruzioni operative per agenti |

## Contribuire

Branching **git flow**: `feature/*` → PR verso `develop`; `release/*` e `hotfix/*` → `master`.
Commit: `(#NNN) descrizione all'infinito inglese`. Dettagli in [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md).

## Layout repo

```
san-martino-services/
├── pom.xml                  parent BOM + plugin config centralizzata
├── CLAUDE.md                istruzioni operative per agenti
├── qodana.yaml              profilo Qodana + bootstrap generate-sources
├── api/                     OpenAPI spec — fonte di verità dei contratti
│   ├── events-api.yaml
│   ├── sagas-api.yaml
│   └── stands-api.yaml
├── docs/
│   ├── ci-cd.md             pipeline GitHub Actions
│   ├── CODING_STANDARDS.md  regole di codice e pattern
│   ├── CLAUDE.md            stato del progetto
│   ├── VAULT_INTEGRATION.md
│   └── api/                 reference OpenAPI GENERATO (redocly + prettier)
├── .github/workflows/
│   ├── ci-cd.yml            build, test, immagini OCI, GHCR, docs
│   └── qodana_code_quality.yml
├── docker-compose.yml       dev infra (Postgres, Kafka, Keycloak, Vault)
└── events-service/          + stands-service, gateway, saga-orchestrator,
                             #   notifications-service, auth-service
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
