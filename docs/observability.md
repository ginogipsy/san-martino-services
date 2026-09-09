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

### Attraverso Kafka

Un evento saga non apre più una catena nuova: l'id viaggia come header del record,
`X-Correlation-Id`, con due meccanismi diversi ai due estremi.

In **produzione** l'interceptor è `KafkaCorrelationInterceptor`, un `ProducerInterceptor` di
Apache Kafka: non è un bean, è Kafka a istanziarlo per nome da `interceptor.classes`. Ce lo
mette `ObservabilityAutoConfiguration` con un `DefaultKafkaProducerFactoryCustomizer`, che
aggiunge alla config map del producer il nome della classe — in merge con gli interceptor
eventualmente dichiarati dal servizio, perché `updateConfigs` sovrascrive la chiave invece
di accodarla — e le due chiavi da cui `configure()` legge header e chiave MDC. Passare dalla
config map è l'unica via per configurare un oggetto che non è Spring a costruire.

In **consumo** è `KafkaCorrelationRecordInterceptor`, un `RecordInterceptor` di Spring Kafka,
esposto come bean e agganciato da Boot alla listener container factory. Attenzione: **un
servizio che dichiara una propria factory deve chiamare `setRecordInterceptor` a mano**,
perché con un bean di nome `kafkaListenerContainerFactory` l'auto-configurazione di Boot
arretra e con lei l'aggancio. `notifications-service` è in questo caso — la factory custom
gli serve per i deserializer — e lo fa esplicitamente in `KafkaConsumerConfig`.

Il callback di `KafkaTemplate.send` gira sul thread I/O del producer, che non ha l'MDC della
richiesta: `SagaEventPublisher` cattura la mappa MDC prima della `send` e la ripristina
attorno al log dell'esito, altrimenti quella riga — il passaggio da HTTP a Kafka, cioè dove
si guarda quando un evento non arriva — resterebbe l'unica non correlabile della catena.

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

### La catena completa, dal gateway a Kafka

Prova la propagazione end-to-end: un id **generato dal gateway** deve arrivare ai log di
`notifications-service` attraversando saga-orchestrator e Kafka.

```bash
# 1. Infrastruttura. I servizi applicativi girano sull'host, non in Compose: non hanno
#    healthcheck, e i depends_on che li richiedono `service_healthy` farebbero fallire un
#    `docker compose up` dell'intero stack.
docker compose up -d vault vault-init postgres-events postgres-stands postgres-saga kafka

# Se hai cambiato un secret in docker-compose.yml, vault-init va rieseguito: è un
# one-shot, un container già uscito non ripopola nulla.
docker compose up -d --force-recreate --no-deps vault-init
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=my-root-token \
  san-martino-vault vault kv get secret/gateway     # deve contenere SAGA_URI

# 2. I cinque servizi coinvolti, sull'host
for s in events-service stands-service saga-orchestrator notifications-service gateway; do
  java -jar $s/target/$s-<versione>.jar > /tmp/$s.log 2>&1 &
done

# 3. Una cantina da far validare alla saga, passando dal gateway
curl -s -X POST localhost:8080/v1/stands -H 'Content-Type: application/json' \
  -d '{"number":42,"name":"Cantina E2E","description":{"it":"prova","en":"test"},
       "firstParticipationYear":1990,"latitude":41.58,"longitude":13.58}'

# 4. La saga, SENZA X-Correlation-Id: l'id lo deve generare il gateway e tornare in risposta
curl -s -D - -o /dev/null -X POST localhost:8080/v1/sagas/create-event-with-stands \
  -H 'Content-Type: application/json' \
  -d '{"event":{"name":"Festa E2E","place":"Arce","startDate":"2026-11-11",
       "endDate":"2026-11-13","description":{"it":"prova","en":"test"}},
       "standIds":["<id della cantina>"]}' | grep -i correlation

# 5. Lo stesso id nei log di notifications-service, sul thread del listener
grep '<id generato>' /tmp/notifications-service.log

# 6. Prova diretta sul record, invece che dedotta dai log: l'header c'è davvero.
#    MSYS_NO_PATHCONV serve su Git Bash, che altrimenti riscrive il path dentro il container.
MSYS_NO_PATHCONV=1 docker exec san-martino-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic sanmartino.saga.events \
  --from-beginning --property print.headers=true --timeout-ms 12000
```

Il gateway non logga nulla per richiesta, quindi nei suoi log l'id non si trova: è un punto
cieco della traccia, non un MDC rotto. Che il filtro abbia fatto il suo lavoro si vede
dall'header in risposta e dai log dei servizi a valle.

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

- i 40 test unitari di `common-observability` (`./mvnw -pl :common-observability test`), di cui
  11 sui due interceptor Kafka: comportamento degli interceptor e cablaggio dell'autoconfig;
- `./mvnw clean verify -DskipTests` sull'intero reactor;
- stack Compose avviato: Prometheus ready, Loki ready, Grafana health `ok`, Promtail senza
  errori, data source e dashboard provisionate (verificate via API Grafana);
- `events-service` avviato sull'host: log JSON ECS con tutti i campi MDC, 404 loggato una
  volta sola a WARN, `/actuator/prometheus` con `application` e i buckets, target Prometheus
  `up`, log interrogabili in Loki per `correlationId`, container Compose raccolti dal
  socket Docker.

- **la catena completa, a runtime, con gateway + events + stands + saga + notifications
  accesi insieme** (procedura in [§6](#la-catena-completa-dal-gateway-a-kafka)): un id
  generato dal gateway su una richiesta senza header è comparso nei log di events-service e
  stands-service (propagazione HTTP), di saga-orchestrator, e di notifications-service
  attraverso Kafka, sul thread del listener. L'header `X-Correlation-Id` è stato letto
  direttamente dai record del topic `sanmartino.saga.events`, non dedotto dai log;
- il log di esito di `SagaEventPublisher`, che gira sul thread I/O del producer, esce
  correlato.

**Non** verificato:

- **FCM**: la prova è stata fatta con `sanmartino.fcm.enabled=false`, quindi il push è
  passato da `LoggingPushNotificationSender`. L'invio reale a Firebase resta da provare;
- **il gateway come punto osservabile**: non logga nulla per richiesta, quindi non si può
  dire se il filtro `CircuitBreaker` con time limiter esegua la chiamata a valle su un
  thread del pool di Resilience4j perdendo l'MDC. Il transito dell'header non ne dipende
  (lo porta il wrapper della richiesta, non l'MDC) e funziona; servirebbe una riga di
  access log per chiudere la domanda;
- **il transito su Kafka nei test automatici**: la copertura locale è unitaria, il
  passaggio su un broker è provato solo a mano. Manca `org.testcontainers:kafka` in scope
  `test` su `saga-orchestrator` e `notifications-service`;
- `EventsApiTest` continua a non girare in locale per il problema Testcontainers descritto
  in [ci-cd.md](./ci-cd.md) — è precedente a questo task e si valida in CI.

Dettaglio cosmetico noto: la risposta esce con `X-Correlation-Id` due volte, stesso valore.
Lo imposta il filtro del gateway e Gateway MVC ci accoda quello del servizio a valle.
