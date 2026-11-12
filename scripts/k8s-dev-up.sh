#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# San Martino su un cluster Kubernetes locale (kind), da zero o in
# aggiornamento: cluster, controller Ingress, segreti, immagini, manifest.
# Idempotente: rilanciarlo ricostruisce solo ciò che è cambiato.
#
#   scripts/k8s-dev-up.sh               # tutto
#   scripts/k8s-dev-up.sh --skip-build  # non ricostruisce le immagini
#
# Variabili:
#   KIND       binario di kind                         (default: `kind` nel PATH)
#   PANEL_DIR  checkout di san-martino-user-panel      (default: ../san-martino-user-panel)
#
# Requisiti: docker, kubectl, kind (https://kind.sigs.k8s.io), le porte 80 e
# 8080 libere su 127.0.0.1. Usa SOLO il context kind-sanmartino: gli altri
# cluster configurati in kubectl non vengono toccati.
# ---------------------------------------------------------------------------
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER=sanmartino
CTX="kind-$CLUSTER"
NODE="$CLUSTER-control-plane"
NS=sanmartino
OVERLAY="$REPO/k8s/overlays/local"
SECRETS_DIR="$OVERLAY/secrets"
if [ -z "${PANEL_DIR:-}" ]; then
    if [ -d "$REPO/../san-martino-user-panel" ]; then
        PANEL_DIR="$REPO/../san-martino-user-panel"
    elif [ -d "$REPO/../san-martino-user-panel-032" ]; then
        PANEL_DIR="$REPO/../san-martino-user-panel-032"
    else
        PANEL_DIR="$REPO/../san-martino-user-panel"
    fi
fi
KIND="${KIND:-kind}"
TAG=dev
BACKEND_IMAGE=ghcr.io/ginogipsy/sanmartino
PANEL_IMAGE=ghcr.io/ginogipsy/san-martino-user-panel
SERVICES=(gateway auth-service events-service stands-service saga-orchestrator notifications-service audit-service)
# Avvio a fasi. Partendo tutti insieme, chi dipende da un altro pod (i servizi
# da Vault e dai database, Keycloak dal suo Postgres) esce finché quello non
# risponde, e il backoff di Kubernetes (10 s, 20 s, 40 s…) allunga l'avvio di
# minuti. Ogni fase aspetta che la precedente sia pronta.
DATA=(statefulset/postgres-events statefulset/postgres-stands statefulset/postgres-saga
      statefulset/postgres-auth statefulset/postgres-audit statefulset/postgres-keycloak statefulset/kafka
      deployment/vault)

SKIP_BUILD=false
for arg in "$@"; do
    case "$arg" in
        --skip-build) SKIP_BUILD=true ;;
        -h|--help) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Argomento sconosciuto: $arg (vedi --help)" >&2; exit 2 ;;
    esac
done

log()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\nERRORE: %s\n' "$*" >&2; exit 1; }
kc()   { kubectl --context "$CTX" "$@"; }

# --- 0. Prerequisiti ---------------------------------------------------------
log "Prerequisiti"
command -v docker  >/dev/null || die "docker non trovato"
command -v kubectl >/dev/null || die "kubectl non trovato"
command -v "$KIND" >/dev/null || die "kind non trovato: installalo (https://kind.sigs.k8s.io/docs/user/quick-start/#installation) o indica il binario con KIND=/percorso/kind"
docker info >/dev/null 2>&1   || die "il daemon Docker non risponde"
[ -f "$PANEL_DIR/Dockerfile" ] || die "pannello non trovato in $PANEL_DIR: indica il checkout con PANEL_DIR=/percorso/san-martino-user-panel"
# Il Dockerfile del pannello per Kubernetes (nginx non root sulla 8080) porta
# con sé nginx.conf: senza, il pod non rispetterebbe il profilo restricted.
[ -f "$PANEL_DIR/nginx.conf" ] || die "$PANEL_DIR non ha nginx.conf: serve il Dockerfile del pannello per Kubernetes (task #032)"
info "kind $("$KIND" version | awk '{print $2}'), kubectl $(kubectl version --client -o yaml | awk '/gitVersion/ {print $2; exit}')"

