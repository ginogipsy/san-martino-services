# Progetto San Martino - Riepilogo e Stato Attuale

Questo documento riassume l'architettura, lo stack tecnologico e lo stato di avanzamento del progetto "San Martino", un'applicazione per la gestione di eventi locali.

## 1. Visione Generale del Progetto
Applicazione per la gestione di eventi locali (ispirata al borgo di Arce) che comprende:
*   **App nativa Android:** Sviluppata in Kotlin.
*   **Front-end Web:** Angular 19 per il pannello admin (situato in una cartella speculare del repository).
*   **Ecosistema di Microservizi:** Sviluppato in Java/Spring Boot.

## 2. Stack Tecnologico & Strumenti

*   **Backend:**
    *   Java (target: Java 26)
    *   Spring Boot (target: 3.x-4.x, attualmente 4.0.6)
    *   Maven (approccio API-first con OpenAPI Generator)
    *   Lombok (reintrodotto)
*   **Database & Migrazioni:**
    *   PostgreSQL per la persistenza dei dati core.
    *   Flyway per il versioning del database.
    *   Per i test, non si userà H2, ma si seguirà un approccio più moderno.
*   **Docker & CI/CD:**
    *   Containerizzazione tramite plugin `spring-boot:build-image`.
    *   Pipeline automatizzate con GitHub Actions.
*   **Testing & Osservabilità:**
    *   Mockito, WebTestClient per i test.
    *   Grafana per i log, combinato con un approccio AOP (Aspect-Oriented Programming).
*   **Gestione Segreti:** HashiCorp Vault con `spring-cloud-starter-vault-config`.

## 3. Architettura dei Microservizi

Abbiamo optato per un approccio moderno, evitando soluzioni datate come Eureka.

*   **Spring Cloud Gateway + Resilience4j:** Per la gestione del traffico, routing e tolleranza ai guasti (circuit breaker).
*   **Saga Orchestrator:** Microservizio centralizzato per la gestione delle transazioni distribuite e future evoluzioni (statistiche di vendita, votazioni piatti/cantine).
*   **Kafka & Notifications Service:** Architettura event-driven accoppiata a Firebase Cloud Messaging (FCM) per inviare notifiche push all'app Android.
*   **Keycloak (Aggiornamento Importante):** Inizialmente pensato come servizio esterno, è stato ora inserito e integrato direttamente come modulo all'interno del repository principale.
*   **Servizi Principali Implementati/In Corso:** `event-service`, `stands-service`, `gateway`, `saga-orchestrator`, `notifications-service`, `auth-service`.

## 4. Stato di Avanzamento

