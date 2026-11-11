#!/bin/sh
# ---------------------------------------------------------------------------
# Configurazione di Vault per i servizi San Martino: mount KV, una policy per
# servizio, auth method Kubernetes con un ruolo per servizio.
#
# NON contiene segreti e NON è specifico del cluster locale: è lo stesso script
# da lanciare contro il Vault di produzione, con un token che possa scrivere
# policy e auth method:
#
#   VAULT_ADDR=https://vault.example.org VAULT_TOKEN=… sh configure.sh
#
# Idempotente: si può rilanciare dopo ogni modifica.
#
# Variabili (tutte con un default):
#   SANMARTINO_NAMESPACE   namespace dei servizi                  (sanmartino)
#   VAULT_K8S_AUDIENCE     audience del token montato nei pod     (vault)
#   VAULT_K8S_HOST         API server, visto da Vault             (https://kubernetes.default.svc)
#   VAULT_K8S_CA_CERT      file col CA dell'API server            (vuoto = Vault gira nel cluster)
#   VAULT_K8S_REVIEWER_JWT file col token per la TokenReview      (vuoto = Vault gira nel cluster)
#
# Con Vault DENTRO il cluster, CA e token del reviewer sono quelli della sua
# ServiceAccount (letti da /var/run/secrets/kubernetes.io/serviceaccount), che
# deve avere il ClusterRole system:auth-delegator. Con Vault FUORI dal cluster
# vanno passati gli ultimi due file.
# ---------------------------------------------------------------------------
set -eu

NAMESPACE="${SANMARTINO_NAMESPACE:-sanmartino}"
AUDIENCE="${VAULT_K8S_AUDIENCE:-vault}"
K8S_HOST="${VAULT_K8S_HOST:-https://kubernetes.default.svc}"

# Il nome del servizio è anche: path KV, policy, ruolo e ServiceAccount.
SERVICES="gateway auth-service events-service stands-service saga-orchestrator notifications-service"

# --- KV v2 su secret/ (in dev mode esiste già) -------------------------------
if ! vault secrets list -format=json | grep -q '"secret/"'; then
    vault secrets enable -path=secret -version=2 kv
fi

# --- Una policy per servizio: solo lettura del proprio path ------------------
# `secret/data/<svc>/*` copre i path per profilo (secret/<svc>/<profile>),
# letti da Spring Cloud Vault quando un profilo Spring è attivo.
for svc in $SERVICES; do
    vault policy write "$svc" - <<EOF
path "secret/data/$svc" {
  capabilities = ["read"]
}
path "secret/data/$svc/*" {
  capabilities = ["read"]
}
EOF
done

# --- Auth method Kubernetes ----------------------------------------------------
if ! vault auth list -format=json | grep -q '"kubernetes/"'; then
    vault auth enable kubernetes
fi

if [ -n "${VAULT_K8S_CA_CERT:-}" ] && [ -n "${VAULT_K8S_REVIEWER_JWT:-}" ]; then
    vault write auth/kubernetes/config \
        kubernetes_host="$K8S_HOST" \
        kubernetes_ca_cert=@"$VAULT_K8S_CA_CERT" \
        token_reviewer_jwt=@"$VAULT_K8S_REVIEWER_JWT"
else
    vault write auth/kubernetes/config kubernetes_host="$K8S_HOST"
fi

# Un ruolo per servizio, legato alla SOLA ServiceAccount omonima nel namespace e
# all'audience del token proiettato nei pod: il token di events-service non
# apre il ruolo di stands-service, e un token per l'API di Kubernetes (audience
# diversa) non apre nessun ruolo.
for svc in $SERVICES; do
    vault write "auth/kubernetes/role/$svc" \
        bound_service_account_names="$svc" \
        bound_service_account_namespaces="$NAMESPACE" \
        audience="$AUDIENCE" \
        token_policies="$svc" \
        token_ttl=1h \
        token_max_ttl=24h
done

echo "Vault configurato: policy e ruoli Kubernetes per $SERVICES"
