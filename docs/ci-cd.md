# CI/CD — GitHub Actions

Due workflow in `.github/workflows/`:

| File | Scopo |
|---|---|
| `ci-cd.yml` | build, test, immagini OCI, publish su GHCR, docs OpenAPI |
| `release.yml` | Tag, GitHub Release, Version Bump (Maven) |
| `qodana_code_quality.yml` | analisi statica JetBrains Qodana |

## Allineamento a git flow

```
feature/*  ──PR──►  develop  ──►  release/*  ──PR──►  master  ──tag──►  GHCR
                       ▲                                  │
                       └────────── back-merge ────────────┘
```

| Evento | build & test | immagini OCI | push GHCR | docs | Release/Tag |
|---|---|---|---|---|---|
| PR → `develop` / `master` / `release/**` | ✅ | ✅ build | ❌ | gate bloccante | ❌ |
| push su `develop` | ✅ | ✅ build | ❌ | **commit automatico** | ❌ |
| push su `release/**` / `hotfix/**` | ✅ | ✅ build | ❌ | warning non bloccante | ❌ |
| push su `master` | ✅ | ✅ build | ✅ | warning non bloccante | **Automazione Release** |

### Release Pipeline (`release.yml`)
Quando un branch di release viene mergiato su `master`:
1. **Tagging**: Crea un tag git `vX.Y.Z` basato sulla versione determinata dallo **Smart Versioning**.
2. **GitHub Release**: Crea una release su GitHub con changelog automatico.
3. **Smart Versioning Logic**:
   - **Branch Name**: Estrae la versione (formato `X.Y.Z`) dalla fine del nome del branch sorgente (es. `release/1.2.0`, `release/v1.2.0`, `hotfix/1.2.1`).
   - **PR Labels**: Cerca le etichette `major` o `minor` nella Pull Request mergiata per decidere l'incremento.
   - **Default**: Se non trova nulla, usa la versione attuale del `pom.xml`.
4. **Version Bump**: Incrementa la patch rispetto alla versione rilasciata tramite `mvn versions:set` e aggiorna `develop` (back-merge).

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

**Il publish usa `docker push`, non `spring-boot.build-image.publish=true`.** Motivo: il plugin legge le credenziali da `<docker><publishRegistry>` nel pom, che non ha user property da riga di comando — servirebbe modificare il `pom.xml` con `${env.…}`. Buildpacks carica l'immagine nel daemon locale, poi `docker/login-action` + `docker push` applica tre tag senza toccare il pom.

Tag prodotti su `master`:

```
ghcr.io/ginogipsy/sanmartino/<service>:0.0.1
ghcr.io/ginogipsy/sanmartino/<service>:sha-<short>
ghcr.io/ginogipsy/sanmartino/<service>:latest
```

Il prefisso non è hardcoded: è letto da `docker.image.prefix` del parent pom con `help:evaluate` ed esposto come job output.

> **GHCR e OIDC.** GHCR non supporta la federazione OIDC per l'autenticazione al registry: il meccanismo corretto è `GITHUB_TOKEN` con `permissions: packages: write` a livello di job. `id-token: write` servirà solo se si aggiungerà la firma keyless con cosign o `actions/attest-build-provenance`.

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

`qodana.yaml` esegue un bootstrap prima dell'analisi:

```yaml
bootstrap: ./mvnw -B -ntp generate-sources
```

Necessario: i controller implementano le interfacce prodotte da `openapi-generator`, che `.gitignore` esclude (`**/generated-sources/openapi/`). Senza questo step Qodana non risolve quei simboli e riporta centinaia di falsi positivi su `events-service`, `stands-service` e `saga-orchestrator`.

Richiede il secret **`QODANA_TOKEN`** nelle repository secrets; senza, il job fallisce per un motivo che non riguarda la qualità del codice.

## Riprodurre la CI in locale

```bash
docker compose up -d vault vault-init      # secret KV richiesti dai test
./mvnw clean verify                        # build + test completi
./mvnw -pl :events-service -am -DskipTests package spring-boot:build-image
```

### Testcontainers su Windows + Docker Desktop

Su questa postazione Testcontainers non riesce a raggiungere Docker: entrambe le named pipe rispondono **HTTP 400 con un `Info` vuoto**, etichettato `com.docker.desktop.address=npipe://\\.\pipe\docker_cli`.

Ipotesi verificate e **smentite**:

| Ipotesi | Prova | Esito |
|---|---|---|
| pipe sbagliata | `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` | stesso 400 |
| docker-java troppo vecchio (Engine 29 ha `Min API 1.40`) | bump Testcontainers 1.20.4 → 1.21.3 | stesso 400 |
| negoziazione versione API | `DOCKER_API_VERSION=1.44` | stesso 400 |

Ipotesi residua non verificata: l'impostazione *Docker Desktop → Settings → Advanced → "Allow the default Docker socket to be used"*.

**Non riguarda la CI**: su `ubuntu-latest` Testcontainers usa `/var/run/docker.sock` senza il proxy di Docker Desktop in mezzo. Conseguenza pratica: i test che dipendono da Testcontainers si validano solo in CI, finché il problema locale non è risolto.

## Punti aperti

- **`project.version` è fisso a `0.0.1`** nel parent pom. Ogni push su `master` sovrascrive i tag `0.0.1` e `latest`; solo `sha-<short>` rende le immagini tracciabili. Serve una strategia di versioning (revision property, tag-driven, o `versions:set` in fase di release).
- **Kafka nei test non è ancora abilitato.** L'ambiente CI lo supporta, ma `saga-orchestrator` e `notifications-service` non hanno `org.testcontainers:kafka` in scope `test`. Al momento non hanno classi di test, quindi nulla fallisce.
- **Lint OpenAPI non bloccante** (`continue-on-error: true`): le spec non sono ancora allineate al ruleset `recommended` di Redocly. Togliere il flag per trasformarlo in un gate.
- **`build-images` gira anche sulle PR** (6 immagini). Intercetta le rotture dei buildpack prima del merge, al costo di tempo runner. Per limitarlo a `master`, aggiungere una condizione `if` al job.