# --- 1. Cluster --------------------------------------------------------------
log "Cluster kind '$CLUSTER'"
if "$KIND" get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    info "già presente"
else
    for port in 80 8080; do
        if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
            die "la porta $port di 127.0.0.1 è occupata (un altro servizio, o il gateway avviato da IntelliJ?): liberala e rilancia"
        fi
    done
    "$KIND" create cluster --config "$REPO/k8s/kind/cluster.yaml" --wait 120s
fi
kc cluster-info >/dev/null || die "il context $CTX non risponde"

# --- 2. Segreti locali -------------------------------------------------------
# Generati una volta e riusati: cambiarli a cluster acceso disallineerebbe i
# database, che la password la fissano al primo avvio.
log "Segreti locali ($SECRETS_DIR)"
mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR"
random() { od -An -tx1 -N16 /dev/urandom | tr -d ' \n'; }
secret() {
    local file="$SECRETS_DIR/$1.env"; shift
    if [ -f "$file" ]; then info "$(basename "$file"): esiste, riusato"; return; fi
    printf '%s\n' "$@" > "$file"
    chmod 600 "$file"
    info "$(basename "$file"): generato"
}
secret vault-token "token=$(random)"
for db in events stands saga auth audit keycloak; do
    secret "postgres-$db" "password=$(random)"
done
secret keycloak-admin username=admin "password=$(random)"
secret grafana-admin  username=admin "password=$(random)"

# --- 3. Immagini -------------------------------------------------------------
build() { # immagine contesto [build-arg]
    local image=$1 context=$2; shift 2
    info "build $image"
    # --provenance=false: con il containerd image store, l'attestazione di
    # provenienza (che contiene un timestamp) cambia l'ID dell'immagine a ogni
    # build, anche quando tutto arriva dalla cache. Senza, l'ID cambia solo se
    # cambia il contenuto, ed è quello che il caricamento più sotto confronta.
    # Le immagini pubblicate dalla CI non passano da qui.
    docker build --quiet --provenance=false "$@" -t "$image" "$context" >/dev/null
}
if [ "$SKIP_BUILD" = false ]; then
    log "Build delle immagini (tag $TAG)"
    for svc in "${SERVICES[@]}"; do
        build "$BACKEND_IMAGE/$svc:$TAG" "$REPO" --build-arg "SERVICE=$svc"
    done
    build "$PANEL_IMAGE:$TAG" "$PANEL_DIR"
fi

# Carica nel nodo solo le immagini che lì mancano o sono diverse. I Deployment
# già in esecuzione con un'immagine cambiata vanno riavviati: il tag `dev`
# resta lo stesso, quindi Kubernetes da solo non se ne accorgerebbe.
#
# Il confronto è fra l'ID Docker e DUE valori del nodo, perché cosa sia l'ID
# dipende dallo store di Docker: con quello classico è il digest della config
# (= l'id di crictl), con il containerd image store è il digest dell'indice OCI
# (= il target registrato da containerd). Confrontando un valore solo, una delle
# due configurazioni ricaricherebbe e riavvierebbe tutto a ogni esecuzione.
log "Caricamento delle immagini nel nodo"
CHANGED=()
load() { # immagine deployment
    local image=$1 deployment=$2 want config target
    want="$(docker image inspect -f '{{.Id}}' "$image" 2>/dev/null)" \
        || die "immagine $image assente: lancia lo script senza --skip-build"
    config="$(docker exec "$NODE" crictl inspecti -o go-template --template '{{.status.id}}' "$image" 2>/dev/null || true)"
    target="$(docker exec "$NODE" ctr -n k8s.io images ls "name==$image" 2>/dev/null | awk 'NR==2 {print $3}' || true)"
    if [ "$want" = "$config" ] || [ "$want" = "$target" ]; then
        info "$image: già nel nodo"
    else
        "$KIND" load docker-image --name "$CLUSTER" "$image" >/dev/null
        info "$image: caricata"
        CHANGED+=("$deployment")
    fi
}
for svc in "${SERVICES[@]}"; do load "$BACKEND_IMAGE/$svc:$TAG" "$svc"; done
load "$PANEL_IMAGE:$TAG" user-panel

# --- 4. Controller Ingress ---------------------------------------------------
log "Traefik"
kc apply -k "$REPO/k8s/kind/traefik" >/dev/null
kc -n traefik rollout status deployment/traefik --timeout=180s

