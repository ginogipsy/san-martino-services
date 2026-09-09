# CI/CD — GitHub Actions

I workflow in `.github/workflows/`:

| File | Scopo |
|---|---|
| `ci-cd.yml` | build, test, immagini OCI, publish su GHCR, docs OpenAPI, release |
| `release.yml` | tag git, GitHub Release, bump della versione su `develop`. **Non ha un trigger proprio**: è un `workflow_call` invocato come ultimo job di `ci-cd.yml` |
| `deploy.yml` | pubblicazione **a click** delle immagini di un branch su un registry. Doppio trigger: `workflow_call` da `ci-cd.yml` (il pallino dentro la run) e `workflow_dispatch` (bottone nel tab Actions) |
| `qodana_code_quality.yml` | analisi statica JetBrains Qodana |

## Allineamento a git flow

```
feature/*  ──PR──►  develop  ──►  release/*  ──PR──►  master  ──tag──►  GHCR
                       ▲                                  │
                       └────────── back-merge ────────────┘
```

| Evento | build & test | immagini OCI | push GHCR | docs | Release/Tag | Deploy a click |
|---|---|---|---|---|---|---|
| PR → `develop` / `master` / `release/**` | ✅ | ✅ build | ❌ | gate bloccante | ❌ | ❌ (usa "Run workflow") |
| push su `develop` | ✅ | ✅ build | ❌ | **commit automatico** | ❌ | ✅ pallino |
| push su `release/**` / `hotfix/**` | ✅ | ✅ build | ❌ | warning non bloccante | ❌ | ✅ pallino |
| push su `master` | ✅ | ✅ build | ✅ | warning non bloccante | **tag + Release + bump**, solo a valle del verde | ✅ pallino |

### Release (`release.yml`, chiamato da `ci-cd.yml`)

**La versione non viene indovinata dalla pipeline: la decide chi apre il branch di release.** Su `release/vX.Y.Z` (o `hotfix/vX.Y.Z`) si esegue

```bash
./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
```

e si committa. Così la versione entra nel diff della PR verso `master` e si rivede come qualsiasi altra modifica. Per una patch che parte direttamente da `develop` non serve fare niente: il pom è già alla patch successiva, scritta dal bump della release precedente.

Al merge su `master`, il job `release` gira **dopo** `build-test` e `build-images` (`needs`), quindi:

1. **Controlla che il tag non esista.** Un tag di release è immutabile: se `vX.Y.Z` c'è già, la run si ferma. Il caso tipico è un bump su `develop` fallito, che rimanderebbe la stessa versione al giro dopo.
2. **Controlla che il nome del branch e i pom concordino.** Se il merge arriva da `release/*` o `hotfix/*`, la versione nel nome deve essere quella dei pom. Da un altro branch il confronto non è possibile e si usa il pom, con un `notice` nel log.
3. **Controlla che la versione ricevuta sia quella del pom** a quel commit.
4. **Crea tag e GitHub Release** con le note generate.
5. **Porta `develop` alla patch successiva**, mai a scendere: se `develop` è già a una versione superiore (un hotfix rilasciato mentre `develop` sta su una minor successiva) il bump viene saltato.

Il bump riscrive tutti e otto i pom su `develop`: **chi ha un feature branch aperto se lo ritrova in conflitto** al primo merge successivo, e va ribasato. È già costato un build rotto (commit `f3706ae`).

#### Perché non è più un workflow a sé

`release.yml` partiva su `push: master`, cioè **in parallelo** a `build-test`: tag e GitHub Release nascevano anche da una run rossa. E la versione veniva calcolata per conto proprio da nome del branch ed etichette della PR, senza mai essere scritta nei pom — così il tag e l'artefatto divergevano:

| tag | `project.version` a quel commit | immagine su GHCR |
|---|---|---|
| `v1.0.0` | 1.0.0 | `:1.0.0` |
| `v1.1.0` | **1.0.1** | `:1.0.1` |
| `v1.1.2` | **1.1.1** | `:1.1.1` |
| `v1.1.3` | 1.1.3 | `:1.1.3` |

