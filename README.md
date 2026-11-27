# San Martino — Backend Services

Monorepo Maven multi-modulo per il backend dell'ecosistema **"Le Cantine di San Martino"** (Arce, FR).

## Stack Tecnologico

- **Java 25 (LTS)** + **Spring Boot 4.0.6** / Spring Framework 7 / Spring Cloud 2025.1
- **Approccio API-First**: contratti OpenAPI 3 (`api/*.yaml`) + generazione server `openapi-generator`
- **Persistenza**: PostgreSQL 17 + migrazioni **Flyway** (con seed storico multi-edizione)
- **Messaggistica & Saghe**: Apache Kafka (KRaft) per orchestrazione saghe e audit trail asincrono
- **Sicurezza & Segreti**: OAuth2 / JWT con **Keycloak 26.8** + **HashiCorp Vault** KV
- **Container & Orchestrazione**: `Dockerfile` multi-stage (`eclipse-temurin:25`, layered JAR non-root) + **Kubernetes /
  Kustomize** (`Kind` locale + overlay `cloud` con PSA `restricted`, HPA, PDB, cert-manager)
- **Testing & Qualità**: JUnit 5 + Testcontainers + MockMvc + analisi statica gratuita **SpotBugs & PMD** in CI
- **Observability**: aspect AOP (`common-observability`) + log JSON ECS → **Loki**, metriche Micrometer →
  **Prometheus**, tracing distribuito OpenTelemetry OTLP → **Tempo**, dashboard **Grafana**

## Topologia & Architettura

```
gateway (Spring Cloud Gateway MVC + Resilience4j + CORS + Public Config)
  ├─→ auth-service          (PostgreSQL + Keycloak 26 — login, registrazione, ruoli)
  ├─→ events-service        (PostgreSQL — edizioni della festa UPCOMING / PAST)
  ├─→ stands-service        (PostgreSQL — cantine multi-edizione, menu, ricerca fuzzy,
  │                          recensioni/moderazione, preferiti, analytics, coda ordini & pulsantiera IoT)
  ├─→ saga-orchestrator     (PostgreSQL + Kafka producer — creazione evento + cantine con compensazione)
  ├─→ audit-service         (PostgreSQL + Kafka consumer — audit log di business per ROLE_ADMIN)
  └─→ notifications-service (Kafka consumer + Firebase Cloud Messaging — notifiche push)
infra: Keycloak • HashiCorp Vault • Kafka • Grafana / Loki / Prometheus / Tempo
iot:   esp32-stand-keypad   (Firmware ESP32 per pulsantiere fisiche Cassa / Cucina / Consegna)
```

Tutti i microservizi dipendono da **`common-observability`**, libreria interna condivisa che fornisce logging
strutturato AOP (`@Logged`, `@Masked`), propagazione `X-Correlation-Id` su HTTP e Kafka, header `X-Trace-Id`, tracing
distribuito OpenTelemetry e business audit logging dichiarativo (`@Auditable`).

## Stato dei Moduli

- ✅ `common-observability` — libreria AOP di logging, correlation ID, tracing OTLP e `@Auditable`
- ✅ `gateway` — routing centralizzato, circuit breaker/timeout Resilience4j, fallback e public config da Vault
- ✅ `auth-service` — autenticazione, registrazione utenti, sincronizzazione ruoli Keycloak e profili
- ✅ `events-service` — CRUD edizioni della festa (`2014`–`2026`) con stato dinamico `UPCOMING` / `PAST`
- ✅ `stands-service` — cantine multi-evento (`stand_event_participations`), menu per edizione e continuità storica delle
  recensioni, ricerca intelligente con sinonimi/fuzzy e analytics, moderazione recensioni, preferiti, gestione staff e
  coda ordini per pulsantiera a 3 postazioni (`CASHIER`, `KITCHEN`, `DELIVERY`)
