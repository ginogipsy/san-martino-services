#!/bin/sh
# ---------------------------------------------------------------------------
# Secret KV del cluster LOCALE: l'equivalente di vault-init nel
# docker-compose.yml, con gli indirizzi DNS del cluster al posto di localhost.
#
# Solo ciò che è segreto o cambia fra ambienti. Gli URL fra i servizi stanno
# nella ConfigMap sanmartino-topology (k8s/base) e qui non si ripetono: per
# questo non c'è un secret/gateway, che non ha niente da leggere. Un path
# assente non blocca l'avvio (Spring Cloud Vault lo segnala a INFO, "Not found").
#
# Le password arrivano dall'ambiente, cioè dagli stessi Secret che usano i
# Postgres: un solo valore, nessuna copia da tenere allineata.
#
# NB: `vault kv put` interpreta un valore che inizia con @ come un file da
# leggere. Le password generate per il cluster locale sono alfanumeriche.
# ---------------------------------------------------------------------------
set -eu

vault kv put secret/auth-service \
    spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/san-martino \
    spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8080/realms/san-martino/protocol/openid-connect/certs \
    spring.datasource.url=jdbc:postgresql://postgres-auth:5432/auth \
    spring.datasource.username=auth \
    spring.datasource.password="$AUTH_DB_PASSWORD" \
    keycloak.server-url=http://keycloak:8080 \
    keycloak.realm=san-martino \
    keycloak.client-id=admin-cli-wrapper \
    keycloak.client-secret="${KEYCLOAK_CLIENT_SECRET:-public-client}"
# Il client admin-cli-wrapper del realm è PUBBLICO: Keycloak ignora il secret,
# ma la property deve esistere perché AuthService la inietta. Se il client
# diventa confidential, il valore va messo nel Secret keycloak-client.

vault kv put secret/events-service \
    spring.datasource.url=jdbc:postgresql://postgres-events:5432/events \
    spring.datasource.username=events \
    spring.datasource.password="$EVENTS_DB_PASSWORD"

vault kv put secret/stands-service \
    spring.datasource.url=jdbc:postgresql://postgres-stands:5432/stands \
    spring.datasource.username=stands \
    spring.datasource.password="$STANDS_DB_PASSWORD"

vault kv put secret/saga-orchestrator \
    spring.datasource.url=jdbc:postgresql://postgres-saga:5432/saga \
    spring.datasource.username=saga \
    spring.datasource.password="$SAGA_DB_PASSWORD" \
    spring.kafka.bootstrap-servers=kafka:9092

vault kv put secret/notifications-service \
    spring.kafka.bootstrap-servers=kafka:9092 \
    sanmartino.fcm.credentials-path=/var/run/secrets/fcm/service-account.json
# FCM è spento (sanmartino.fcm.enabled=false): il path serve solo a risolvere
# il placeholder. Quando si accende, il file va montato lì da un Secret.

vault kv put secret/audit-service \
    spring.datasource.url=jdbc:postgresql://postgres-audit:5432/audit \
    spring.datasource.username=audit \
    spring.datasource.password="$AUDIT_DB_PASSWORD" \
    spring.kafka.bootstrap-servers=kafka:9092

# Marcatore scritto per ULTIMO: se esiste, tutti i put sopra sono andati a buon
# fine (set -e). È quello che controllano il ciclo e la readiness del seed.
vault kv put secret/vault-seed seeded-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "Secret KV del cluster locale scritti"
