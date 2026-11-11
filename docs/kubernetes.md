# Infrastruttura Kubernetes — San Martino

Architettura, manifest e script di automazione per il deploy in locale (Kind) e in cloud del sistema a microservizi **"Le cantine di San Martino"**.

---

## 1. Architettura & Struttura `k8s/`

La configurazione è gestita con **Kustomize** nativo e organizzata in layer componibili nel repository `san-martino-services`:

```
k8s/
├── base/                                # Manifest applicativi comuni a tutti gli ambienti
│   ├── apps/                            # 6 microservizi Spring Boot + user-panel (Angular)
│   ├── common-config.yaml               # ConfigMap di runtime (logging, profili, env)
│   ├── ingress.yaml                     # Regole di routing base (/v1, /api/auth -> gateway; / -> user-panel)
│   ├── namespace.yaml                   # Namespace `sanmartino` con Pod Security Admission (restricted)
│   └── spring-boot-defaults.yaml        # Patch condivisa (probe Actuator, securityContext non-root)
├── components/                          # Componenti opzionali attivabili negli overlay
│   ├── infra-local/                     # 5 database PostgreSQL 17-alpine + Apache Kafka 3.8 KRaft
│   ├── keycloak/                        # Keycloak 26.8.2 con realm export montato
│   ├── vault-dev/                       # HashiCorp Vault 1.21 in dev mode con sidecar di seed
│   ├── vault-k8s-auth/                  # Integrazione ServiceAccount e autenticazione Kubernetes su Vault
│   └── observability/                   # Prometheus v3.2, Loki 3.4, Grafana 11.6, Grafana Alloy v1.21
├── kind/                                # Configurazione cluster Kind locale e Ingress Traefik
│   ├── cluster.yaml                     # Nodo v1.35.8 con port mapping host 80 e 8080 (127.0.0.1)
│   └── traefik/                         # Traefik v3.7.18 controller Ingress non-root
└── overlays/
    ├── local/                           # Overlay per sviluppo locale (Kind)
    │   ├── kustomization.yaml           # Include tutti i componenti locali + Ingress sanmartino.local
    │   └── secrets/                     # Generati automaticamente da k8s-dev-up.sh (ignorati da git)
    └── cloud/                           # Overlay per ambienti di produzione / cloud (GKE, EKS, DO)
        ├── cluster/                     # cert-manager ClusterIssuers (Let's Encrypt prod e staging)
        ├── availability.yaml            # PodDisruptionBudget e HorizontalPodAutoscaler (HPA)
        ├── scaling.yaml                 # Repliche e TopologySpreadConstraints (distribuzione su nodi)
        ├── resources.yaml               # CPU e Memory requests/limits per i container di produzione
        ├── ingress-tls.yaml             # TLS Ingress gestito da cert-manager
        └── vault-auth.yaml              # ServiceAccount Reviewer per Vault Kubernetes Auth
```

---

## 2. Sviluppo Locale con Kind

### Prerequisiti
- **Docker** 20.10+ (o Docker Desktop)
- **kubectl** v1.30+
- **kind** v0.25+

### Avvio dell'ambiente locale
Lo script `scripts/k8s-dev-up.sh` automatizza l'intero ciclo di bootstrap:
1. Crea il cluster Kind `sanmartino-local` se non esiste.
2. Genera credenziali casuali sicure per i database e Vault in `k8s/overlays/local/secrets/`.
3. Compila e carica nel cluster le immagini dei 6 servizi Spring Boot e del pannello Angular.
4. Distribuisce il controller Ingress **Traefik**.
5. Applica i manifesti in tre fasi per evitare cascate di riavvii:
   - **Fase 1:** Database PostgreSQL, Kafka e Vault dev.
   - **Fase 2:** Keycloak e stack di osservabilità (Prometheus, Loki, Alloy, Grafana).
   - **Fase 3:** Microservizi applicativi e pannello utente web.

```bash
# Avvio ambiente locale
./scripts/k8s-dev-up.sh
```

Aggiungi a `/etc/hosts`:
```
127.0.0.1 sanmartino.local grafana.sanmartino.local
```

