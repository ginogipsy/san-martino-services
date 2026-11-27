# Roadmap & Backlog — Ecosistema San Martino

Documento di tracciamento delle prossime evoluzioni tecniche, didattiche e di business pianificate per l'ecosistema **"
Le Cantine di San Martino"** (`san-martino-services`, `san-martino-user-panel`, `sanmartinoapp`).

---

## 1. Porting Didattico Spring Boot -> Quarkus (In Corso)

- [ ] **Strategia di migrazione & architettura:**
    - Analisi comparativa tra migrazione graduale per microservizio (*Strangler Fig*), *Spring Compatibility Extensions*
      e **Quarkus nativo puro** (Jakarta CDI `ArC`, Jakarta REST, Hibernate ORM with Panache), valutando costi, rischi e
      scelte di business.
- [ ] **Percorso pratico step-by-step:**
    1. Setup Maven Quarkus BOM, generazione API-First OpenAPI e configurazione `application.properties` / profili
       `%dev`, `%test`, `%prod`.
    2. Dependency Injection build-time con **ArC (`@ApplicationScoped`, `@Inject`)** vs reflection runtime di Spring.
    3. Persistenza dati con **Hibernate ORM with Panache** + **Flyway** + Quarkus Dev Services / Testcontainers.
    4. Integrazione **Quarkus Vault** + autenticazione **OIDC / SmallRye JWT** con Keycloak.
    5. Messaggistica **SmallRye Reactive Messaging (Kafka)**, fault tolerance (**SmallRye Fault Tolerance**) e
       osservabilità (**Quarkus OpenTelemetry + Micrometer**).
    6. Test con `@QuarkusTest` / `@QuarkusIntegrationTest` e confronto startup/RAM JVM vs Native Image GraalVM.

---

## 2. Gestione Economica di Piatti, Bevande e Incassi (Business)

- [ ] **Listino prezzi multi-edizione (`menu_items`):**
    - Introduzione del prezzo unitario (`BigDecimal` / `NUMERIC(10,2)` o centesimi interi) per piatti e vini,
      differenziabile per singola edizione della festa mantenendo il legame storico tramite `canonical_item_id`.
    - Visualizzazione prezzi su App Android (`MenuScreen`, scheda cantina) e gestione nel Web Panel (`admin-stand`).
- [ ] **Gestione Cassa, Totale Ordine e Pagamenti:**
    - Storicizzazione dello *snapshot* del prezzo unitario in `stand_order_items` all'emissione dell'ordine in
      **Postazione Cassa**, calcolo automatico di `total_amount` e resto.
    - Tracciamento metodo di pagamento (`CASH`, `POS`, `TOKEN` gettoni cantina, `FREE` omaggio staff) ed eventuali menu
      degustazione/combo.
- [ ] **Dashboard Incassi & Rendicontazione Cantina:**
    - Reportistica economica in tempo reale e storica per cantina (`ROLE_STAND_OWNER` / `ROLE_ADMIN`): incasso totale,
      split cibo/bevande, top piatti per ricavo, confronto tra edizioni ed export CSV/PDF di chiusura serata.

---

## 3. Qualità del Codice & Copertura Test (CI/CD & Portfolio)

- [ ] **Test Coverage con JaCoCo (`jacoco-maven-plugin`):**
    - Generazione report XML aggregati di copertura test su tutti i moduli (con esclusione dei package generati da
      OpenAPI).
- [ ] **Integrazione SonarQube Cloud (workflow non bloccante):**
    - Affiancare al controllo autonomo SpotBugs + PMD un workflow GitHub Actions dedicato a **SonarQube Cloud** (piano
      gratuito *Open Source* per repository pubblici) per esporre badge e metriche di *Quality Gate*, *Coverage*,
      *Security Hotspots* e *Technical Debt*.

---

## 4. Provisioning Hardware ESP32 da Browser (IoT)

- [ ] **Web Flasher / Configuratore USB nel Pannello Cantina (`Web Serial API`):**
    - Pulsante *"Carica su dispositivo"* nella configurazione Pulsantiera di `san-martino-user-panel` per scrivere via
      USB sull'ESP32 il firmware e i parametri della cantina (`standId`, `deviceToken`, `stationRole`, Wi-Fi), definendo
      la policy di autorizzazione (`ROLE_ADMIN` / `ROLE_IOT_INSTALLER` vs `ROLE_STAND_OWNER`).
