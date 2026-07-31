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

1.  **Testcontainers non funziona su questa postazione Windows.** Docker Desktop 29.6.2 risponde HTTP 400 con un `Info` vuoto su entrambe le named pipe; tre ipotesi verificate e smentite. Non riguarda la CI (`ubuntu-latest` usa il socket Unix). Conseguenza: i test che dipendono da Testcontainers si validano solo in CI. Dettagli e tabella delle ipotesi in [ci-cd.md](./ci-cd.md).
2.  **Segreti hardcoded** in `application.yaml` (`token: my-root-token`) e in `docker-compose.yml` (`keycloak.client-secret`). Debito noto — vedi `java:S2068` in [CODING_STANDARDS.md](./CODING_STANDARDS.md).
3.  **URL datasource sospetto** nel secret `auth-service`: `jdbc:postgresql://localhost:5437/events/auth`, mentre il DB del container `postgres-auth` è `auth`. Il `/events/` in mezzo sembra un residuo di copia-incolla.
4.  **`project.version` fisso a `0.0.1`**: ogni push su `master` sovrascrive i tag `0.0.1` e `latest` su GHCR. Serve una strategia di versioning.
5.  **Kafka nei test non abilitato**: `saga-orchestrator` e `notifications-service` non hanno `org.testcontainers:kafka` in scope `test` (per ora non hanno classi di test).
6.  **Smoke test `notifications-service`** (Kafka + FCM) mai completato.
7.  **Configurazione del realm Keycloak** via `docker-compose.yml` — da valutare.

## 5. Prossimi Passi (Priorità)

1.  **Far girare la pipeline su una PR verso `develop`** e verificare le due incognite mai validate in locale: che `temurin:26` esista su `setup-java`, e che `EventsApiTest` passi con Vault più Testcontainers.
2.  **Configurare il secret `QODANA_TOKEN`** nelle repository secrets, altrimenti il job Qodana fallisce.
3.  **Rimuovere i segreti hardcoded** da `application.yaml` e `docker-compose.yml`.
4.  **Strategia di versioning** al posto dello `0.0.1` fisso.
5.  **Completare lo smoke test del `notifications-service`** (Kafka + FCM).
6.  **Smoke test end-to-end via Saga.**

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