# --- 5. Manifest, in tre fasi ------------------------------------------------
# I selettori filtrano gli oggetti dell'overlay per label. `notin` e `!=`
# includono anche gli oggetti che la label non ce l'hanno (Namespace,
# ConfigMap, Secret, Service…), che quindi arrivano già alla prima fase.
EXISTING="$(kc -n "$NS" get deployments -o name 2>/dev/null || true)"

log "Fase 1: database, Kafka, Vault"
kc apply -k "$OVERLAY" -l 'app.kubernetes.io/component notin (backend,identity)' >/dev/null
for r in "${DATA[@]}"; do
    kc -n "$NS" rollout status "$r" --timeout=600s
done

log "Fase 2: Keycloak"
kc apply -k "$OVERLAY" -l 'app.kubernetes.io/component!=backend' >/dev/null
kc -n "$NS" rollout status deployment/keycloak --timeout=600s

log "Fase 3: servizi Spring Boot"
kc apply -k "$OVERLAY" >/dev/null
# ${a[@]+"${a[@]}"}: con set -u, l'array vuoto è un errore su bash 3.2 (macOS).
for dep in ${CHANGED[@]+"${CHANGED[@]}"}; do
    if grep -qx "deployment.apps/$dep" <<<"$EXISTING"; then
        info "immagine cambiata: riavvio $dep"
        kc -n "$NS" rollout restart "deployment/$dep" >/dev/null
    fi
done
for svc in "${SERVICES[@]}" user-panel; do
    kc -n "$NS" rollout status "deployment/$svc" --timeout=600s
done
for r in statefulset/prometheus statefulset/loki statefulset/grafana deployment/alloy; do
    kc -n "$NS" rollout status "$r" --timeout=300s
done

# --- 6. Verifica e riepilogo -------------------------------------------------
log "Verifica dall'host"
FAILED=0
check() { # descrizione atteso url [opzioni di curl]
    local what=$1 want=$2 url=$3 code retries=5 delay=2; shift 3
    while [ "$retries" -gt 0 ]; do
        code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@" "$url" || true)"
        if [ "$code" = "$want" ]; then
            info "OK   $what → $code"
            return 0
        fi
        retries=$((retries - 1))
        [ "$retries" -gt 0 ] && sleep "$delay"
    done
    info "FAIL $what → $code (atteso $want)"; FAILED=1
}
check "API dalla :8080 (Android)      GET /v1/events" 200 http://127.0.0.1:8080/v1/events
check "pannello da sanmartino.local   GET /"          200 http://127.0.0.1/ -H 'Host: sanmartino.local'
check "Grafana da grafana.sanmartino.local"           200 http://127.0.0.1/api/health -H 'Host: grafana.sanmartino.local'
# L'Actuator non è instradato: /actuator/** cade nella regola `/` e risponde
# l'nginx del pannello (fallback SPA, 200 e HTML). Conta che NON sia il JSON
# di un servizio Spring.
if curl -s --max-time 10 http://127.0.0.1/actuator/health | grep -q '"status"'; then
    info "FAIL /actuator/health raggiungibile dall'Ingress"; FAILED=1
else
    info "OK   /actuator/health non esposto dall'Ingress"
fi

hosts_line="127.0.0.1 sanmartino.local grafana.sanmartino.local"
log "Pronto"
cat <<EOF
    Pannello e API     http://sanmartino.local   (o http://localhost)
    API per Android    http://localhost:8080     adb reverse tcp:8080 tcp:8080
    Grafana            http://grafana.sanmartino.local
                       utente e password in $SECRETS_DIR/grafana-admin.env
    kubectl            kubectl --context $CTX -n $NS get pods
    Spegnimento        scripts/k8s-dev-down.sh
EOF
if ! grep -Eq '^[[:space:]]*127\.0\.0\.1[[:space:]].*\bsanmartino\.local\b' /etc/hosts; then
    printf '\n    Manca la riga in /etc/hosts per i nomi .local:\n'
    printf "      echo '%s' | sudo tee -a /etc/hosts\n" "$hosts_line"
fi
if [ "$FAILED" -ne 0 ]; then
    die "almeno una verifica è fallita (vedi sopra)"
fi
