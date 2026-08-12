# Logging & Observability

Due pezzi, pensati per funzionare insieme:

1. **`common-observability`** — libreria condivisa dai sei servizi: un aspect AOP che logga
   le invocazioni dei metodi, un correlation id in MDC, log strutturati in JSON.
2. **Stack Grafana** nel `docker-compose.yml` — Prometheus (metriche), Loki (log),
   Promtail (spedizione), Grafana (UI con data source e dashboard pre-configurate).

```
                 ┌──────────────────────────────────────────┐
   richiesta ──► │ CorrelationIdFilter   → MDC + header      │
                 │ MethodLoggingAspect   → >> << !! + MDC    │  servizio Spring Boot
                 │ Actuator + Micrometer → /actuator/prometheus
                 └───────┬───────────────────────┬──────────┘
                         │ logs/<service>.log    │ scrape ogni 15s
                         │ (JSON, formato ECS)   │
                    ┌────▼─────┐            ┌────▼───────┐
                    │ Promtail │──push────► │            │
                    └────┬─────┘            │ Prometheus │
                         │                  └────┬───────┘
                    ┌────▼─────┐                 │
                    │   Loki   │◄────────────────┼──── Grafana :3000
                    └──────────┘                 │      (Loki + Prometheus)
```

---

## 1. L'aspect di logging

`MethodLoggingAspect` in `common-observability`. Nessuna configurazione lato servizio:
basta la dipendenza, l'auto-configuration fa il resto.

### Cosa intercetta

| Pointcut | Perché |
|---|---|
| `@RestController`, `@RestControllerAdvice` | il confine HTTP: input dell'utente e forma della risposta |
| `@Service` | la business logic, dove stanno le durate che contano |
| `@Logged` (classe o metodo) | tutto il resto, su richiesta |

Sempre **dentro `com.ginogipsy.sanmartino..*`** e mai dentro la libreria stessa: senza
questo vincolo l'aspect intercetterebbe anche i bean di Spring e delle librerie.

`@Component` **non** è nell'elenco di proposito: ci finirebbero mapper e classi di
configurazione, cioè rumore. Dove serve si aggiunge `@Logged` — è quello che fa
`notifications-service`, che non ha né `@Service` né `@RestController`:

```java
@Component
@Logged                       // così l'aspect vede la chiamata verso FCM
public class FcmPushNotificationSender implements PushNotificationSender { … }
```

### Cosa scrive

| Evento | Livello | Riga |
|---|---|---|
| ingresso | DEBUG | `>> EventService.findById(id=6f3a…)` |
| uscita | DEBUG | `<< EventService.findById completed in 12 ms -> EventEntity(id=6f3a…)` |
| uscita lenta (`slow-threshold`, default 1s) | WARN | `<< … completed in 1430 ms, over the 1000 ms threshold` |
| eccezione attesa (`*NotFoundException`) | WARN | `!! EventService.findById failed after 13 ms: EventNotFoundException: Event not found: …` |
| eccezione inattesa | ERROR | `!! … failed after 8 ms: IllegalStateException` **+ stack trace** |
| eccezione che risale un altro livello | DEBUG | `!! EventsController.getEvent propagated EventNotFoundException after 15 ms` |

Tre scelte non ovvie:

- **Il logger è quello della classe intercettata**, non dell'aspect: i livelli restano
  governabili per package (`logging.level.com.ginogipsy.sanmartino`) e in Loki la label
  `logger` continua a indicare il punto reale del codice.
- **Un errore di dominio non è un guasto.** Un 404 con stack trace a livello ERROR rende
  inutile il livello ERROR. Le eccezioni il cui nome finisce con un suffisso di
  `expected-exception-suffixes` (default: `NotFoundException`, che copre tutte quelle dei
  sei servizi) vanno a WARN senza stack trace.
- **Una stack trace per guasto, non una per livello.** L'aspect ricorda l'eccezione già
  segnalata nella catena di invocazioni del thread: il service la logga con la traccia, il
  controller solo come propagazione a DEBUG.

L'aspect gira **fuori** dal transaction advisor (`@Order(100)` contro
`Ordered.LOWEST_PRECEDENCE`): la durata misurata comprende flush e commit, cioè il tempo
che il chiamante percepisce.

### Mascheramento dei dati sensibili

`LogValueFormatter` maschera su tre livelli:

1. **nome del parametro / campo / chiave di mappa** che contiene uno dei
   `sensitive-names` (`password`, `token`, `secret`, `credential`, `authorization`,
   `apikey`, `pin`, `otp`, …) → `***`;
