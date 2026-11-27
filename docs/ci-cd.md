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

| File               | Scopo                                                                  |
|--------------------|------------------------------------------------------------------------|
| `ci-cd.yml`        | build, test, immagini OCI, publish su GHCR, docs OpenAPI               |
| `release.yml`      | Tag, GitHub Release, Version Bump (Maven)                              |
| `deploy.yml`       | Pubblicazione manuale/automatica delle immagini su registry            |
| `code-quality.yml` | analisi statica gratuita SpotBugs + PMD su JDK 25 (zero token esterni) |

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

Reactor completo su **JDK 25 LTS** (`temurin`), `./mvnw clean verify`.

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
- **`actions/cache/restore` (read-only) nei job matrice**: sette job paralleli che scrivono la stessa chiave si
  sovrascriverebbero a vicenda. La cache la popola **solo** `build-test`.

Uno step finale rimuove `~/.m2/repository/com/ginogipsy` prima del salvataggio: impedisce che un artifact del progetto in cache mascheri una rottura reale in una run futura.

## Job 2 — Immagini OCI

Matrice di **7 servizi** (`events-service`, `stands-service`, `auth-service`, `gateway`, `saga-orchestrator`,
`notifications-service`, `audit-service`), `fail-fast: false`. Un solo `Dockerfile`, nella radice, per tutti e sette:

```bash
docker build --build-arg SERVICE=<service> --build-arg REVISION=<versione> -t ghcr.io/ginogipsy/sanmartino/<service>:<versione> .
```

Il context è la radice del repo perché Maven ha bisogno del parent pom e di `common-observability`. Dentro:

| Stage | Immagine | Cosa fa |
|---|---|---|
| `build` | `eclipse-temurin:25-jdk` | `./mvnw -pl :<service> -am -DskipTests package`, `~/.m2` in una cache mount di BuildKit; poi `jarmode=tools extract --layers` |
| runtime | `eclipse-temurin:25-jre` | i quattro layer del jar (dipendenze → loader → snapshot → applicazione), utente `10001` non root, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` |

Il runner non ha bisogno di JDK né della cache `~/.m2`: Maven gira dentro lo stage di build. `.dockerignore` esclude `target/` — in locale contiene jar di versioni vecchie che il glob dello stage raccoglierebbe — oltre a `.git`, docs e qualunque file di credenziali.

### Java 25 LTS e Dockerfile multi-stage

Con la migrazione al task #045, il monorepo è standardizzato su **Java 25 (LTS)** (`eclipse-temurin:25`). Questa scelta assicura supporto a lungo termine dai vendor (Temurin, Zulu), aggiornamenti di sicurezza regolari e massima stabilità con Spring Boot 4 e i builder container.

## Job Release

**La versione è determinata dallo Smart Versioning** (risolta in `ci-cd.yml` e usata in modo coerente per JAR, immagini
Docker e tag Git):

1. **Branch Name**: Estrae la versione (formato `X.Y.Z`) dal branch sorgente `release/X.Y.Z` o `hotfix/X.Y.Z` (anche con
   prefisso `v`).
2. **PR Labels**: Cerca le etichette `major` o `minor` nella Pull Request mergiata.
3. **Default**: Usa `<revision>` del `pom.xml` (avanzando automaticamente alla patch libera successiva se il tag esiste
   già).

A valle del tag e della GitHub Release, il bot aggiorna `develop` incrementando `<revision>` alla patch successiva
tramite `mvn versions:set-property`. È inoltre possibile avviare manualmente una release da GitHub Actions UI
(`workflow_dispatch`).

## Job Deploy (Il "Terzo Pallino")

La pubblicazione delle immagini segue una logica ibrida per massimizzare il controllo e la velocità:

1. **Continuous Deployment (Master/Tags)**: Al push su `master` o alla creazione di un tag `v*`, il sistema pubblica le
   immagini automaticamente su GHCR (o registry custom se configurato). Il tag mobile sarà `latest` per `master` e
   `X.Y.Z` per i tag.
2. **Manual Approval (Altri branch)**: Su `develop`, `release/*` o `hotfix/*`, la pipeline si ferma al job `gate`. È
   necessario un intervento umano:
   - Cliccare su **"Review deployments"** nell'interfaccia Actions.
   - Fornire l'**Approve** per l'environment `san-martino-registry`.
   - Una volta approvato, l'immagine viene pubblicata con il **nome normalizzato del branch** (es. `develop`).

> [!CAUTION]
> **Setup Obbligatorio**: L'approvazione manuale richiede la presenza dell'environment **`san-martino-registry`** in
*Settings -> Environments* con una regola di *Required reviewers* configurata, altrimenti lo step di verifica bloccherà
il job segnalando l'assenza di approvazione umana.

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

## Analisi Statica Gratuita (`code-quality.yml`)

Esegue su JDK 25 (`temurin`) l'analisi statica gratuita tramite **SpotBugs** (`spotbugs-maven-plugin`) e **PMD**
(`maven-pmd-plugin`) senza necessità di alcun token o servizio esterno:

```bash
./mvnw test-compile pmd:pmd spotbugs:spotbugs
```

Le classi generate da `openapi-generator` (`**/api/generated/**`) sono escluse tramite configurazione in `pom.xml` e
`config/spotbugs-exclude.xml`. Il riepilogo per modulo viene pubblicato nel Job Summary di GitHub Actions e i report
completi (`pmd.html`, `spotbugsXml.xml`, `spotbugsSarif.json`) vengono caricati come artifact.

## Riprodurre la CI in locale

```bash
docker compose up -d vault vault-init      # secret KV richiesti dai test
./mvnw clean verify                        # build + test completi
docker build --build-arg SERVICE=events-service -t sanmartino/events-service:dev .
```

## Kubernetes & Kustomize

L'infrastruttura di orchestrazione risiede in `k8s/` ed è validata in CI tramite render Kustomize:

```bash
# Validazione statica dei manifest locali e cloud
kubectl kustomize k8s/overlays/local > /dev/null
kubectl kustomize k8s/overlays/cloud > /dev/null
kubectl kustomize k8s/overlays/cloud/cluster > /dev/null
```

Per l'avvio e la gestione completa dell'ambiente di sviluppo locale su cluster Kind:

```bash
./scripts/k8s-dev-up.sh       # crea il cluster, compila le immagini e distribuisce i servizi
./scripts/k8s-dev-down.sh     # elimina il cluster
```

Tutti i dettagli architetturali e operativi sono documentati in **[`docs/kubernetes.md`](./kubernetes.md)**.

