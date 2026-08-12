# Coding standards — San Martino

Regole per chi scrive codice in questo repo, umani e agenti. Chi apre una PR è responsabile del rispetto di queste regole; l'analisi statica è una rete di sicurezza, non il primo controllo.

> **Nota sugli strumenti.** In CI gira **Qodana** (ispezioni IntelliJ), non SonarQube. Le due famiglie di regole si sovrappongono ampiamente ma non coincidono: gli ID `java:SXXXX` citati qui sono i riferimenti SonarSource, usati perché sono la nomenclatura più diffusa e documentata ([rules.sonarsource.com](https://rules.sonarsource.com/java/)). Molte hanno un'ispezione IntelliJ equivalente. Se in futuro si vuole il vero motore Sonar, va aggiunto `sonar-maven-plugin` più un'istanza SonarQube o SonarCloud: **non è configurato oggi**.

---

## 1. Regole che mordono in questo codebase

Queste non sono ipotetiche: corrispondono a problemi presenti o probabili qui.

### `java:S2068` / `java:S6437` — credenziali hardcoded

Presente **adesso** in `events-service/src/main/resources/application.yaml`:

```yaml
token: my-root-token   # ATTENZIONE: Non hardcodare in produzione!
```

e in `docker-compose.yml` (`keycloak.client-secret=d457ab89-…`).

Un commento che dice "non fare questo in produzione" non è una mitigazione: il valore è nel repo e nella cronologia git. Regola: **nessun segreto in un file versionato**, nemmeno per il dev locale. Vanno letti da variabile d'ambiente con default innocuo, o da Vault.

```yaml
# ✅
token: ${VAULT_TOKEN:}
```

Il `.gitignore` di questo repo è già rigoroso su chiavi e `.env`: mantenerlo così.

### `java:S1075` — URI hardcoded

`http://localhost:9080`, `jdbc:postgresql://localhost:5433/events` e simili sono sparsi tra `application.yaml`, `docker-compose.yml` e i secret di Vault. Per il dev locale è accettabile; **nel codice Java non lo è mai**. Un URI in una classe va da configurazione tipizzata:

```java
// ✅
@ConfigurationProperties("clients")
record ClientsProperties(URI eventsBaseUrl, URI standsBaseUrl) {}
```

Come effetto collaterale, questo è anche il posto dove si è già nascosto un bug: il secret `auth-service` contiene `jdbc:postgresql://localhost:5437/events/auth`, dove il DB effettivo del container `postgres-auth` è `auth`. Un URI in configurazione tipizzata e validata avrebbe fatto rumore all'avvio.

### `java:S112` — non lanciare eccezioni generiche

Mai `throw new RuntimeException(...)` o `Exception`. Servono eccezioni di dominio, mappate a risposte HTTP in un unico punto:

```java
// ✅
@ControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(EventNotFoundException.class)
    ProblemDetail onNotFound(EventNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
```

Usare `ProblemDetail` (RFC 9457), già disponibile in Spring Boot 4: dà risposte d'errore coerenti su tutti i servizi senza inventare un formato per servizio.

### `java:S106` — niente `System.out` / `System.err`

Solo SLF4J, e **sempre con messaggi parametrizzati**, non concatenazione:

```java
log.debug("Evento {} non trovato per utente {}", eventId, userId);   // ✅
log.debug("Evento " + eventId + " non trovato");                     // ❌
```

La concatenazione costruisce la stringa anche quando il livello è disattivato.

### `java:S2142` — non ingoiare `InterruptedException`

Rilevante con Kafka e i client HTTP:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();   // ✅ ripristina il flag
    throw new ProcessingException("interrotto", e);
}
```

### `java:S3776` — complessità cognitiva

Soglia predefinita 15. Un metodo che la supera va spezzato, non commentato.

### Altre a cui prestare attenzione

| Regola | Sintesi |
|---|---|
| `java:S2095` | Chiudere le risorse: `try`-with-resources |
| `java:S1192` | Non duplicare literal stringa: estrarre costanti |
| `java:S1118` | Classi di utility: costruttore privato |
| `java:S1319` | Dichiarare `List`/`Map`, non `ArrayList`/`HashMap` |
| `java:S1155` | `isEmpty()`, non `size() == 0` |
| `java:S1874` | Non usare API deprecate |
| `java:S1128` | Nessun import inutilizzato |
| `java:S4502` | CSRF disabilitato: giustificarlo (lecito per API stateless con JWT, va commentato) |
| `java:S5122` | CORS permissivo: mai `*` in produzione |

---

## 2. Pattern architetturali del progetto

### API-first: i sorgenti generati non si toccano

La fonte di verità dei contratti è `api/*.yaml`. Da lì `openapi-generator` produce le interfacce sotto `target/generated-sources/openapi/`, che `.gitignore` esclude.

- **Mai** editare un file generato: viene sovrascritto al build successivo.
- Per cambiare un contratto si modifica lo YAML in `api/` e si rigenera.
- I controller **implementano** l'interfaccia generata, non la duplicano.

### Non far uscire le entity JPA dall'API

I model generati da OpenAPI sono i DTO. Le entity JPA restano nel layer di persistenza. Un mapping esplicito nel service evita che un refactoring del DB diventi un breaking change del contratto.

### Injection dal costruttore

```java
// ✅
@Service
class EventService {
    private final EventRepository repository;
    EventService(EventRepository repository) { this.repository = repository; }
}
```

Mai `@Autowired` su campo: rende i campi non-`final`, nasconde le dipendenze e impedisce di istanziare la classe nei test senza contesto Spring.

### Record per i tipi immutabili

Java 26: usare `record` per DTO, value object e `@ConfigurationProperties`. Lombok resta ammesso sulle entity JPA (dove servono costruttori no-arg e mutabilità), non altrove.

### Transazioni nel service, non nel controller

`@Transactional` sul metodo di service. `@Transactional(readOnly = true)` sulle letture: consente a Hibernate di salvare il dirty checking. `spring.jpa.open-in-view` è già `false` — mantenerlo, così una lazy loading fuori transazione fallisce nei test invece che in produzione.

### Flyway: una migration applicata è immutabile

Mai modificare un `V*__*.sql` già applicato: il checksum cambia e Flyway rifiuta l'avvio. Si aggiunge una nuova migration. `ddl-auto` resta `validate`: lo schema lo governa Flyway, non Hibernate.

---

## 3. Test

- **Preferire le slice al contesto completo.** `@WebMvcTest` per un controller, `@DataJpaTest` per un repository. `@SpringBootTest` avvia tutto (e in questo progetto richiede Vault): riservarlo ai test di integrazione veri.
- **Testcontainers, non H2.** Decisione già presa e giusta: H2 non riproduce il comportamento di PostgreSQL.
- **Container singleton** quando più classi di test servono lo stesso DB: un container `static` riusato batte uno per classe, che moltiplica il tempo di build.
- **Nessun `Thread.sleep` per sincronizzare.** Usare `Awaitility` o i costrutti di attesa di Testcontainers: un `sleep` è un test flaky con un timer.
- **Asserzioni sul comportamento, non sull'implementazione.** `EventsApiTest` che verifica status code e payload è l'esempio giusto.

Vedi `docs/ci-cd.md` per come far girare i test in locale (serve `docker compose up -d vault vault-init`).

---

## 4. Git e PR

- **Branching: git flow.** `feature/*` → PR verso `develop`. `release/*` e `hotfix/*` → `master`, con back-merge su `develop`.
- **Messaggi di commit:** convenzione del repo `(#NNN) descrizione all'infinito inglese`, dove `NNN` è il numero della issue o del task.
  ```
  (#003) added GitHub Actions CI/CD pipeline for the Maven monorepo
  ```
- **Un commit = un cambiamento logico.** Il fix di un bug scoperto strada facendo va in un commit separato, con il *perché* nel corpo: se non è ovvio dal diff, va scritto.
- **Non committare mai** segreti, `target/`, sorgenti generati, `.env`.
- `docs/api/` **è generato**: si rigenera con la catena in `docs/ci-cd.md`, non si edita a mano.

---

## 5. Prima di aprire una PR

```bash
docker compose up -d vault vault-init      # i test hanno bisogno dei secret KV
./mvnw clean verify                        # build + test dell'intero reactor
```

Se hai toccato `api/*.yaml`, rigenera anche le docs (altrimenti il gate della CI fallisce):

```bash
npx @redocly/cli@1.34.2 build-docs api/<spec>.yaml -o docs/api/<spec>.html
npx @redocly/cli@1.34.2 bundle     api/<spec>.yaml -o docs/api/<spec>.bundled.yaml
npx prettier@3.3.3 --write 'docs/api/**/*.md' 'docs/api/**/*.yaml'
```