2. **annotazione `@Masked`** sul parametro, per i casi in cui il nome non lo suggerisce;
3. **pattern `chiave=valore` sulla stringa già renderizzata** → copre i `toString()`
   generati (Lombok, openapi-generator) e i frammenti JSON, dove il nome del parametro
   non basterebbe.

```java
service.login("gino", "hunter2");
// >> AuthService.login(username=gino, password=***)

// RegistrationRequest è una classe @Data di Lombok: il suo toString() conterrebbe
// la password. Il terzo livello la intercetta comunque:
// >> AuthController.register(request=RegistrationRequest(username=gino, password=***, …))
```

### Il logging non deve avere effetti collaterali

Due regole nel formatter, entrambe coperte da test:

- **Sulle entity JPA stampa solo l'id** (`EventEntity(id=6f3a…)`), letto per reflection dal
  campo e non dal getter, così non forza l'inizializzazione di un proxy Hibernate.
- **Non itera le collezioni che non sono del JDK.** Una `PersistentBag` lazy renderizzata
  scatenerebbe una query — o una `LazyInitializationException` fuori transazione. Diventa
  `PersistentBag(...)` e basta.

In più: massimo 5 elementi per collezione, spazi collassati (i `toString()` di
openapi-generator sono multi-riga e un evento di log deve restare una riga), troncamento a
`max-value-length` caratteri.

### Property

Prefisso `sanmartino.observability`, default nella libreria — un servizio sovrascrive solo
ciò che gli serve.

| Property | Default | Significato |
|---|---|---|
| `enabled` | `true` | interruttore generale (aspect + filtro) |
| `logging.enabled` | `true` | solo l'aspect |
| `logging.log-arguments` | `true` | parametri di input nella riga di ingresso |
| `logging.log-return-value` | `true` | valore di ritorno nella riga di uscita |
| `logging.max-value-length` | `300` | troncamento di un valore renderizzato |
| `logging.slow-threshold` | `1s` | oltre questa durata l'uscita va a WARN |
| `logging.expected-exception-suffixes` | `[NotFoundException]` | eccezioni a WARN senza stack trace |
| `logging.sensitive-names` | `[password, passwd, secret, token, …]` | frammenti di nome da mascherare |
| `correlation.enabled` | `true` | filtro servlet del correlation id |
| `correlation.header-name` | `X-Correlation-Id` | header letto e riemesso |
| `correlation.mdc-key` | `correlationId` | chiave MDC, e quindi campo JSON |

In produzione conviene alzare il livello: con `logging.level.com.ginogipsy.sanmartino=INFO`
l'aspect smette di scrivere ingressi e uscite (li produce solo se DEBUG è attivo) ma
**continua** a segnalare invocazioni lente e guasti.

---

## 2. Correlation id e MDC

`CorrelationIdFilter` è il primo filtro della catena (`Ordered.HIGHEST_PRECEDENCE`) e:

- legge `X-Correlation-Id`, o ne genera uno (UUID) se manca;
- **valida** quello in ingresso (`[A-Za-z0-9._-]{1,64}`): arriva da un client non fidato e
  finisce nei log e in un header di risposta, quindi senza validazione sarebbe log
  injection (CR/LF: una riga = un evento) e response splitting. Fuori formato → rigenerato;
- lo mette in MDC insieme a `httpMethod` e `httpPath`, e lo rimette in **risposta**;
- lo riscrive sulla **richiesta inoltrata**, così il gateway (Spring Cloud Gateway MVC, che
  inoltra gli header in ingresso) lo passa a events-service e stands-service senza toccare
  le route.

Per le chiamate HTTP in uscita c'è `CorrelationIdPropagationInterceptor`, che la libreria
espone come bean. Va agganciato al `RestClient` — `saga-orchestrator` lo fa in `SagaConfig`,
perché quei client non partono dal `RestClient.Builder` auto-configurato e i
`RestClientCustomizer` non li toccherebbero:

```java
RestClient.builder().baseUrl(baseUrl).requestInterceptor(correlationId).build();
```

