# Business Audit Logging

Sistema di tracciamento e persistenza degli eventi di business dell'ecosistema distribuito **"Le cantine di San Martino"**.

A differenza dei log tecnici/operativi di applicazione (gestiti da `common-observability` su Loki tramite standard ECS per debugging e performance), l'**Audit Logging** registra le **azioni di dominio eseguite dagli utenti o dal sistema**, garantendo conformità, tracciabilità delle modifiche e audit trail per gli amministratori.

---

## 1. Architettura del Sistema

```
 ┌─────────────────────────────────────────────────────────┐
 │ Servizio di Dominio (es. stands-service, events-service)│
 │                                                         │
 │  @Auditable(action = "STAND:CREATE",                    │
 │             resourceId = "#result.id",                  │
 │             description = "'Creata cantina ' + #name")  │
 │  public StandResponse create(StandRequest request) { … }│
 └────────────────────────────┬────────────────────────────┘
                              │
                    AuditAspect (AOP)
                    (valuta SpEL + risolve SecurityContext)
                              │
                    KafkaAuditPublisher
                              │
                              ▼ topic: sanmartino.audit.events
                 ┌─────────────────────────┐
                 │          Kafka          │
                 └────────────┬────────────┘
                              │
                              ▼ consumer: AuditEventListener
 ┌─────────────────────────────────────────────────────────┐
 │ audit-service (Porta 8086)                              │
 │                                                         │
 │  - PostgreSQL (`postgres-audit`, porta 5438 locale)     │
 │  - Tabella `audit_logs` con indici B-Tree + JSONB       │
 │  - API protetta: GET /v1/audit-logs (ROLE_ADMIN)        │
 └─────────────────────────────────────────────────────────┘
```

### Separazione delle responsabilità
* **Decoupling asincrono via Kafka:** La registrazione di un audit log non blocca né rallenta la transazione di business del microservizio chiamante. Se Kafka è temporaneamente non disponibile, il servizio continua a funzionare senza impattare l'utente finale.
* **Isolamento dei dati:** Il microservizio `audit-service` possiede il proprio database dedicato PostgreSQL (`audit`), garantendo l'isolamento del dominio e una retention policy indipendente.
* **Sicurezza Role-Based (RBAC):** L'accesso in lettura agli audit log è riservato esclusivamente agli utenti autenticati con ruolo `ROLE_ADMIN` (Keycloak).

---

## 2. Struttura del Record di Audit

Ogni evento di audit rispetta il seguente schema immutabile (`AuditEvent`):

| Campo | Tipo | Descrizione |
|---|---|---|
| `id` | `UUID` / `String` | Identificativo univoco dell'evento |
| `serviceId` | `String` | Identificativo del microservizio emittente (es. `stands-service`, `events-service`) |
| `action` | `String` | Codice univoco dell'azione di dominio (es. `STAND:CREATE`, `STAND:UPDATE`) |
| `userId` | `String` | Identificativo dell'utente autore dell'azione (`sub` del token JWT o `anonymous`) |
| `username` | `String` | Username dell'autore (`preferred_username` o claim analogo del token JWT) |
| `resourceId` | `String` | ID della risorsa impattata (es. ID cantina, ID evento, ID piatto) |
| `description` | `String` | Descrizione leggibile dell'operazione |
| `correlationId` | `String` | Correlation ID per correlare l'audit ai log applicativi su Loki / Grafana |
| `timestamp` | `Instant` | Data e ora UTC dell'evento |
| `metadata` | `Map<String, Object>` | Attributi contestuali aggiuntivi (salvati in formato JSONB) |

---

## 3. Come Usare `@Auditable` nei Microservizi

I servizi non interagiscono direttamente con Kafka o con `audit-service`. È sufficiente includere `common-observability` e annotare i metodi di business (solitamente nei layer `@Service`).

### Esempio Pratico (`StandService.java`)

```java
@Service
public class StandService {

    @Auditable(
        action = "STAND:CREATE",
        resourceId = "#result.id()",
        description = "'Creata cantina ' + #request.name()",
        metadata = "{'capacity': #request.capacity()}"
    )
    public StandResponse create(CreateStandRequest request) {
        // ... business logic ...
        return standResponse;
    }

    @Auditable(
        action = "STAND:DELETE",
        resourceId = "#standId",
        description = "'Eliminata cantina ' + #standId"
    )
    public void delete(UUID standId) {
        // ... business logic ...
    }
}
```

### Supporto Spring Expression Language (SpEL)
L'annotazione `@Auditable` supporta espressioni SpEL dinamiche:
* **Argomenti del metodo:** accessibili tramite il loro nome (es. `#request`, `#standId`) o indice (`#a0`, `#p0`).
* **Valore restituito:** accessibile tramite `#result`.
* **Metadata dinamici:** è possibile definire mappe inline, ad esempio `"{'price': #request.price(), 'category': #request.category()}"`.

Se un'espressione SpEL non viene valorizzata o restituisce `null`, il campo corrispondente rimarrà `null` senza sollevare eccezioni. Se il metodo annotato lancia un'eccezione, l'evento di audit **non** viene emesso (l'azione non si è completata con successo).

---

## 4. Decisioni di Design Architetturali