*   La struttura dei sei servizi è impostata; tutti compilano (`./mvnw clean verify -DskipTests` → `BUILD SUCCESS`).
*   **Pipeline CI/CD su GitHub Actions completata** (task #003): build & test su JDK 26, immagini OCI via Buildpacks per i sei servizi, push su GHCR limitato a `master`, rigenerazione automatica del reference OpenAPI in `docs/api/`. Analisi statica con Qodana. Dettagli in [ci-cd.md](./ci-cd.md).
*   **Standard di codice formalizzati** in [CODING_STANDARDS.md](./CODING_STANDARDS.md); regole operative per gli agenti nel `CLAUDE.md` alla radice del repo.
*   **Logging & observability completata** (task #004): nuovo modulo `common-observability` (aspect AOP di logging con mascheramento dei dati sensibili, correlation id in MDC, log JSON in formato ECS), metriche Micrometer/Prometheus sui sei servizi, stack Prometheus + Loki + Promtail + Grafana nel `docker-compose.yml` con data source e dashboard provisionate. Dettagli e istruzioni di prova in [observability.md](./observability.md).
*   **Correlation id attraverso Kafka** (task #008): l'interceptor producer viene registrato dall'autoconfig sul producer factory di Boot (`interceptor.classes`, in merge con quelli del servizio), il `RecordInterceptor` è agganciato a mano nella factory custom di `notifications-service`, e `SagaEventPublisher` ripristina l'MDC nel callback della `send`. Aggiunta la route del gateway verso `saga-orchestrator`, così le saghe non si avviano più scavalcandolo. **Validato a runtime sullo stack completo**: un id generato dal gateway arriva ai log di `notifications-service` passando per Kafka, e l'header è presente sui record del topic. Procedura riproducibile in [observability.md](./observability.md) §6.

### Problemi risolti

1.  **`auth-service` non si avviava (DataSource):**
    *   Errore: `Failed to configure a DataSource: 'url' attribute is not specified…`
    *   Risolto aggiungendo `spring.datasource` e le dipendenze JPA/PostgreSQL.
2.  **`auth-service` non si avviava (Vault):**
    *   Errore: `Cannot create authentication mechanism for TOKEN…`
    *   Causa reale individuata durante il task #003, vedi punto successivo.
3.  **`vault-init` non ha mai popolato un solo secret** — e usciva con codice 0, quindi il fallimento era invisibile.
    *   Il `command:` nel `docker-compose.yml` usava uno scalare YAML *folded* (`>`), che collassa i newline: le continuazioni `\` si rompevano, ogni `vault kv put` partiva senza dati (`Must supply data`) e le righe successive venivano eseguite come comandi inesistenti. Siccome l'ultimo comando era un `echo`, il container segnalava successo.
    *   Risolto passando a uno scalare *literal* (`|`) con entrypoint esplicito e `set -e`. Verificato: i sei secret sono ora presenti sotto `secret/`.
    *   Questa è con ogni probabilità la causa dell'errore Vault al punto 2.

### Problemi noti, ancora aperti

0. **Osservabilità: la catena del correlation id è validata, il gateway resta un punto cieco.** Gateway → saga → events/stands → Kafka → `notifications-service` è stato provato a runtime (task #008). Restano aperti tre punti: il gateway non logga nulla per richiesta, quindi non si sa se il filtro `CircuitBreaker` con time limiter esegua la chiamata a valle su un thread del pool di Resilience4j perdendo l'MDC (il transito dell'header non ne dipende e funziona); lo scrape Prometheus e l'ingestione in Loki sono stati verificati solo con `events-service`; la risposta esce con `X-Correlation-Id` duplicato, perché lo impostano sia il filtro del gateway sia il servizio a valle.

1. **Testcontainers non funziona su questa postazione Windows.** Docker Desktop 29.6.2 risponde HTTP 400 con un `Info` vuoto su entrambe le named pipe; tre ipotesi verificate e smentite. Non riguarda la CI (`ubuntu-latest` usa il socket Unix). Conseguenza: i test che dipendono da Testcontainers si validano solo in CI. Dettagli e tabella delle ipotesi in [ci-cd.md](./ci-cd.md).
2. **Segreti hardcoded** in `application.yaml` (`token: my-root-token`) e in `docker-compose.yml` (`keycloak.client-secret`). Debito noto — vedi `java:S2068` in [CODING_STANDARDS.md](./CODING_STANDARDS.md).
3. ~~**`project.version` fisso a `0.0.1`**~~ — **risolto, ma mai osservato su una run reale.** `release.yml` determina la versione da nome del branch, etichette PR o input manuale, tagga il repo e incrementa la patch su `develop`; `ci-cd.yml` pubblica su GHCR tre tag per servizio (`:${VERSION}`, `:sha-<7>`, `:latest`). La versione corrente del reactor è `1.1.3`. Letto dai workflow, non verificato su un merge su `master`.
4. **Kafka nei test non abilitato**: `saga-orchestrator` e `notifications-service` non hanno `org.testcontainers:kafka` in scope `test`. Le classi di test ora esistono (`SagaEventPublisherTest`, `KafkaConsumerConfigTest`), ma sono unitarie: il transito su un broker è provato solo a mano.
5. **Smoke test `notifications-service`**: il ramo Kafka è validato end-to-end (task #008), **FCM no** — la prova è stata fatta con `sanmartino.fcm.enabled=false`, quindi il push è passato da `LoggingPushNotificationSender`.
6. **Configurazione del realm Keycloak** via `docker-compose.yml` — da valutare.

## 5. Prossimi Passi (Priorità)

1.  **Far girare la pipeline su una PR verso `develop`** e verificare le due incognite mai validate in locale: che `temurin:26` esista su `setup-java`, e che `EventsApiTest` passi con Vault più Testcontainers.
2.  **Configurare il secret `QODANA_TOKEN`** nelle repository secrets, altrimenti il job Qodana fallisce.
3.  **Rimuovere i segreti hardcoded** da `application.yaml` e `docker-compose.yml`.
4.  **Completare lo smoke test del `notifications-service`**: manca il solo invio reale via FCM, il ramo Kafka è validato.
5.  **Rigenerare `docs/api/`**: c'è drift fra gli YAML in `api/` e gli HTML generati, e il gate della CI lo blocca. Catena esatta e versioni pinnate in [ci-cd.md](./ci-cd.md).
6.  **Chiudere i punti aperti dell'osservabilità**: una riga di access log sul gateway (oggi punto cieco) e `org.testcontainers:kafka` per provare il transito in CI.

## 6. Decisioni Prese in Precedenza

*   **Search Functionality:** Inizialmente implementata lato UI nell'app Android, con possibile spostamento futuro lato backend.
*   **Keycloak:** Integrato come modulo interno, non più servizio esterno.
*   **Java Version:** Java 26.
*   **Spring Boot Version:** 4.0.6.
*   **Docker Build:** `spring-boot:build-image` plugin.
*   **CI/CD:** GitHub Actions.
*   **API Design:** API-first con OpenAPI Generator.
*   **Database Migrations:** Flyway.
*   **Testing:** Mockito, WebTestClient.
*   **Logging/Observability:** Grafana con AOP.
*   **VCS:** Git.