Le chiavi MDC diventano campi di primo livello nel log JSON: `correlationId`, `httpMethod`,
`httpPath` (per richiesta) e `operation`, `durationMs`, `outcome` (sulle righe di uscita
dell'aspect). Sono filtrabili in Loki senza regex.

> **Kafka non propaga ancora il correlation id.** Un evento saga consumato da
> notifications-service apre una catena nuova. Serve un `ProducerInterceptor` /
> `RecordInterceptor` che scriva l'id negli header del record: non è in questo task.

---

## 3. Formato dei log

`logging.structured.format.file: ecs` — **Elastic Common Schema**, JSON nativo di Spring
Boot (nessuna dipendenza aggiuntiva, nessun `logback-spring.xml`). Riga reale prodotta da
`events-service`:

```json
{"@timestamp":"2026-07-31T14:26:56.109036400Z","log":{"level":"DEBUG","logger":"com.ginogipsy.sanmartino.events.service.EventService"},"process":{"pid":2684,"thread":{"name":"http-nio-8081-exec-3"}},"service":{"name":"events-service","version":"0.0.1","environment":"local"},"message":"<< EventService.findAll completed in 148 ms -> [EventEntity(id=11111111-…-000000002026), …]","httpPath":"/v1/events","correlationId":"demo-123","httpMethod":"GET","operation":"EventService.findAll","durationMs":"148","outcome":"success","ecs":{"version":"8.11"}}
```

Dove finisce:

| Destinazione | Formato | Property |
|---|---|---|
| file `logs/<service>.log` | JSON (ECS) — è la sorgente di Promtail | `logging.file.name`, `logging.structured.format.file` |
| console | testo leggibile, con il correlation id nel prefisso | `logging.pattern.level` |
| console, in container | JSON se `LOG_CONSOLE_FORMAT=ecs` | `logging.structured.format.console` |

Il file rotola da solo (10 MB, 7 giorni di storico: default di Spring Boot). `LOG_DIR`
(default `./logs`) è **relativo alla working directory del processo**, che con IntelliJ è la
cartella del modulo: per questo Promtail cerca i log sia in `logs/` sia in `<modulo>/logs/`.

Sulla console il correlation id compare nel livello, come fa Spring Boot con il traceId:

```
2026-07-31T16:26:55.939+02:00 DEBUG [events-service,demo-123] 2684 --- [nio-8081-exec-3] c.g.s.events.api.EventsController : >> EventsController.listEvents(status=null)
```

---

## 4. Metriche

`spring-boot-starter-actuator` **non** basta: l'endpoint `/actuator/prometheus` esiste solo
se c'è il registry, quindi ogni servizio dichiara anche
`io.micrometer:micrometer-registry-prometheus` (scope `runtime`).

Nei sei `application.yaml`:

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}   # label comune a tutte le metriche
    distribution:
      percentiles-histogram:
        http.server.requests: true              # buckets: senza, nessun p95 lato Prometheus
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

## 5. Lo stack Docker

| Servizio | Immagine | Porta host | Configurazione |
|---|---|---|---|
| Prometheus | `prom/prometheus:v3.2.1` | 9090 | `docker/prometheus/prometheus.yml` |
| Loki | `grafana/loki:3.4.2` | 3100 | `docker/loki/loki-config.yml` |
| Promtail | `grafana/promtail:3.4.2` | — | `docker/promtail/promtail-config.yml` |
| Grafana | `grafana/grafana:11.6.0` | 3000 | `docker/grafana/provisioning/**` |

Quattro decisioni che vale la pena conoscere:

- **Prometheus punta a `host.docker.internal`.** I servizi girano di norma sull'host
  (IntelliJ): lo dicono i secret di Vault, che contengono `localhost:5433`. Su Linux
  `host.docker.internal` non esiste, quindi il compose aggiunge
  `extra_hosts: host.docker.internal:host-gateway`. Per i servizi in container c'è la
  variante commentata in fondo a `prometheus.yml`.
- **Un job Prometheus per servizio**, così la label `job` è il nome del servizio e non
  entra in conflitto con la label `application` che i servizi esportano da sé.
- **Promtail ha due sorgenti**: i file JSON (repo montato in sola lettura su `/workspace`) e
  lo stdout dei container Compose via socket Docker. La seconda serve a Kafka, Postgres,
  Keycloak — e ai servizi, se li avvii in container (`LOG_CONSOLE_FORMAT=ecs` è già nel
  compose).
- **Come label solo `level` e `service`.** `correlationId`, `operation` e `durationMs`
  restano campi della riga JSON: come label farebbero esplodere la cardinalità di Loki (una
  serie temporale per richiesta). Si filtrano con `| json | correlationId="…"`.

Le credenziali di Grafana si sovrascrivono da `.env` (`GRAFANA_ADMIN_USER`,
`GRAFANA_ADMIN_PASSWORD`); il default `admin`/`admin` è solo per il dev locale.