### 4.1. Action Codes: Stringhe convenzionali vs Enum Monolitico
Invece di un unico Java `enum` centralizzato in `common-observability` contenente tutte le possibili azioni di tutti i microservizi, abbiamo adottato una **convenzione a stringhe strutturate** (`<SERVIZIO>:<AZIONE>`):
* **Indipendenza dei rilasci:** Se `stands-service` introduce l'azione `STAND:TRANSFER_MANAGER`, non deve forzare il rilascio, bumping e re-deploy di `common-observability` per tutti gli altri 5 microservizi.
* **Flessibilità di dominio:** Ogni microservizio definisce liberamente le proprie costanti di dominio (es. `StandAuditActions.java`).
* **Governance:** Il pattern `<MODULO>:<AZIONE>` (es. `STAND:CREATE`, `EVENT:PUBLISH`, `AUTH:PASSWORD_RESET`) garantisce namespace ordinati ed evita collisioni.

### 4.2. Protocol Buffers (Protobuf) vs Java Record + Kafka JSON
Molti progetti enterprise adottano Protobuf per definire contratti gRPC e messaggi. Valutazione rispetto al nostro stack:
* **Quando Protobuf eccelle:**
  * Architetture polyglot estese (es. backend misti Go, C++, Python, Java).
  * Chiamate RPC sincrone ad altissima frequenza con requisiti di latenza sub-millisecondo.
  * Sistemi con decine di team distribuiti che condividono un repository `schema-registry` centralizzato.
* **Perché in questo progetto è stato scelto Java Record + JSON:**
  * **Ecosistema Omogeneo:** I microservizi di San Martino sono tutti sviluppati in Java 25 (LTS) / Spring Boot 4.
  * **Zero Tooling Overhead:** Nessun bisogno di installare compilatori nativi `protoc`, plugin maven complessi o step di code generation per i messaggi interni.
  * **Persistenza Diretta PostgreSQL JSONB:** Il payload JSON dell'evento o dei suoi `metadata` mappa direttamente e trasparentemente sulle colonne JSONB di Postgres senza layer di transcodifica Protobuf-JSON.
  * **Manutenibilità e Leggibilità:** I messaggi su Kafka sono direttamente ispezionabili e leggibili dagli sviluppatori tramite CLI o Kafka UI in chiaro senza dover caricare descrittori `.proto`.

---

## 5. Microservizio `audit-service`

### Persistenza (PostgreSQL)
Tabella `audit_logs` con migrazione Flyway (`V1__create_audit_logs_table.sql`):
```sql
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    service_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    username VARCHAR(128),
    resource_id VARCHAR(128),
    description TEXT,
    correlation_id VARCHAR(64),
    timestamp TIMESTAMPTZ NOT NULL,
    metadata JSONB
);

CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_service_id ON audit_logs(service_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_resource_id ON audit_logs(resource_id);
```

### API REST e Sicurezza
L'endpoint REST di consultazione `/v1/audit-logs` è descritto in `api/audit-api.yaml`:

```http
GET /v1/audit-logs?serviceId=stands-service&action=STAND:CREATE&from=2026-11-01T00:00:00Z&page=0&size=20
Authorization: Bearer <JWT-Token>
```

* **Autenticazione & Autorizzazione:** Integrato con Keycloak OAuth2 Resource Server.
* **Regola di Sicurezza:** Richiede il ruolo **`ROLE_ADMIN`**. Chiamate non autenticate restituiscono `401 Unauthorized`, mentre utenti autenticati senza ruolo `admin` ricevono `403 Forbidden`.
* **Filtri Dinamici:** È possibile filtrare per:
  * `serviceId`
  * `action`
  * `userId`
  * `resourceId`
  * intervallo temporale (`from`, `to`)
  * paginazione (`page`, `size`, `sort`)

---

## 6. Configurazione e Infrastruttura

### Porte e Riferimenti Locali
| Componente | Host / Porta | Note |
|---|---|---|
| `audit-service` | `http://localhost:8086` | Microservizio Spring Boot |
| `postgres-audit` | `localhost:5438` | Database PostgreSQL dedicato (`audit`/`audit`) |
| Gateway Route | `/v1/audit-logs/**` | Instrada ad `AUDIT_URI` con Circuit Breaker `auditCb` |
| Kafka Topic | `sanmartino.audit.events` | Partizionato, auto-creato all'avvio |

### Segreti HashiCorp Vault
Il path per il servizio locale e Kubernetes è `secret/audit-service`:
```properties
spring.datasource.url=jdbc:postgresql://postgres-audit:5432/audit
spring.datasource.username=audit
spring.datasource.password=<generata/configurata>
spring.kafka.bootstrap-servers=kafka:9092
```

### Avvio in Docker Compose
Il database `postgres-audit` si avvia con lo stack infrastrutturale:
```bash
docker compose up -d
```
Mentre il container `audit-service` è configurato con profilo `apps`:
```bash
docker compose --profile apps up -d audit-service
```

### Avvio in Kubernetes
I manifesti sono integrati in:
* `k8s/base/apps/audit-service.yaml`: Deployment e Service sulla porta 8086 con ServiceAccount dedicata.
* `k8s/components/infra-local/postgres/audit.yaml`: StatefulSet con PersistentVolume per il cluster Kind.
* `k8s/overlays/local/`: include immagine `ghcr.io/ginogipsy/sanmartino/audit-service:dev` e secret generator `postgres-audit`.
* `scripts/k8s-dev-up.sh`: gestisce automaticamente la creazione del database, generazione password e avvio a fasi.
