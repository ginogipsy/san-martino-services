# CLAUDE.md — Istruzioni Unificate per Agenti AI (Ecosistema San Martino)

Guida unica e completa per agenti AI che operano sull'ecosistema distribuito **"Le cantine di San Martino"** (Arce, FR).

---

## 1. Mappa dei Repository & Architettura

Il sistema è composto da tre repository locali interconnessi:

```
/home/francesco-grossi/Projects/san-martino/
├── san-martino-services/         # Backend monorepo Maven (Java 25 LTS / Spring Boot 4.0.6) + K8s
├── san-martino-user-panel/       # Frontend Web SPA (Angular 21 / NGINX unprivileged)
└── sanmartinoapp/                # Mobile App Android nativa (Kotlin / Jetpack Compose)
```

---

## 2. Backend a Microservizi (`san-martino-services`)

Monorepo Maven multi-modulo con approccio **API-First** (OpenAPI Generator) e architettura distribuita:
* `gateway`: Spring Cloud Gateway MVC (routing, circuit breaker Resilience4j, iniezione correlation ID).
* `auth-service`: Autenticazione e gestione utenti, integrato con Keycloak 26.8.2 e PostgreSQL.
* `events-service`: Gestione eventi locali, notifiche e anagrafica (PostgreSQL).
* `stands-service`: Gestione delle cantine gastronomiche e disponibilità prodotti (PostgreSQL).
* `saga-orchestrator`: Orchestrazione saghe distribuite per transazioni asincrone su Kafka (PostgreSQL).
* `notifications-service`: Consumer di eventi da topic Kafka (`sanmartino.saga.events`) con push via Firebase Cloud Messaging (FCM).
* `audit-service`: Tracciamento e audit trail eventi di business via Kafka (`sanmartino.audit.events`), con persistenza PostgreSQL e API protetta `ROLE_ADMIN`.
* `common-observability`: Modulo AOP condiviso (aspect di logging, correlation ID in MDC, log JSON ECS, metriche Micrometer, aspect `@Auditable` per emissione audit).

### Comandi Backend
```bash
docker compose up -d                              # Avvia solo infra locale (Vault, DB, Kafka, Keycloak); le app Java sono su profilo 'apps'
./mvnw clean verify                               # Build e test dell'intero reactor
./mvnw -pl :<service> -am -DskipTests package     # Compilazione di un singolo modulo
docker build --build-arg SERVICE=<service> -t ghcr.io/ginogipsy/sanmartino/<service>:dev . # Build immagine OCI
```

### Gotchas & Regole Backend
* **I test non partono senza Vault:** ogni `application.yaml` include `spring.config.import: vault://` non opzionale; i datasource usano placeholder (`${EVENTS_DB_URL}`, ecc.) risolti dai secret KV.
* **Testcontainers:** su Windows è presente una limitazione nota su named pipe Docker Desktop; la validazione definitiva con Testcontainers avviene nei runner Linux di CI.
* **I sorgenti generati non si toccano:** la fonte di verità dei contratti è `api/*.yaml`. Le classi e interfacce generate in `target/generated-sources/openapi/` sono escluse da git.
* **Rigenerazione Docs API:** se si modificano file in `api/*.yaml`, rigenerare `docs/api/` con Redocly e Prettier alle versioni esatte specificate in `docs/ci-cd.md`.
* **Standard di codice Java:** consultare e rispettare sempre [docs/CODING_STANDARDS.md](san-martino-services/docs/CODING_STANDARDS.md).

---

## 3. Web User Panel (`san-martino-user-panel`)

Applicazione Web SPA per l'amministrazione delle cantine gastronomiche.

* **Stack:** Angular 21, TypeScript, NGINX unprivileged (porta `8080`, UID `101`).
* **Conformità K8s:** Immagine container multi-stage (`node:22-alpine` -> `nginxinc/nginx-unprivileged:1.29-alpine-slim`) conforme al profilo Pod Security Admission `restricted`.

### Comandi Web Panel
```bash
npm install                                       # Installazione dipendenze
npm start                                         # Server di sviluppo locale (ng serve)
npm run build                                     # Build di produzione Angular
npm test                                          # Esecuzione unit test
docker build -t san-martino-user-panel:local .    # Build immagine container NGINX
```

### Gotchas Web Panel
* **URL Relativi:** in `src/environments/environment.ts` gli endpoint sono configurati come percorsi relativi (`/api/auth`, `/v1/stands`). Dietro l'Ingress Traefik del cluster (o in produzione), le chiamate transitano sullo stesso host evitando problemi di CORS o configurazioni per-ambiente nel bundle JS.

---

## 4. App Mobile Android (`sanmartinoapp`)

Applicazione mobile nativa per i visitatori dell'evento.

* **Stack:** Kotlin, Jetpack Compose, Retrofit, Room, Android Gradle Plugin (AGP).

