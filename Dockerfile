# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Immagine di UN servizio del monorepo, scelto con --build-arg SERVICE.
#
#   docker build --build-arg SERVICE=events-service \
#                -t ghcr.io/ginogipsy/sanmartino/events-service:<versione> .
#
# Il context è la radice del repo, non la cartella del modulo: Maven ha bisogno
# del parent pom e di common-observability per costruire qualunque servizio.
#
# Perché un Dockerfile e non `spring-boot:build-image`: l'approccio Dockerfile
# multi-stage estrae i jar a layer (spring-boot-loader, dependencies, application)
# garantendo build e caching deterministici e indipendenti dal builder Paketo.
# ---------------------------------------------------------------------------

ARG JAVA_VERSION=25

# --- Stage 1: build --------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build
ARG SERVICE
RUN test -n "${SERVICE}" || (echo "--build-arg SERVICE=<modulo> è obbligatorio" >&2; exit 1)
WORKDIR /workspace

COPY . .
# ~/.m2 in una cache mount: le dipendenze sopravvivono fra una build e l'altra
# (e fra i sei servizi) senza finire in un layer dell'immagine.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl ":${SERVICE}" -am -DskipTests package

# Il jar estratto a layer: dipendenze, loader e codice del servizio diventano
# layer separati, e un cambio di codice non ritrasferisce le dipendenze.
RUN java -Djarmode=tools -jar "${SERVICE}/target/${SERVICE}-"*.jar \
         extract --layers --launcher --destination /extracted

# --- Stage 2: runtime ------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre

# Utente non root con UID numerico, così il pod può dichiarare runAsNonRoot e
# Kubernetes è in grado di verificarlo.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app

COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

# LOG_DIR di default è ./logs, relativo alla working directory: deve esistere ed
# essere scrivibile dall'utente del processo. In K8s, con il filesystem in sola
# lettura, ci si monta sopra un emptyDir.
RUN mkdir logs && chown app:app logs
USER 10001

# Dimensiona la heap sul limite di memoria del container invece che sulla RAM del
# nodo. È un default: JAVA_TOOL_OPTIONS si sovrascrive dal Deployment.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Nessun EXPOSE: la porta dipende dal servizio (server.port in application.yaml)
# e la dichiara il Deployment.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
