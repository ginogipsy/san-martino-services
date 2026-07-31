### Guida alla Gestione dei Segreti con HashiCorp Vault e Spring Boot

Questa guida riassume i passaggi per configurare i tuoi microservizi Spring Boot per leggere i segreti da HashiCorp Vault, sia per un ambiente generico che per ambienti specifici (es. `dev`, `prod`).

---

#### 1. **Configurazione di Base di Vault (Prerequisiti)**

Assicurati che il tuo server Vault sia in esecuzione e accessibile. Per interagire con Vault tramite CLI, avrai bisogno di:

*   **`VAULT_ADDR`**: L'indirizzo del tuo server Vault (es. `http://127.0.0.1:8200`).
*   **`VAULT_TOKEN`**: Un token di autenticazione valido per Vault (per lo sviluppo, un `root token` può essere usato, ma **MAI in produzione**).

Puoi impostare queste variabili d'ambiente o passarle direttamente al comando `vault`.

---

#### 2. **Configurazione di Spring Boot per Vault (`application.yaml`)**

Per ogni servizio, assicurati che il file `src/main/resources/application.yaml` (o `application-{profile}.yaml`) contenga la seguente configurazione per Spring Cloud Vault. Questo permette all'applicazione di connettersi a Vault e di sapere dove cercare i segreti.

> **IMPORTANTE (Spring Boot 3.x/4.x + Spring Cloud 2020.0+):** il *bootstrap context* è disabilitato di default. La sola sezione `spring.cloud.vault.*` **non basta**: Vault non verrebbe mai contattato all'avvio. Bisogna abilitare esplicitamente il caricamento con `spring.config.import: vault://`. In alternativa (approccio legacy) si aggiunge la dipendenza `spring-cloud-starter-bootstrap` e si sposta la config in `bootstrap.yml` — ma l'approccio consigliato è `spring.config.import`.
>
> **Backend KV:** Vault in dev mode monta `secret/` come **KV v2**. Va quindi usato il backend `kv` (non `generic`, che è KV v1: cercherebbe i path sbagliati e non troverebbe i segreti).

**Esempio di configurazione comune per tutti i servizi:**

```yaml
spring:
  application:
    name: <NOME_SERVIZIO> # Es. auth-service, events-service, gateway, ecc.
  config:
    import: vault://       # ABILITA il caricamento dei segreti da Vault (usa optional:vault:// per non fallire se Vault è down)
  cloud:
    vault:
      host: localhost # O l'hostname/IP del tuo server Vault
      port: 8200
      scheme: http    # o https in produzione
      authentication: TOKEN
      token: my-root-token # ATTENZIONE: MAI hardcodare in produzione!
      kv:
        enabled: true
        backend: secret            # mount KV v2 di default in Vault dev
        default-context: <NOME_SERVIZIO> # legge secret/<NOME_SERVIZIO>
```

**Precedenza:** le proprietà importate da `vault://` **sovrascrivono** quelle definite nello stesso `application.yaml`. Poiché i segreti in Vault usano direttamente i nomi reali delle proprietà (es. `spring.datasource.url`), i placeholder `${...}` presenti nel file vengono messi in ombra e non causano errori di risoluzione.

**Nota:** Dopo aver aggiunto questa configurazione, rimuovi tutti i valori hardcoded o di default per le proprietà che vuoi gestire in Vault.

---

#### 3. **Inserimento dei Segreti in Vault (Ambiente Generico/Default)**

Questi comandi inseriscono i segreti nel percorso `secret/<NOME_SERVIZIO>`. Questi valori verranno utilizzati se nessun profilo Spring specifico è attivo, o come fallback se un profilo specifico non definisce un certo segreto.

**Sostituisci i placeholder con i tuoi valori reali.**

```sh
# --- auth-service ---
vault kv put secret/auth-service \
    spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9080/realms/san-martino \
    spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9080/realms/san-martino/protocol/openid-connect/certs \
    spring.datasource.url=jdbc:postgresql://localhost:5437/auth \
    spring.datasource.username=auth \
    spring.datasource.password=auth \
    keycloak.server-url=http://localhost:9080 \
    keycloak.realm=san-martino \
    keycloak.client-id=admin-cli-wrapper \
    keycloak.client-secret=d457ab89-9283-4d14-b3ba-8f82a5a244f5

# --- events-service ---
vault kv put secret/events-service \
    spring.datasource.url=jdbc:postgresql://localhost:5433/events \
    spring.datasource.username=events \
    spring.datasource.password=events

# --- gateway ---
vault kv put secret/gateway \
    EVENTS_URI=http://localhost:8081 \
    STANDS_URI=http://localhost:8082

# --- notifications-service ---
vault kv put secret/notifications-service \
    spring.kafka.bootstrap-servers=localhost:9092 \
    sanmartino.fcm.credentials-path=/path/to/your/fcm/credentials.json # Sostituisci con il percorso reale

# --- saga-orchestrator ---
vault kv put secret/saga-orchestrator \
    spring.datasource.url=jdbc:postgresql://localhost:5435/saga \
    spring.datasource.username=saga \
    spring.datasource.password=saga \
    clients.events-base-url=http://localhost:8081 \
    clients.stands-base-url=http://localhost:8082 \
    spring.kafka.bootstrap-servers=localhost:9092

# --- stands-service ---
vault kv put secret/stands-service \
    spring.datasource.url=jdbc:postgresql://localhost:5434/stands \
    spring.datasource.username=stands \
    spring.datasource.password=stands
```

