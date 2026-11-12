# San Martino — backend services

Monorepo Maven multi-module per il backend di **"Le cantine di San Martino"** (Arce, FR).

## Stack

- Java 26 (non-LTS, bleeding edge)
- Spring Boot 4.0.6 / Spring Framework 7
- PostgreSQL + Flyway
- API-first con OpenAPI 3 + openapi-generator
- Build immagini via `Dockerfile` multi-stage (Temurin 26) → GHCR
- Testing: JUnit 5 + Testcontainers + MockMvc
- Observability: aspect AOP + log JSON (ECS) → Loki, metriche Actuator → Prometheus, UI Grafana

## Topologia target

```
gateway (Spring Cloud Gateway + Resilience4j)
  ├─→ auth-service (Postgres + Keycloak)
  ├─→ events-service (Postgres)
  ├─→ stands-service (Postgres)
  ├─→ saga-orchestrator (Postgres)
  ├─→ audit-service (Postgres)
  └─→ notifications-service (Kafka consumer + FCM)
infra: Keycloak • Kafka • Grafana/Loki/Prometheus (Tempo: da fare)
```

Tutti i servizi dipendono da `common-observability`, la libreria condivisa con l'aspect di
logging, correlation id e audit logging AOP (`@Auditable`) — vedi [docs/observability.md](docs/observability.md) e [docs/audit-logging.md](docs/audit-logging.md).

## Stato attuale

- ✅ `events-service` — scaffold completo (in corso)
- ✅ `stands-service`
- ✅ `auth-service`
- ✅ `audit-service`
- ⬜ `gateway`
- ⬜ `saga-orchestrator`
- ⬜ `notifications-service`
- ✅ CI/CD GitHub Actions — vedi [docs/ci-cd.md](docs/ci-cd.md)

## Sviluppo locale

L'ambiente supporta due modalità di lavoro complementari:

### Opzione A: Debug rapido da IDE (Docker Compose)
Ideale per lo sviluppo quotidiano, aggiunta di feature e debug con breakpoint direttamente da IntelliJ IDEA.

```bash
# 1. Avvia SOLO l'infrastruttura di supporto (PostgreSQL, Vault, Kafka, Keycloak, Grafana)
#    I microservizi applicativi hanno profilo "apps" e non vengono buildati né avviati come container.
docker compose up -d

# 2. Avvia il microservizio desiderato da IntelliJ in modalità Debug:
#    - Esegui ad esempio StandsApplication o EventsApplication
#    - Spring Boot contatta Vault su http://localhost:8200 per le credenziali DB e si connette al PostgreSQL locale
#    - Puoi inserire breakpoint, ispezionare lo stato ed effettuare hot-reload immediato
```

### Opzione B: Integrazione completa & E2E (Kubernetes con Kind)
Ideale per testare l'intero stack distribuito (tutti i microservizi, Web Panel Angular, Ingress Traefik, networking e app Android).

```bash
# Avvia l'intero cluster locale simulando la produzione
./scripts/k8s-dev-up.sh

# Quando hai terminato i test, arresta il cluster:
./scripts/k8s-dev-down.sh
```

> [!TIP]
> **Come alternare le due modalità:** Entrambe condividono le porte host (`8080`, `8200`, `9080`). Se passi da una all'altra, arresta la precedente:
> - Da Kubernetes a Compose: `./scripts/k8s-dev-down.sh` seguito da `docker compose up -d`
> - Da Compose a Kubernetes: `docker compose down` seguito da `./scripts/k8s-dev-up.sh`

### Comandi utili da riga di comando

```bash
# Build e test completo del reactor Maven (i database dei test li fornisce Testcontainers)
./mvnw clean verify

# Compilazione ed esecuzione manuale da CLI di un singolo modulo
./mvnw -pl :common-observability -am install -DskipTests
cd events-service && ../mvnw spring-boot:run

# Build locale di un'immagine container (senza push)
docker build --build-arg SERVICE=events-service -t sanmartino/events-service:dev .
```

> Su Windows con Docker Desktop 29.x, Testcontainers attualmente non riesce a
> raggiungere il daemon: i test che ne dipendono si validano solo in CI.
> Diagnosi completa in [docs/ci-cd.md](docs/ci-cd.md).

## Documentazione

| Documento | Contenuto |
|---|---|
| [docs/RELEASE_GUIDE.md](docs/RELEASE_GUIDE.md) | **Guida ai Rilasci e Versionamento (Smart Versioning)** |
| [docs/ci-cd.md](docs/ci-cd.md) | pipeline GitHub Actions, cache monorepo, publishing GHCR, docs generate |
| [docs/observability.md](docs/observability.md) | aspect AOP di logging, correlation id, stack Prometheus/Loki/Grafana |
| [docs/audit-logging.md](docs/audit-logging.md) | business audit logging, aspect `@Auditable`, Kafka topic, Postgres e API protetta |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | regole di codice, ID Sonar rilevanti, pattern architetturali |
| [docs/VAULT_INTEGRATION.md](docs/VAULT_INTEGRATION.md) | integrazione Spring Cloud Vault |
| [docs/kubernetes.md](docs/kubernetes.md) | architettura e guida all'infrastruttura Kubernetes (Kind e Cloud) |
| [docs/api/](docs/api/) | reference OpenAPI **generato** — non editare a mano |
| [CLAUDE.md](CLAUDE.md) | istruzioni unificate per agenti AI su tutto l'ecosistema San Martino |

## Contribuire

Branching **git flow**: `feature/*` → PR verso `develop`; `release/*` e `hotfix/*` → `master`.
Commit: `(#NNN) descrizione all'infinito inglese`. Dettagli in [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md).

## 🛡️ Sicurezza e Portfolio

Questo repository è stato configurato per essere mostrato in un portfolio pubblico:
- **Zero Segreti**: Tutti i segreti (password DB, client secrets) sono stati rimossi dai file di configurazione.
- **Configurazione via .env**: Usa il file `.env.example` come base per creare il tuo file `.env` locale con le tue credenziali.
- **Vault Integration**: I segreti a runtime sono gestiti tramite HashiCorp Vault.

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
│   ├── observability.md     logging AOP, correlation id, Prometheus/Loki/Grafana
│   ├── CLAUDE.md            stato del progetto
│   ├── VAULT_INTEGRATION.md
│   └── api/                 reference OpenAPI GENERATO (redocly + prettier)
├── .github/workflows/
│   ├── ci-cd.yml            build, test, immagini OCI, GHCR, docs
│   └── qodana_code_quality.yml
├── docker-compose.yml       dev infra (Postgres, Kafka, Keycloak, Vault, osservabilità)
├── docker/                  config dello stack: prometheus/, loki/, promtail/, grafana/
├── common-observability/    libreria condivisa: aspect di logging + correlation id
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