### Endpoints locali
- **Web App:** [http://sanmartino.local](http://sanmartino.local)
- **API Gateway:** [http://sanmartino.local/v1](http://sanmartino.local/v1) / [http://sanmartino.local/api/auth](http://sanmartino.local/api/auth)
- **Grafana UI:** [http://grafana.sanmartino.local](http://grafana.sanmartino.local) (admin/admin o credenziali generate)
- **Android Debug Endpoint:** `http://localhost:8080/`

### Spegnimento
```bash
# Elimina il cluster Kind
./scripts/k8s-dev-down.sh

# Elimina il cluster e rimuove i file di segreti generati
./scripts/k8s-dev-down.sh --purge
```

### Kind vs Docker Compose: Quando usare cosa

| Scenario | Strumento consigliato | Comando | Note |
|---|---|---|---|
| **Sviluppo rapido & Debug** | **Docker Compose** + IntelliJ | `docker compose up -d` | Avvia solo DB, Kafka, Keycloak, Vault e Grafana. Permette di lanciare i microservizi da IntelliJ in modalità Debug con breakpoint e hot-reload istantaneo. |
| **Test E2E & Validazione K8s** | **Kubernetes (Kind)** | `./scripts/k8s-dev-up.sh` | Compila e avvia tutti i microservizi, Ingress Traefik, user-panel Angular, Alloy e policy K8s. Replica fedelmente l'ambiente di produzione. |

> [!WARNING]
> **Conflitto porte host:** Poiché sia Kind che Docker Compose espongono porte comuni (`8080`, `8200`, `9080`), **non eseguirli contemporaneamente**. Per passare da Kind a Compose: `./scripts/k8s-dev-down.sh && docker compose up -d`. Per tornare a Kind: `docker compose down && ./scripts/k8s-dev-up.sh`.

---

## 3. Connettività App Android & Client

### Risoluzione Loopback e API 36+
Sulle immagini emulatore Android con API 36+, le **Local Network Protections** bloccano il routing convenzionale verso la subnet `10.0.2.2`.
Per questo motivo:
1. Il cluster Kind espone l'Ingress Traefik sia sulla porta `80` che sulla porta `8080` di `127.0.0.1`.
2. L'app Android è configurata per puntare a `http://127.0.0.1:8080/`.
3. Con l'emulatore avviato, eseguire il task Gradle registrato nel repository Android:
   ```bash
   ./gradlew adbReverse
   ```
   che esegue internamente:
   ```bash
   adb reverse tcp:8080 tcp:8080
   ```
In questo modo le chiamate dal guest emulatore su `127.0.0.1:8080` arrivano direttamente a Traefik sull'host.

### User Panel Web
Il pannello Angular utilizza percorsi relativi (`/api/auth`, `/v1/stands`) definiti in `src/environments/environment.ts`. Quando servito da NGINX dietro l'Ingress Traefik, il browser risolve le richieste verso l'host corrente (`sanmartino.local` o il dominio cloud pubblico), evitando problemi di Cross-Origin Resource Sharing (CORS).

---

## 4. Gestione Segreti & HashiCorp Vault

- **In locale (`components/vault-dev`):** Vault è eseguito in memoria nel container con un sidecar che esegue `seed-secrets.sh` per popolare chiavi e configurazioni al boot.
- **In cloud (`components/vault-k8s-auth` & `overlays/cloud`):**
  - I servizi non usano token statici: sfruttano il metodo nativo **Kubernetes Auth** di Vault.
  - Ogni pod monta il token della propria `ServiceAccount` con audience `vault`.
  - Spring Cloud Vault invia il JWT proiettato all'endpoint `auth/kubernetes/login` di Vault.
  - Vault convalida il token contattando l'API Server di Kubernetes tramite il token reviewer configurato da `vault-auth.yaml`.

---

## 5. Osservabilità (Prometheus, Loki, Alloy, Grafana)

- **Prometheus v3.2:** Scopre automaticamente i pod annotati con `prometheus.io/scrape: "true"` tramite `kubernetes_sd_configs` limitato al namespace `sanmartino`.
- **Loki 3.4 & Grafana Alloy v1.21:** I log dei pod sono raccolti da Alloy tramite l'API di Kubernetes (`loki.source.kubernetes`) ed etichettati con namespace, pod, e container prima della spedizione a Loki. Alloy gira come `Deployment` anziché `DaemonSet` per rispettare la Pod Security Admission `restricted` (che vieta `hostPath`).
- **Filtro Actuator:** I pannelli delle dashboard HTTP e le query PromQL filtrano le chiamate con `uri!~"/actuator.*"`, impedendo che le probe periodiche di liveness/readiness (0.37 req/s a riposo) inquinino le metriche di latenza p95 e traffico.

---

## 6. Overlay Cloud (Produzione)

L'overlay `k8s/overlays/cloud` è predisposto per cluster gestiti (GKE, EKS, DigitalOcean Kubernetes):

1. **Database & Kafka Gestiti:** I database e Kafka sono esterni al cluster; gli indirizzi e le credenziali sono iniettati a runtime via Vault.
2. **cert-manager & TLS:** Gestione automatica dei certificati Let's Encrypt tramite i `ClusterIssuer` definiti in `k8s/overlays/cloud/cluster/`.
3. **Autoscaling & Resilienza:**
   - **PodDisruptionBudget (PDB):** Garantisce che almeno 1 replica per servizio rimanga attiva durante manutenzioni dei nodi o drain.
   - **HorizontalPodAutoscaler (HPA):** Scala automaticamente da 2 a 4 repliche per gateway, events-service e stands-service in base all'utilizzo CPU (>70%).
   - **TopologySpreadConstraints:** Distribuisce le repliche su host fisici/zone diverse impostando `matchLabelKeys: [app.kubernetes.io/name, pod-template-hash]` con `labelSelector` su `app.kubernetes.io/part-of: sanmartino`.
4. **Verifica statica dei manifest:**
   ```bash
   kubectl kustomize k8s/overlays/cloud
   ```