Due release su quattro. Al tag `v1.1.2` corrisponde l'immagine `:1.1.1`, e nessuna `:1.1.2` esiste: un rollback a quel tag non trova l'artefatto. Ora la versione è lo stesso output (`build-test.outputs.version`) che tagga le immagini, quindi tag, pom e immagine non possono più divergere.

Rimossi nello stesso giro: il `git push --force` sui tag (un re-run spostava un tag già pubblicato), l'input `manual_version` (una versione forzata a mano ricreerebbe la divergenza) e la lettura delle etichette `major`/`minor`, che era la parte che indovinava. Per rilanciare una release fallita si usa *Re-run failed jobs* sulla run.

Il filtro `paths` del trigger vale anche per la release: un merge su `master` che tocca solo `docs/**` non produce immagini e quindi non produce release.

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

## Job 4 — Deploy manuale su registry (`deploy.yml`)

L'equivalente del **terzo pallino** di GitLab: build automatica, test automatici, e poi un bottone che rilascia l'immagine.

**Su GitHub Actions quel pallino non esiste.** Non c'è `when: manual`, non c'è un play button per singolo job. I due sostituti sono entrambi in `deploy.yml`, così condividono la stessa logica di pubblicazione:

| | Cos'è | Dove si configura |
|---|---|---|
| **Environment approval** | Un job della **stessa run** resta in *pending* col bottone **Review deployments** → Approve. È il gemello del manual job. | `environment: nexus` in YAML **+** Settings → Environments (questa parte **non** è YAML) |
| **`workflow_dispatch`** | Bottone **Run workflow** nel tab Actions, branch dal dropdown. È una run *nuova*, non un pallino di quella esistente. | Solo YAML |

Il pallino nasce dal job `gate` (`needs: [build-test, build-images]` sul chiamante), quindi è premibile **solo con i primi due verdi** — la stessa semantica del manual job. Il gate non fa lavoro utile: esiste per tenere `environment:` su un job singolo, così l'approvazione è **un click solo** e poi partono i sei push in parallelo. Se stesse sul job a matrice, GitHub aprirebbe sei deployment.

Su `workflow_dispatch` il gate è saltato: premere "Run workflow" **è già** l'atto manuale.

### Prerequisito, da fare a mano prima del merge

1. Settings → **Environments** → New environment → `nexus`
2. **Required reviewers** → aggiungi te stesso → Save

> **Perché prima.** Un `environment:` che non esiste viene creato da GitHub **implicitamente e senza protezioni**: in quel caso il deploy non sarebbe manuale, partirebbe da solo a ogni push. Per questo lo step `Require a human approval` interroga `GET /repos/{owner}/{repo}/actions/runs/{run_id}/approvals` e **fallisce** se nessuno ha approvato — un errore rumoroso invece di una pubblicazione silenziosa.

I required reviewers sono gratuiti perché il repo è **pubblico**; su un repo privato servirebbe GitHub Pro/Team. Il bottone "Run workflow" compare nella UI solo quando `deploy.yml` è arrivato su **`master`** (il default branch): è una regola di GitHub, non una scelta nostra.

### Destinazione configurabile

Nessun placeholder finto nello YAML. Settings → Secrets and variables → Actions:

| | Nome | Esempio | Se assente |
|---|---|---|---|
| Variable | `DEPLOY_REGISTRY` | `nexus.example.com:8891` | fallback su `ghcr.io` |
| Variable | `DEPLOY_NAMESPACE` | `sanmartino` | fallback su `<owner>/sanmartino` |
| Secret | `DEPLOY_USERNAME` | `ci-publisher` | **errore** se `DEPLOY_REGISTRY` è valorizzata |
| Secret | `DEPLOY_PASSWORD` | token Nexus | **errore** se `DEPLOY_REGISTRY` è valorizzata |

Finché le variabili non ci sono si pubblica su GHCR con `GITHUB_TOKEN`: il flusso funziona già oggi, senza un Nexus. Quando il Nexus arriverà basta valorizzarle — **nessuna modifica allo YAML**. Un namespace vuoto è legittimo: i repository Docker di Nexus sono spesso legati a una porta, con l'immagine nella radice.

### Perché ricostruisce invece di promuovere