- ✅ `saga-orchestrator` — saga distribuita `create-event-with-stands` con compensazione automatica ed eventi Kafka
- ✅ `notifications-service` — consumer Kafka `sanmartino.saga.events` e invio notifiche push FCM
- ✅ `audit-service` — consumer Kafka `sanmartino.audit.events`, persistenza e API di consultazione `ROLE_ADMIN`
- ✅ `iot/esp32-stand-keypad` — firmware Arduino/ESP32 (5 display TM1637 + 10 slot LED/pulsanti)
- ✅ `k8s/` — manifest Kubernetes/Kustomize (`overlays/local` su Kind e `overlays/cloud` di produzione)
- ✅ CI/CD GitHub Actions — build/test, Smart Versioning, analisi statica SpotBugs & PMD, docs OpenAPI e deploy GHCR

## Sviluppo Locale

L'ambiente supporta due modalità di lavoro complementari:

### Opzione A: Debug rapido da IDE (Docker Compose)

Ideale per lo sviluppo quotidiano, aggiunta di feature e debug con breakpoint da IntelliJ IDEA.

```bash
# 1. Avvia SOLO l'infrastruttura di supporto (PostgreSQL, Vault, Kafka, Keycloak, Grafana/Loki/Tempo/Prometheus)
#    I microservizi applicativi sono sul profilo "apps" e non vengono avviati come container di default.
docker compose up -d

# 2. Avvia il microservizio desiderato da IntelliJ (o via ./mvnw spring-boot:run):
#    - Esegui ad esempio StandsApplication, EventsApplication o GatewayApplication
#    - Il servizio recupera i segreti da Vault su http://localhost:8200 e si collega al proprio PostgreSQL locale
```

### Opzione B: Integrazione completa & E2E (Kubernetes con Kind)

Ideale per testare l'intero ecosistema distribuito (7 microservizi, Web Panel Angular, Ingress Traefik, Keycloak, stack
di osservabilità e app Android).

```bash
# Avvia il cluster Kind locale, compila/carica le immagini e applica i manifest
./scripts/k8s-dev-up.sh

# Arresta il cluster locale
./scripts/k8s-dev-down.sh
```

> [!TIP]
> **Alternanza tra Compose e Kind:** Entrambe le modalità espongono le porte host (`8080`, `8200`, `9080`). Prima di
passare da una all'altra, arresta la precedente (`./scripts/k8s-dev-down.sh` oppure `docker compose down`).

### Comandi Utili

```bash
# Build e test completo del reactor Maven (richiede Vault attivo; i DB dei test sono forniti da Testcontainers)
./mvnw clean verify

# Analisi statica gratuita (PMD + SpotBugs su Java 25)
./mvnw test-compile pmd:pmd spotbugs:spotbugs

# Compilazione rapida di un singolo servizio con le dipendenze interne
./mvnw -pl :stands-service -am -DskipTests package

# Build locale dell'immagine container OCI multi-stage
docker build --build-arg SERVICE=stands-service -t ghcr.io/ginogipsy/sanmartino/stands-service:dev .
```

## Documentazione

| Documento                                                            | Contenuto                                                                                                 |
|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| [docs/RELEASE_GUIDE.md](docs/RELEASE_GUIDE.md)                       | **Guida ai Rilasci e Versionamento (Smart Versioning)**                                                   |
| [docs/ci-cd.md](docs/ci-cd.md)                                       | Pipeline GitHub Actions, cache monorepo, SpotBugs/PMD, publishing GHCR e docs OpenAPI                     |
| [docs/observability.md](docs/observability.md)                       | Aspect AOP di logging, correlation ID, tracing OpenTelemetry/Tempo e stack Grafana                        |
| [docs/audit-logging.md](docs/audit-logging.md)                       | Business audit logging (`@Auditable`), topic Kafka, PostgreSQL e API protetta                             |
| [docs/search-engine.md](docs/search-engine.md)                       | Ricerca intelligente piatti/vini (fuzzy, sinonimi), analytics e continuità multi-edizione                 |
| [docs/VAULT_INTEGRATION.md](docs/VAULT_INTEGRATION.md)               | Integrazione HashiCorp Vault per segreti e credenziali runtime                                            |
| [docs/kubernetes.md](docs/kubernetes.md)                             | Architettura Kubernetes / Kustomize (`local` Kind e `cloud`)                                              |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md)                 | Standard di codice Java, sicurezza e pattern architetturali                                               |
| [docs/TODO.md](docs/TODO.md)                                         | Roadmap evolutiva (Porting Quarkus, Gestione Economica Piatti/Bevande, SonarQube/JaCoCo, Web Flasher IoT) |
| [iot/esp32-stand-keypad/README.md](iot/esp32-stand-keypad/README.md) | Schema hardware, cablaggio GPIO e installazione firmware ESP32 della pulsantiera cantina                  |
| [docs/api/](docs/api/)                                               | Reference HTML OpenAPI **generato automaticamente** — non modificare a mano                               |
| [CLAUDE.md](CLAUDE.md)                                               | Istruzioni operative unificate per l'intero ecosistema San Martino                                        |