### Comandi Android
```bash
./gradlew assembleDebug                           # Build APK debug
./gradlew test                                    # Esecuzione unit test
./gradlew connectedAndroidTest                    # Test di strumentazione su device/emulatore
./gradlew :app:syncOpenApiSpecs                   # Sincronizza contratti YAML da san-martino-services/api
./gradlew :app:openApiGenerateEvents :app:openApiGenerateStands # Genera client OpenAPI Android
./gradlew adbReverse                              # Configura port forwarding per emulatore (API 36+)
```

### Gotchas & Networking Android
* **NON usare `10.0.2.2`:** su immagini emulatore API 36+, le *Local Network Protections* scartano il traffico UID verso la subnet locale dell'emulatore.
* **Configurazione Debug & adbReverse:** il build type `debug` punta a `http://127.0.0.1:8080/`. Eseguire `./gradlew adbReverse` (che lancia `adb reverse tcp:8080 tcp:8080`) per instradare le richieste dal loopback dell'emulatore all'Ingress Traefik sull'host.
* **Versionamento:** `version.properties` è l'unica fonte di verità per `versionCode` e `versionName`. Non modificare manualmente questi valori in `build.gradle.kts`.

---

## 5. Orchestrazione Kubernetes & Kustomize (`k8s/`)

La configurazione Kubernetes risiede in `san-martino-services/k8s/` ed è strutturata in:
* `k8s/base/`: Namespace `sanmartino` (PSA `restricted`), Deployment e Service per le 7 app, Ingress Traefik.
* `k8s/components/`:
  * `infra-local`: 5 PostgreSQL 17-alpine + Kafka KRaft.
  * `keycloak`: Keycloak 26.8.2 con realm export.
  * `vault-dev` / `vault-k8s-auth`: Vault dev con sidecar seed in locale; autenticazione nativa con ServiceAccount token proiettato in cloud.
  * `observability`: Prometheus v3.2 (service discovery su pod), Loki 3.4, Grafana 11.6, Grafana Alloy v1.21 (Deployment, no hostPath).
* `k8s/overlays/local/`: Configurazione per cluster locale Kind (`sanmartino.local`).
* `k8s/overlays/cloud/`: Configurazione per cloud reale (cert-manager TLS, HPA, PDB, repliche, database/Kafka gestiti).

### Comandi di Gestione Ambiente Locale
```bash
./scripts/k8s-dev-up.sh                           # Avvia Kind, build/load immagini, distribuisce i manifest in 3 fasi
./scripts/k8s-dev-down.sh                         # Spegne il cluster locale
./scripts/k8s-dev-down.sh --purge                 # Spegne il cluster ed elimina i secret generati
```

### Risoluzione Host Locali (`/etc/hosts`)
```
127.0.0.1 sanmartino.local grafana.sanmartino.local
```

---

## 6. Convenzioni Git Flow & CI/CD

Tutti i repository condividono la stessa impostazione metodologica:

* **Modello di Branching & Naming:** `git flow`.
  * `develop`: branch di sviluppo e integrazione.
  * `master`: branch di produzione (**non** `main`).
  * **Naming dei Branch (OBBLIGATORIO):** Ogni branch deve includere il numero della issue a tre cifre:
    * `feature/<NNN>-<descrizione-kebab-case>` (es. `feature/043-audit-log-business`)
    * `bugfix/<NNN>-<descrizione-kebab-case>` (es. `bugfix/044-fix-token-expiration`)
    * `release/<versione>` o `hotfix/<versione>`
  * `feature/*` e `bugfix/*` -> PR verso `develop`.
  * `release/*` e `hotfix/*` -> PR verso `master` con back-merge su `develop`.
* **Convenzione Commit (OBBLIGATORIA):**
  Ogni commit deve SEMPRE iniziare con il prefisso `(#NNN)` corrispondente al numero della issue/task:
  ```
  (#NNN) descrizione all'infinito inglese
  ```
  *Esempi:*
  * `(#043) implement business audit logging library and aspect in common-observability`
  * `(#032) add the cloud overlay for production Kubernetes`
  Un commit = un singolo cambiamento logico.
* **Release & Smart Versioning:** Al merge su `master`, il workflow `release.yml` calcola la versione semantica (da nome branch o label PR `major`/`minor`), tagga la release e aggiorna `develop`.
* **Deploy Artefatti:**
  * **Automatico:** push su `master` e tag `v*` pubblicano immagini/APK su GitHub Packages (GHCR).
  * **Manuale:** push su branch di sviluppo abilitano il deploy manuale previa approvazione nell'ambiente `san-martino-registry`.

---

## 7. Regole di Lavoro per Agenti AI

1. **Mai committare segreti o credenziali:** il `.gitignore` esclude `.env*`, `*.pem`, `*.key`, credenziali Firebase e cartelle `**/secrets/`. Mantenere rigorosamente questa protezione.
2. **Nessun git push automatico:** eseguire commit locali chiari e descrittivi; non inviare push verso i remoti senza esplicita autorizzazione dell'utente.
3. **Verificare sempre i risultati:** eseguire comandi e test reali per validare il codice prima di dichiarare completato un task.
4. **Trasparenza:** distinguere sempre tra ciò che è stato verificato in locale e ciò che richiede verifica in ambiente reale/CI.