`ci-cd.yml` pubblica su GHCR **solo** su push diretto a `master`: fuori da `master` le sei immagini vengono costruite e buttate via, quindi non c'è nulla da promuovere. `deploy.yml` rifà `spring-boot:build-image` sul commit selezionato. Il prezzo è una seconda passata di buildpacks; il guadagno è che la semantica di publish esistente resta intatta e nessuna immagine spuria finisce su GHCR a ogni push.

La catena è la stessa del [Job 2](#job-2--immagini-oci) — build locale, `docker tag`, `docker push` — per lo stesso motivo documentato là. Anche il nome dell'immagine sorgente non è riscritto qui: `version` e `docker.image.prefix` arrivano come input da `build-test`, cioè **una lettura sola del pom per tutta la run**, e in `workflow_dispatch` vengono letti con `help:evaluate`.

### Tag

```
<registry>/<namespace>/<service>:1.1.4-release-v1.2.0-06f4292   # immutabile
<registry>/<namespace>/<service>:release-v1.2.0                 # mobile, ultimo deploy del branch
```

Lo slug del branch non è un semplice `/`→`-`: un tag OCI vuole primo carattere alfanumerico o `_`, poi `[a-zA-Z0-9._-]`, massimo 128 caratteri. Verificato su casi costruiti (`hotfix/UPPER_Case`, ref non-ASCII, nomi da 200 caratteri, `///`): nessun tag invalido in output.

**`latest` non viene mai toccato.** Quel tag segue `master` ed è responsabilità di `ci-cd.yml`; un deploy di branch che lo spostasse lo renderebbe un puntatore a qualunque cosa qualcuno abbia premuto per ultimo.

### Cosa aspettarsi nella UI

Una run col gate non approvato **resta gialla** finché non si preme Approve o non la si cancella: è lo stesso comportamento di una pipeline "blocked" di GitLab, non un guasto. Il job `deploy` non è in `needs` di nessun altro, quindi non blocca né la release né le docs. Su `develop`, però, ogni push lascia una run in attesa: si accumulano, e vanno cancellate a mano se non interessano.

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

- **Il deploy manuale non è mai stato osservato su una run reale.** Lo schema dei tag è stato provato in locale su casi costruiti e i due YAML sono validati contro lo schema di Actions, ma l'environment, l'endpoint `approvals` e il deployment esistono solo lato GitHub: si vedono al primo push dopo aver configurato i required reviewers. Da guardare a quel giro: che il job compaia in *pending* col bottone, che senza reviewer configurati fallisca invece di pubblicare, e che un solo Approve liberi tutti e sei i job.
- **Il flusso di release non è mai stato osservato su una run reale.** I tre controlli e il bump monotono sono stati eseguiti in locale su casi costruiti (tag esistente, branch incoerente, hotfix con `develop` più avanti, `1.9.9 → 1.9.10`), e i due YAML sono validati sintatticamente; ma il workflow intero gira solo al primo merge su `master`. Da guardare a quel giro: che il job `release` compaia come chiamata al reusable workflow, che il tag nasca **dopo** il push delle sei immagini, e che il bump su `develop` non sbatta contro la branch protection.
- **Le due release già incoerenti restano tali.** `v1.1.0` (pom 1.0.1) e `v1.1.2` (pom 1.1.1) non vengono riscritte: i tag pubblicati non si spostano, e su GHCR manca l'immagine `:1.1.2`. Se serve un rollback a una di quelle versioni, l'immagine da cercare è quella con la versione del pom, non quella del tag.
- **Kafka nei test non è ancora abilitato.** L'ambiente CI lo supporta, ma `saga-orchestrator` e `notifications-service` non hanno `org.testcontainers:kafka` in scope `test`. Al momento non hanno classi di test, quindi nulla fallisce.
- **Lint OpenAPI non bloccante** (`continue-on-error: true`): le spec non sono ancora allineate al ruleset `recommended` di Redocly. Togliere il flag per trasformarlo in un gate.
- **`build-images` gira anche sulle PR** (6 immagini). Intercetta le rotture dei buildpack prima del merge, al costo di tempo runner. Per limitarlo a `master`, aggiungere una condizione `if` al job.