## Convenzioni Git Flow & Contributi

- **Branching (`git flow`):** `feature/<NNN>-<descrizione>` o `bugfix/<NNN>-<descrizione>` → PR verso `develop`;
  `release/<X.Y.Z>` e `hotfix/<X.Y.Z>` → PR verso `master` con bump automatico su `develop`.
- **Commit:** prefisso obbligatorio `(#NNN) descrizione`. Dettagli
  in [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md).

## 🛡️ Sicurezza e Portfolio

Repository progettato secondo pratiche production-grade:

- **Zero segreti nel codice**: credenziali DB, token e chiavi API (inclusa Google Maps) sono esternalizzati su
  **HashiCorp Vault** e variabili d'ambiente (`.env.example`).
- **Container hardened**: immagini runtime basate su `eclipse-temurin:25-jre`, utente non-root (`UID 10001`) e
  conformità Pod Security Admission `restricted`.

## Layout del Repository

```
san-martino-services/
├── pom.xml                        Parent BOM, versioni centralizzate (${revision}) e plugin
├── Dockerfile                     Build multi-stage layered JAR per tutti i 7 microservizi
├── CLAUDE.md                      Guida architetturale e convenzioni dell'ecosistema
├── config/spotbugs-exclude.xml    Filtri di esclusione per analisi statica SpotBugs
├── api/                           Contratti OpenAPI 3 (fonte di verità API-first)
│   ├── audit-api.yaml
│   ├── events-api.yaml
│   ├── sagas-api.yaml
│   └── stands-api.yaml
├── docs/                          Documentazione tecnica, guide operative e docs/api/ generate
├── .github/workflows/
│   ├── ci-cd.yml                  Build & test, validazione K8s, immagini OCI, docs OpenAPI
│   ├── release.yml                Smart Versioning, tag Git, GitHub Release e bump su develop
│   ├── deploy.yml                 Pubblicazione immagini manuale (gate) e automatica (master/tag)
│   └── code-quality.yml           Analisi statica gratuita SpotBugs + PMD su JDK 25
├── docker-compose.yml             Infrastruttura locale (Postgres, Kafka, Keycloak, Vault, LGTM)
├── docker/                        Configurazioni Prometheus, Loki, Tempo, Alloy/Promtail, Grafana
├── k8s/                           Manifest Kubernetes Kustomize (base, components, overlays local/cloud)
├── scripts/                       Automazione cluster locale Kind (k8s-dev-up.sh / k8s-dev-down.sh)
├── iot/esp32-stand-keypad/        Firmware Arduino/ESP32 e documentazione hardware pulsantiera
├── common-observability/          Libreria condivisa (logging AOP, correlation ID, tracing, audit)
├── gateway/                       API Gateway MVC + Resilience4j
├── auth-service/                  Autenticazione, utenti e integrazione Keycloak
├── events-service/                Gestione edizioni della festa
├── stands-service/                Cantine multi-evento, menu, ricerca, recensioni, ordini & pulsantiera
├── saga-orchestrator/             Orchestrazione saghe distribuite + Kafka producer
├── notifications-service/         Consumer Kafka + notifiche push FCM
└── audit-service/                 Consumer Kafka + persistenza e API audit log
```