---

#### 4. **Inserimento dei Segreti in Vault (Ambienti Specifici: `dev`, `prod`, ecc.)**

Per differenziare i segreti per ambiente, useremo il percorso `secret/<NOME_SERVIZIO>/{profile}`. Con il backend `kv` e `default-context`, Spring Cloud Vault cerca automaticamente in `secret/<NOME_SERVIZIO>/<profile>` (il profilo va **dopo** il contesto, non prima) quando un profilo Spring è attivo, con fallback su `secret/<NOME_SERVIZIO>`.

**Esempio per `auth-service` (adatta per gli altri servizi):**

*   **Per l'ambiente `dev`:**
    ```sh
    vault kv put secret/auth-service/dev \
        spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak-dev:9080/realms/san-martino-dev \
        spring.datasource.url=jdbc:postgresql://postgres-dev:5432/auth_service_db_dev \
        spring.datasource.username=auth_dev \
        spring.datasource.password=auth_dev_pass \
        keycloak.server-url=http://keycloak-dev:9080 \
        keycloak.realm=san-martino-dev \
        keycloak.client-id=admin-cli-wrapper-dev \
        keycloak.client-secret=dev-secret-for-keycloak
    ```

*   **Per l'ambiente `prod`:**
    ```sh
    vault kv put secret/auth-service/prod \
        spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.yourdomain.com/realms/san-martino-prod \
        spring.datasource.url=jdbc:postgresql://postgres-prod:5432/auth_service_db_prod \
        spring.datasource.username=auth_prod \
        spring.datasource.password=auth_prod_pass \
        keycloak.server-url=https://keycloak.yourdomain.com \
        keycloak.realm=san-martino-prod \
        keycloak.client-id=admin-cli-wrapper-prod \
        keycloak.client-secret=prod-secret-for-keycloak
    ```

**Come l'applicazione legge i segreti:**

Quando avvii la tua applicazione Spring Boot con un profilo attivo:
*   `java -jar myapp.jar --spring.profiles.active=dev` -> L'applicazione cercherà i segreti in `secret/<NOME_SERVIZIO>/dev` (con fallback su `secret/<NOME_SERVIZIO>`).
*   `java -jar myapp.jar --spring.profiles.active=prod` -> L'applicazione cercherà i segreti in `secret/<NOME_SERVIZIO>/prod` (con fallback su `secret/<NOME_SERVIZIO>`).
*   `java -jar myapp.jar` (senza profilo) -> L'applicazione cercherà i segreti in `secret/<NOME_SERVIZIO>`.

---

#### 5. **Esecuzione dei Comandi Vault tramite Docker (se Vault è in Docker)**

Se il tuo server Vault è in esecuzione come container Docker (es. `san-martino-vault`), puoi eseguire i comandi `vault kv put` direttamente all'interno del container.

```sh
# Esempio generico per qualsiasi comando vault
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='my-root-token' san-martino-vault vault <comando_vault>

# Esempio specifico per inserire i segreti di stands-service
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='my-root-token' san-martino-vault vault kv put secret/stands-service \
    spring.datasource.url=jdbc:postgresql://localhost:5434/stands \
    spring.datasource.username=stands \
    spring.datasource.password=stands
```

**Nota:** `VAULT_ADDR` e `VAULT_TOKEN` devono essere passati come variabili d'ambiente al comando `docker exec` affinché il client `vault` all'interno del container possa autenticarsi e connettersi correttamente.

---

#### 6. **Considerazioni Importanti e Best Practices**

*   **Sicurezza del Token:** Il `my-root-token` è solo per lo sviluppo locale. In produzione, usa metodi di autenticazione più sicuri per Vault (es. AppRole, Kubernetes Auth Method, AWS/GCP Auth Method).
*   **Vault Policies (ACL):** Definisci politiche di accesso in Vault per limitare chi (o cosa) può leggere/scrivere quali segreti. Ad esempio, il servizio `auth-service` in produzione dovrebbe avere accesso solo a `secret/prod/auth-service`.
*   **Precedenza delle Proprietà:** Spring Boot carica le proprietà da diverse fonti con una specifica precedenza. I segreti da Vault hanno una precedenza elevata, ma possono essere sovrascritti da variabili d'ambiente o argomenti della riga di comando.
*   **Versioning (KVv2):** Il KV Secrets Engine v2 di Vault mantiene una cronologia delle modifiche ai segreti. Ogni `vault kv put` crea una nuova versione. Questo permette rollback e audit.
*   **Segreti Comuni:** Se hai segreti che sono uguali per tutti gli ambienti, puoi metterli nel percorso `secret/<NOME_SERVIZIO>` e non duplicarli nei percorsi specifici per ambiente. I percorsi specifici per ambiente sovrascriveranno solo le proprietà che definiscono.
