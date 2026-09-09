# CI/CD — GitHub Actions

> [!IMPORTANT]
> **Configurazione Obbligatoria per il Deploy Manuale**
> Per abilitare il pulsante di approvazione (il "terzo pallino") sui branch feature, devi configurare l'ambiente su GitHub:
> 1. Vai in **Settings** -> **Environments**.
> 2. Clicca su **New environment** e chiamalo esattamente **`san-martino-registry`**.
> 3. Sotto **Deployment protection rules**, attiva **Required reviewers**.
> 4. Aggiungi il tuo account GitHub come revisore.
> 5. Clicca su **Save protection rules**.

Due workflow in `.github/workflows/`:

| File | Scopo |
|---|---|
| `ci-cd.yml` | build, test, immagini OCI, publish su GHCR, docs OpenAPI |
| `release.yml` | Tag, GitHub Release, Version Bump (Maven) |
| `deploy.yml` | Pubblicazione manuale/automatica delle immagini su registry |
| `qodana_code_quality.yml` | analisi statica JetBrains Qodana |

## Allineamento a git flow

```
feature/*  ──PR──►  develop  ──►  release/*  ──PR──►  master  ──tag──►  GHCR
                       ▲                                  │
                       └────────── back-merge ────────────┘
```

| Evento | build & test | immagini OCI | push GHCR | docs | Release/Tag | Deploy Immagine |
|---|---|---|---|---|---|---|
| PR → `develop` / `master` / `release/**` | ✅ | ✅ build | ❌ | gate bloccante | ❌ | ❌ (usa "Run workflow") |
| push su `develop` | ✅ | ✅ build | ❌ | **commit automatico** | ❌ | ✋ **Manuale** (Approve) |
| push su `release/**` / `hotfix/**` | ✅ | ✅ build | ❌ | warning non bloccante | ❌ | ✋ **Manuale** (Approve) |
| push su `master` | ✅ | ✅ build | ✅ | warning non bloccante | **Automazione Release** | 🚀 **Automatico** |
| push tag `v*` | ✅ | ✅ build | ✅ | ❌ | ❌ | 🚀 **Automatico** |

`feature/*` non è nei trigger `push`: è già coperto dall'evento `pull_request` verso `develop`. Averlo in entrambi consumerebbe due run per lo stesso commit.

## Job 1 — Build & Test

Reactor completo su JDK 26 (`temurin`), `./mvnw clean verify`.

**Vault è un requisito hard dei test.** Ogni `application.yaml` dichiara `spring.config.import: vault://` *non opzionale*, e i datasource usano placeholder (`${EVENTS_DB_URL}`, …) risolti dai secret KV. Il job riusa i servizi `vault` e `vault-init` del `docker-compose.yml` del repo — unica fonte di verità, zero duplicazione:

```bash
docker compose up -d vault vault-init
code="$(docker wait san-martino-vault-init)"   # exit code reale del seeding
```

`docker wait` sul `container_name` è preferito a `--exit-code-from`, che con `--abort-on-container-exit` spegnerebbe anche Vault.

I database **non** vengono avviati: li fornisce Testcontainers. `@ServiceConnection` produce un bean `JdbcConnectionDetails` che ha precedenza sulle property `spring.datasource.*` provenienti da Vault, quindi i test puntano sempre al container effimero.

### Cache Maven nel monorepo

```yaml
key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml', '.mvn/wrapper/maven-wrapper.properties') }}
restore-keys: ${{ runner.os }}-m2-
```

Tre scelte deliberate:

- **`actions/cache` esplicita** invece di `cache: maven` di `setup-java`, per controllare la chiave.
- **`hashFiles` su tutti i pom**: reactor unico → cache unica. Un cambio di dipendenza in un solo modulo invalida correttamente la cache condivisa; `restore-keys` recupera comunque la quasi totalità degli artifact.
- **`actions/cache/restore` (read-only) nei job matrice**: sei job paralleli che scrivono la stessa chiave si sovrascriverebbero a vicenda. La cache la popola **solo** `build-test`.

Uno step finale rimuove `~/.m2/repository/com/ginogipsy` prima del salvataggio: impedisce che un artifact del progetto in cache mascheri una rottura reale in una run futura.

