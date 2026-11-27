# Motore di Ricerca Intelligente Piatti & Cantine (`/v1/stands/search`)

Questo documento descrive l'architettura, l'algoritmo di scoring e la roadmap di evoluzione (`TODO`) del motore di ricerca per piatti, bevande e cantine implementato in `stands-service`.

---

## 1. Funzionalità Attualmente Implementate (`#052`, `#055` & `#057`)

L'endpoint `GET /v1/stands/search` (`q`, `kind`, `onlyAvailable`, `lat`, `lng`) esegue una ricerca cross-cantina combinando:

1. **Normalizzazione Testuale & Stopwords**:
   - Rimozione di accenti diacritici (`NFD`), punteggiatura e filtro automatico di articoli/preposizioni (`"al"`, `"con"`, `"di"`, `"la"`, ecc.), così una ricerca come `"pasta al sugo"` viene scomposta nei token significativi `["pasta", "sugo"]`.
2. **Stemmer Morfologico Italiano (Singolare / Plurale)**:
   - Unificazione automatica della radice tra singolare e plurale (`"castagna"` ↔ `"castagne"`, `"pizzella"` ↔ `"pizzelle"`, `"fungo"` ↔ `"funghi"`).
3. **Tolleranza agli Errori di Battitura (Fuzzy Search — Damerau-Levenshtein)**:
   - Riconosce refusi di digitazione da smartphone fino a distanza `1` (parole di 4–6 lettere) o `2` (parole $\ge 7$ lettere), incluse trasposizioni di lettere adiacenti (es. `"castange"` → `"castagne"`, `"amatricciana"` → `"amatriciana"`, `"guancale"` → `"guanciale"`).
4. **Dizionario Sinonimi Gastronomici & Auto-Arricchimento Keywords**:
   - Espansione semantica delle categorie culinarie (`pasta` ↔ `primo, bucatini, fettuccine, sagnozzi, rigatoni`; `carne` ↔ `secondo, salsiccia, arrosticini, porchetta`; `castagne` ↔ `caldarroste, brace`, ecc.).
   - Quando un gestore crea o aggiorna un piatto (`addMenuItem` / `updateMenuItem`), il sistema arricchisce automaticamente le `keywords` estraendo i termini chiave dal nome/descrizione e applicando il dizionario sinonimi.
5. **Sistema di Punteggio Multi-Fattore (`score`)**:
   - Match frase intera su nome piatto (`+150`) o keyword (`+130`).
   - Match per singolo token su nome piatto o keyword (`+100` esatto, `+75/+80` prefisso/stem, `+50/+55` fuzzy typo).
   - Match tramite espansione sinonimi (`+65`) o descrizione IT/EN (`+40`).
   - **Bonus Multi-Token (`+40`)**: se tutti i termini cercati compaiono nello stesso piatto.
   - **Bonus Disponibilità (`+15`)**: premia i piatti con porzioni disponibili (`availablePlates > 0`).
   - **Bonus Popolarità Click (`0..+25`)**: premia i piatti più cliccati dagli utenti (`MENU_ITEM_CLICK` con scala
     logaritmica $\min(25, \text{round}(\ln(1 + \text{clicks}) \times 6))$).
   - **Bonus Recensioni Approvate (`-10..+20`)**: premia i piatti con voto medio approvato elevato
     (`round((avgRating - 3.0) * 10)`).
   - **Bonus Vicinanza GPS (`0..+20`)**: formula di Haversine rispetto alle coordinate opzionali `lat`/`lng` dell'utente.
6. **Tracciamento Analitico Ricerche & Click (`#055`)**:
    - **Log automatico ricerche (`stand_search_logs`)**: ogni ricerca con query normalizzata di almeno 2 caratteri viene
      salvata automaticamente con `raw_query`, `normalized_query`, `kind_filter`, `only_available` e `results_count`.
    - **Tracciamento click e interazioni (`POST /v1/stands/analytics/interactions` → `stand_interaction_events`)**:
      registra i click sui singoli piatti (`MENU_ITEM_CLICK`) e le aperture di menu cibo (`STAND_FOOD_OPEN`), bevande
      (`STAND_DRINK_OPEN`), descrizione (`STAND_DESCRIPTION_OPEN`) e mappa (`STAND_MAP_OPEN`).
    - **Riepilogo per Dashboard (`GET /v1/stands/analytics/summary?limit=10`)**: restituisce `topSearchedQueries`,
      `zeroResultQueries` (piatti cercati dai visitatori ma assenti alla festa), `topClickedMenuItems` e
      `topClickedStands`.
7. **Recensioni & Moderazione (`#057`)**:
    - **Invio e consultazione recensioni (`GET/POST /v1/stands/reviews`)**: supporta recensioni su `STAND`, `MENU_ITEM`
      ed `EVENT` (create in stato `PENDING`).
    - **Flusso di approvazione Moderatori (`PUT /v1/stands/reviews/{reviewId}/moderation`)**: i Moderatori/Admin
      approvano (`APPROVED`) o rifiutano (`REJECTED`) le recensioni prima della pubblicazione sull'app mobile.

---

## 2. Roadmap & TODO — Evoluzioni Future Non Ancora Implementabili

Le seguenti ottimizzazioni richiedono i dati dei moduli successivi della roadmap o estensioni infrastrutturali dedicate:

- [x] **COMPLETATO (`#055` — Step 3 — Analytics & Popolarità di Ricerca)**:
    - Integrato nel calcolo dello `score` un peso di popolarità dinamico basato sui piatti più cliccati dagli utenti
      (`stand_interaction_events`) e persistenza automatica delle ricerche (`stand_search_logs`).
- [x] **COMPLETATO (`#057` — Step 4 — Recensioni & Rating Medio)**:
    - Aggiunto al ranking un boost proporzionale al voto medio approvato dai moderatori ($\text{Rating} \in [1..5]
      \star$) per i piatti recensiti (`festival_reviews`).
- [x] **COMPLETATO (`#058` — Step 5 — Cantine Preferite / "Dove voglio andare")**:
    - Persistenza e sincronizzazione delle cantine preferite dell'utente (`user_favorite_stands`,
      `GET/PUT/DELETE /v1/stands/favorites`) con supporto offline-first e filtro dedicato sull'app Android.
- [x] **COMPLETATO (`#060` — Partecipazione Multi-Evento delle Cantine, Menu per Edizione & Consultazione Storica in
  Sola Lettura)**:
    - **Modello Multi-Edizione**: consentito a una stessa cantina (identità storica permanente) di partecipare a più
      eventi/edizioni annuali (`stand_event_participations`), configurando per ciascun evento dettagli specifici (numero
      piazzola,
      posizione GPS, descrizione dell'edizione) e un menu dedicato di piatti e vini (`menu_items.event_id`).
    - **Continuità Storica delle Recensioni**: le recensioni della cantina si perpetuano attraverso le
      diverse edizioni, così come le recensioni di piatti e vini quando la cantina ripropone gli stessi piatti/vini in
      edizioni successive (`menu_items.canonical_item_id` e aggregazione cross-edizione in `StandService`).
    - **Navigazione Edizioni Passate in Sola Lettura sull'App**: resi esplorabili sull'app mobile anche tutti gli
      eventi passati con la stessa identica struttura dell'evento corrente (elenco cantine partecipanti a
      quell'edizione → menu piatti e vini di quell'anno → recensioni storiche di cantine, piatti e vini), in modalità
      **sola lettura** (`readOnly = true`, senza possibilità di inviare nuove recensioni su edizioni concluse).
- [ ] **TODO (Perfezionamento Recensioni — Regola "Ultimo Commento Approvato per Utente")**:
    - Mantenere lo storico completo di tutti i commenti inviati su DB (`festival_reviews`), ma fare in modo che per
      ciascun autore (`author_user_id` / `author_username`) e target (`target_type`, `target_id`) **solo l'ultima
      recensione in stato `APPROVED`** concorra al calcolo della media voti e venga mostrata pubblicamente.
- [ ] **TODO (Estensione Completa & Uniforme Recensioni a Cantine, Piatti e Vino/Bevande)**:
    - Rifinire ed estendere in modo uniforme il flusso di recensione, rating medio e moderazione su tutte le categorie
      gastronomiche: Cantine (`STAND`), Piatti (`MENU_ITEM` con `kind=FOOD`) e Vino/Bevande (`MENU_ITEM` con
      `kind=DRINK`), oltre agli Eventi.
- [ ] **TODO (Miglioramento Visualizzazione Recensioni sull'App Android)**:
    - Perfezionare l'interfaccia grafica delle recensioni sull'app mobile (badge voto medio direttamente sulle card di
      cantine/piatti/vini, layout del dialog recensioni più ricco e leggibile, indicatori visivi della propria ultima
      recensione).
- [ ] **TODO (Configurazione Keycloak Completa & Superadmin `ROLE_MEZZ`)**:
    - Includere nella configurazione esportata di Keycloak (`san-martino-export.json`) tutti i ruoli applicativi e un
      utente superadmin con ruolo speciale **`ROLE_MEZZ`** (o `MEZZ`), abilitato ad assegnare qualsiasi ruolo, creare
      utenti direttamente dal pannello web e gestire in modo completo stands ed eventi.
- [x] **COMPLETATO (`#059` — Step 6 — Pulsantiera IoT 10 Slot, 3x ESP32, Coda Ordini & Tempo di Attesa Live)**:
    - Integrata la pulsantiera a 10 slot (`1..10`) con 3 postazioni sincronizzate (**Cassa**, **Cucina**,
      **Consegna**) + 5 display 7-segmenti TM1637, gestione granulare dei permessi per postazione (`OWNER`, `CASHIER`,
      `KITCHEN`, `DELIVERY`, `VIEWER`) e calcolo live della coda attiva (`activeOrdersCount`,
      `currentServingOrderNumber`, `estimatedWaitMinutes`) con penalità automatica per code molto lunghe nel motore di
      ricerca.
- [ ] **TODO (Database Scaling — PostgreSQL `pg_trgm` + `unaccent` + `tsvector` GIN Index)**:
  - Quando il catalogo storico supererà migliaia di piatti su più edizioni, spostare il calcolo di similarità e full-text search direttamente in SQL nativo tramite indici `GIN` su `to_tsvector('italian', ...)` e `pg_trgm`.
- [ ] **TODO (Ricerca Semantica — PostgreSQL `pgvector` / LLM Embeddings)**:
  - Supportare ricerche conversazionali libere (es. *"qualcosa di caldo vegetariano da mangiare passeggiando"*) generando embedding vettoriali dei piatti al salvataggio e interrogandoli con similarità coseno su `pgvector`.


