#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Elimina il cluster kind di San Martino, con tutti i suoi dati (database,
# Kafka, Loki, Prometheus vivono nei volumi del cluster).
#
#   scripts/k8s-dev-down.sh           # elimina il cluster, tiene i segreti locali
#   scripts/k8s-dev-down.sh --purge   # elimina anche k8s/overlays/local/secrets
#
# I segreti restano di default: il prossimo k8s-dev-up.sh li riusa, e le
# credenziali (Grafana, Keycloak) non cambiano a ogni ricreazione.
#
# Variabili: KIND  binario di kind (default: `kind` nel PATH)
# ---------------------------------------------------------------------------
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER=sanmartino
SECRETS_DIR="$REPO/k8s/overlays/local/secrets"
KIND="${KIND:-kind}"

PURGE=false
for arg in "$@"; do
    case "$arg" in
        --purge) PURGE=true ;;
        -h|--help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Argomento sconosciuto: $arg (vedi --help)" >&2; exit 2 ;;
    esac
done

command -v "$KIND" >/dev/null || { echo "kind non trovato: indica il binario con KIND=/percorso/kind" >&2; exit 1; }

if "$KIND" get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    "$KIND" delete cluster --name "$CLUSTER"
else
    echo "Nessun cluster kind '$CLUSTER': niente da eliminare."
fi

if [ "$PURGE" = true ] && [ -d "$SECRETS_DIR" ]; then
    rm -rf "$SECRETS_DIR"
    echo "Segreti locali eliminati ($SECRETS_DIR)."
fi