## Job 2 — Immagini OCI

Matrice di 6 servizi, `fail-fast: false`.

```bash
./mvnw -pl :<service> -am -DskipTests package spring-boot:build-image
```

`-pl :artifactId -am` costruisce solo il modulo target più il parent; il goal `build-image` è no-op sui progetti `pom`, quindi il parent nel reactor non è un problema (verificato).

## Job Release

**La versione è determinata dallo Smart Versioning**:
1. **Branch Name**: Estrae la versione (formato `X.Y.Z`) dalla fine del nome del branch sorgente (es. `release/1.2.0`).
2. **PR Labels**: Cerca le etichette `major` o `minor` nella Pull Request mergiata.
3. **Default**: Usa la versione attuale del `pom.xml`.

A valle del tag, il bot aggiorna `develop` (back-merge) incrementando la patch via `mvn versions:set`.

## Job Deploy (Il "Terzo Pallino")

La pubblicazione delle immagini segue una logica ibrida per massimizzare il controllo e la velocità:

1. **Continuous Deployment (Master/Tags)**: Al push su `master` o alla creazione di un tag `v*`, il sistema pubblica le immagini automaticamente. Il tag Docker sarà `latest` per master e `X.Y.Z` per i tag.
2. **Manual Approval (Altri branch)**: Su `develop` o `feature/*`, la pipeline si ferma al job `gate`. È necessario un intervento umano:
   - Cliccare su **"Review deployments"** nell'interfaccia Actions.
   - Fornire l'**Approve** per l'environment `nexus`.
   - Una volta approvato, l'immagine viene pubblicata con il **nome del branch** (es. `feature-008-permessi`).

> [!CAUTION]
> **Setup Obbligatorio**: L'approvazione manuale richiede la presenza dell'environment **`nexus`** in *Settings -> Environments*. Deve avere una regola di *Required reviewers* configurata, altrimenti il deploy non sarà manuale ma partirà a ogni push.

## Job 3 — Docs OpenAPI

Catena, replicabile in locale:

```bash
npx @redocly/cli@1.34.2 build-docs api/<spec>.yaml -o docs/api/<spec>.html
npx @redocly/cli@1.34.2 bundle     api/<spec>.yaml -o docs/api/<spec>.bundled.yaml
npx prettier@3.3.3 --write 'docs/api/**/*.md' 'docs/api/**/*.yaml'
```

**Le versioni sono pinnate a valore esatto**, non a range. L'output di Redoc cambia tra versioni: una versione flottante produrrebbe un commit di rumore a ogni run anche senza modifiche alle spec. Verificato: due run consecutive danno file byte-identici, e l'output non contiene path o timestamp platform-specific (quindi nessun diff spurio fra Windows e i runner Linux).

**Se cambi una delle due versioni, rigenera e committa `docs/api/` nello stesso commit**, altrimenti il gate sulle PR fallisce.

### Perché solo `develop` scrive

`develop` è l'unico branch su cui il bot committa. Se scrivesse anche su `master` o `release/*`, quei branch divergerebbero da `develop` e ogni back-merge di git flow rischierebbe un conflitto su file generati. Le docs risalgono verso `master` attraverso i normali merge.

### Anti-loop

Doppia protezione contro la build ricorsiva innescata dal commit del bot:

1. `docs/**` **non** è nei `paths` di trigger → un commit di sole docs non riattiva il workflow.
2. Il messaggio di commit contiene `[skip ci]`.

## Job Qodana

Esegue un bootstrap prima dell'analisi:

```yaml
bootstrap: ./mvnw -B -ntp generate-sources
```

Necessario: i controller implementano le interfacce prodotte da `openapi-generator`, che `.gitignore` esclude (`**/generated-sources/openapi/`). Senza questo step Qodana non risolve quei simboli e riporta centinaia di falsi positivi.

Richiede il secret **`QODANA_TOKEN`** nelle repository secrets.

## Riprodurre la CI in locale

```bash
docker compose up -d vault vault-init      # secret KV richiesti dai test
./mvnw clean verify                        # build + test completi
./mvnw -pl :events-service -am -DskipTests package spring-boot:build-image
```
