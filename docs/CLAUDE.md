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

## 4. Stato di Avanzamento & Blocco Attuale

*   La struttura dei servizi principali (`event-service`, `stands-service`, `gateway`, `saga-orchestrator`) è stata impostata.
*   Stavamo implementando e configurando il `notifications-service` (con l'integrazione di Kafka e FCM).
*   **Blocco Attuale:** Eravamo nel bel mezzo dell'esecuzione di uno **smoke test** per verificarne il corretto funzionamento quando la chat si è interrotta.

### Problemi Recenti Riscontrati:

1.  **`auth-service` non si avvia (risolto il problema DataSource):**
    *   Errore precedente: `Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.`
    *   Risolto aggiungendo `spring.datasource` e dipendenze JPA/PostgreSQL.
2.  **`auth-service` non si avvia (nuovo errore Vault):**
    *   Errore: `Cannot create authentication mechanism for TOKEN. This method requires either a Token (spring.cloud.vault.token) or a token file at ~/.vault-token.`
    *   Causa: Mancanza di configurazione per l'autenticazione con HashiCorp Vault.
3.  **Configurazione Keycloak:**
    *   Domanda: È possibile configurare il realm di Keycloak direttamente nel `../docker-compose.yml`? (Il `../docker-compose.yml` allegato mostra la configurazione di base di Keycloak con PostgreSQL).
4.  **Integrazione FCM:**
    *   Necessità di rinforzare `../.gitignore` per la chiave privata Firebase.
    *   `FcmPushNotificationSender` reale che legge la chiave Firebase via variabile d'ambiente.

## 5. Prossimi Passi (Priorità)

1.  **Risolvere il problema di avvio dell'`auth-service` (Vault):** Configurare correttamente l'integrazione con HashiCorp Vault.
2.  **Completare e verificare lo smoke test per il `notifications-service`:** Assicurarsi che Kafka e FCM funzionino correttamente.
3.  **Gestione della chiave privata FCM:** Assicurarsi che non venga committata e sia letta da variabile d'ambiente.
4.  **Configurazione del realm Keycloak:** Valutare la possibilità di configurarlo via `../docker-compose.yml` o altri metodi.
5.  **Smoke test end-to-end via Saga.**

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