> **Promtail è End-Of-Life dal 2 marzo 2026** (Grafana Labs): nessun aggiornamento, nemmeno
> di sicurezza. Funziona, ed è quello che questo task ha implementato, ma il successore è
> **Grafana Alloy**. La conversione è un comando:
> `alloy convert --source-format=promtail --output=config.alloy promtail-config.yml`.
> Da valutare come task a sé, insieme a Tempo (Alloy raccoglie log, metriche e trace).

---

## 6. Come si prova

### Solo lo stack

```bash
docker compose up -d prometheus loki promtail grafana
```

| Verifica | Comando | Atteso |
|---|---|---|
| Prometheus | `curl http://localhost:9090/-/ready` | `Prometheus Server is Ready.` |
| Loki | `curl http://localhost:3100/ready` | `ready` (i primi ~15s risponde `Ingester not ready`) |
| Grafana | `curl http://localhost:3000/api/health` | `"database": "ok"` |
| Promtail | `docker compose logs promtail` | nessun `level=error` |

### Con un servizio

```bash
# 1. Vault (i placeholder di application.yaml arrivano da lì) + il DB del servizio
docker compose up -d vault vault-init postgres-events

# 2. La libreria condivisa deve stare in ~/.m2 per lanciare un modulo da solo
./mvnw -pl :common-observability -am install -DskipTests

# 3. Il servizio, sull'host
cd events-service && ../mvnw spring-boot:run
```

```bash
# 4. Traffico, con un correlation id scelto da noi per ritrovarlo in Loki
curl -i -H 'X-Correlation-Id: demo-123' http://localhost:8081/v1/events
curl -i http://localhost:8081/v1/events/00000000-0000-0000-0000-000000000000   # 404
```

La risposta rimanda indietro `X-Correlation-Id: demo-123`; `events-service/logs/events-service.log`
contiene le righe JSON di ingresso e uscita, e il 404 produce **una** riga WARN senza stack
trace.

```bash
# 5. Metriche
curl -s http://localhost:8081/actuator/prometheus | grep http_server_requests_seconds_count

# 6. Target visti da Prometheus (events-service deve essere "up", gli altri cinque
#    "down" se non li hai avviati)
curl -s 'http://localhost:9090/api/v1/targets?state=active' | jq -r \
  '.data.activeTargets[] | "\(.labels.job)\t\(.health)"'

# 7. Log arrivati in Loki
curl -s -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={job="sanmartino"} | json | correlationId="demo-123"' \
  --data-urlencode 'since=30m' | jq '.data.result | length'
```

### In Grafana

<http://localhost:3000> (admin/admin) → **Dashboards → San Martino → San Martino — overview**:
traffico, p95, 4xx/5xx, heap, invocazioni fallite estratte dai log e il pannello dei log.

**Explore → Loki** per le query a mano:

```logql
{job="sanmartino"}                                        # tutto l'applicativo
{job="sanmartino", service="events-service", level="ERROR"}
{job="sanmartino"} | json | correlationId="demo-123"      # una richiesta, tutti i servizi
{job="sanmartino"} | json | outcome="failure"             # invocazioni fallite
{job="sanmartino"} | json | durationMs > 500              # invocazioni lente
{job="docker", service="kafka"}                           # log dei container
```

**Explore → Prometheus**:

```promql
sum by (application) (rate(http_server_requests_seconds_count[1m]))
histogram_quantile(0.95, sum by (le, application) (rate(http_server_requests_seconds_bucket[5m])))
sum by (application) (jvm_memory_used_bytes{area="heap"})
```

---

## 7. Cosa è stato verificato, e cosa no

Verificato su questa postazione (Windows, Docker Desktop 29.6.2):

- i 29 test unitari di `common-observability` (`./mvnw -pl :common-observability test`);
- `./mvnw clean verify -DskipTests` sull'intero reactor;
- stack Compose avviato: Prometheus ready, Loki ready, Grafana health `ok`, Promtail senza
  errori, data source e dashboard provisionate (verificate via API Grafana);
- `events-service` avviato sull'host: log JSON ECS con tutti i campi MDC, 404 loggato una
  volta sola a WARN, `/actuator/prometheus` con `application` e i buckets, target Prometheus
  `up`, log interrogabili in Loki per `correlationId`, container Compose raccolti dal
  socket Docker.

**Non** verificato:

- gli altri cinque servizi a runtime (solo compilazione): in particolare la propagazione
  del correlation id **attraverso** il gateway e la saga, che ha senso provare con
  gateway + events + stands + saga accesi insieme;
- `EventsApiTest` continua a non girare in locale per il problema Testcontainers descritto
  in [ci-cd.md](./ci-cd.md) — è precedente a questo task e si valida in CI.
