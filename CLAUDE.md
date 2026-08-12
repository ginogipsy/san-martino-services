# CLAUDE.md — istruzioni per agenti su questo repo

Monorepo Maven multi-modulo del backend "Le cantine di San Martino" (Arce, FR). Java 26, Spring Boot 4.0.6, 6 servizi: `events-service`, `stands-service`, `auth-service`, `gateway`, `saga-orchestrator`, `notifications-service`.

Le regole di codice stanno in **`docs/CODING_STANDARDS.md`** — leggerlo prima di scrivere codice Java. La pipeline è documentata in **`docs/ci-cd.md`**. (`docs/CLAUDE.md` è invece il riepilogo di stato del progetto, non un file di istruzioni.)

## Comandi

```bash
docker compose up -d vault vault-init   # PREREQUISITO dei test, vedi sotto
./mvnw clean verify                     # build + test dell'intero reactor
./mvnw -pl :<service> -am -DskipTests package   # un solo modulo
```

## Cose non ovvie, da sapere prima di toccare qualcosa

**I test non partono senza Vault.** Ogni `application.yaml` dichiara `spring.config.import: vault://` *non opzionale*, e i datasource usano placeholder (`${EVENTS_DB_URL}`, …) risolti dai secret KV. Senza `docker compose up -d vault vault-init` il contesto Spring non si avvia. I database invece non servono: li fornisce Testcontainers.

**Testcontainers è rotto su questa postazione Windows.** Docker Desktop 29.6.2 risponde HTTP 400 con un `Info` vuoto su entrambe le named pipe. Tre ipotesi già verificate e smentite (pipe alternativa, bump Testcontainers, `DOCKER_API_VERSION`) — dettagli in `docs/ci-cd.md`. Non è un problema della CI: su `ubuntu-latest` funziona. **Conseguenza: non promettere che un test Testcontainers passi basandosi su una run locale — in locale non gira.** Validare in CI.

**I sorgenti generati non si toccano.** La fonte di verità dei contratti è `api/*.yaml`; `openapi-generator` produce le interfacce in `target/generated-sources/openapi/`, escluse da `.gitignore`. Per cambiare un contratto si modifica lo YAML e si rigenera.

**`docs/api/` è generato.** Non editarlo a mano. Se cambi `api/*.yaml`, rigeneralo con la catena esatta in `docs/ci-cd.md` (redocly + prettier, versioni pinnate) e committalo nello stesso commit, altrimenti il gate della CI fallisce.

**Le versioni dei tool nei workflow sono pinnate a valore esatto**, non a range: l'output di Redoc cambia tra versioni e genererebbe commit di rumore. Se aggiorni `REDOCLY_VERSION` o prettier, rigenera `docs/api/` nello stesso commit.

## Convenzioni

**Commit:** `(#NNN) descrizione all'infinito inglese`, dove `NNN` è il numero del task.

```
(#003) added GitHub Actions CI/CD pipeline for the Maven monorepo
```

Un commit = un cambiamento logico. Se scopri un bug strada facendo, va in un commit separato con il *perché* nel corpo.

**Branching: git flow.** `feature/*` → PR verso `develop`; `release/*` e `hotfix/*` → `master` con back-merge su `develop`. Il branch di produzione è **`master`**, non `main`. Il publish su GHCR avviene solo su `master`; il commit automatico delle docs solo su `develop`.

## Regole di lavoro

- **Mai committare segreti**, nemmeno per il dev locale, nemmeno con un commento che dice di non farlo. Il `.gitignore` è rigoroso su chiavi, `.env` e credenziali Firebase: mantenerlo così. In `application.yaml` ci sono ancora token hardcoded — sono debito noto, non un precedente da seguire.
- **Non fare push senza richiesta esplicita.** Il remote è SSH; la chiave della postazione non è registrata su GitHub, quindi il push va comunque fatto dall'utente (IntelliJ o GitHub Desktop funzionano).
- **Verificare, non supporre.** Prima di dichiarare qualcosa funzionante, eseguirlo. Se non è verificabile in locale (vedi Testcontainers), dirlo esplicitamente invece di lasciarlo intendere.
- **Le ipotesi sbagliate si ritirano.** Se una modifica era motivata da una diagnosi rivelatasi falsa, va annullata, non lasciata dentro perché "comunque non fa male".
