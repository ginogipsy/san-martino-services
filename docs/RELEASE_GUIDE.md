# Guida ai Rilasci e Versionamento (Backend)

> [!IMPORTANT]
> **Pulsante "Approve" (Terzo Pallino)**
> Se sui branch di sviluppo la pipeline si ferma senza mostrare il tasto "Review deployments", significa che l'ambiente non è configurato.
> **Assicurati di aver creato l'environment `san-martino-registry`** in *Settings -> Environments* con la regola *Required reviewers* attiva (aggiungendo il tuo nome utente). Senza questo passaggio, il deploy manuale non può funzionare.

Questa guida spiega come gestire il ciclo di vita dei microservizi, i rilasci su GitHub e il sistema di versionamento automatico Maven.

## 📌 Regole del Versionamento

Il backend utilizza il **Semantic Versioning** all'interno dei file `pom.xml`.

- **Major (X.0.0)**: Cambiamenti breaking nelle API o refactoring strutturali.
- **Minor (0.X.0)**: Nuovi endpoint, nuovi servizi o nuove funzionalità business.
- **Patch (0.0.X)**: Correzioni di bug, aggiornamenti di sicurezza, miglioramenti interni.

## 🚀 Come effettuare un Rilascio

Il processo segue il modello **Git Flow**. Hai tre modi per decidere la versione del prossimo rilascio:

### 1. Metodo Standard (Patch automatica)
Se devi solo rilasciare dei bug fix:
1. Crea una Pull Request da `develop` a `master`.
2. Fai il Merge.
3. **Risultato**: Il bot incrementerà automaticamente la **Patch** (es. `0.0.1` -> `0.0.2`).

### 2. Tramite Etichette PR (Consigliato per Minor/Major)
Se stai rilasciando nuove funzionalità API:
1. Crea la Pull Request verso `master`.
2. Su GitHub, aggiungi l'etichetta (label) **`minor`** o **`major`**.
3. Fai il Merge.
4. **Risultato**: Il bot incrementerà la versione Maven in base all'etichetta.

### 3. Tramite Nome del Branch (Git Flow rigido)
Se preferisci specificare la versione nel branch:
1. Crea un branch chiamato `release/0.1.0` (o `release/v0.1.0`).
2. Apri la PR verso `master` e fai il merge.
3. **Risultato**: Il bot forzerà esattamente la versione `0.1.0` in tutti i moduli.

---

## 📦 Rilascio dell'Immagine Docker

La pubblicazione delle immagini Docker sul registry segue una logica differenziata per garantire velocità in produzione e controllo nello sviluppo.

### 🚀 Pubblicazione Automatica (Continuous Deployment)
L'immagine viene creata e pushata **automaticamente** in due casi:
- **Merge su `master`**: L'immagine viene taggata come `latest`.
- **Creazione di un Tag Git (`v*`)**: L'immagine viene taggata con la versione corrispondente (es. `1.2.0`).

### ✋ Pubblicazione Manuale (Il "Terzo Pallino")
Sui branch di sviluppo (**`develop`**, **`feature/*`**, **`release/*`**), la pipeline si ferma dopo i test:
1. Vai nella tab **Actions** su GitHub.
2. Clicca sulla run corrente della pipeline.
3. Vedrai un pulsante **"Review deployments"**.
4. Clicca su **Approve** (per l'environment `san-martino-registry`).
5. **Risultato**: Le immagini verranno pubblicate con il **nome del branch** (es. `feature-008-permessi`), pronte per essere scaricate su una VM di test.

## 🧪 Qualità del Codice (SpotBugs & PMD)

Ogni commit ed ogni Pull Request viene analizzata gratuitamente da **SpotBugs** e **PMD**
(`.github/workflows/code-quality.yml`, compatibile con Java 25 e senza necessità di token esterni).

- Il riepilogo per modulo viene pubblicato direttamente nel **Job Summary** e nelle **Annotations** della Pull Request.
- I report completi (`pmd.html`, `spotbugsXml.xml`, `spotbugsSarif.json`) sono scaricabili come artifact della run.

---

## 🤖 Cosa succede dietro le quinte?

Ad ogni merge su `master`, il workflow di GitHub:

1. Determina la versione corretta tramite **Smart Versioning** (da branch `release/X.Y.Z`, etichette `major`/`minor` o
   `<revision>` del `pom.xml`).
2. Costruisce e pubblica su GHCR le immagini dei 7 microservizi con la versione risolta.
3. Crea un **Tag Git** (es. `v2.1.0`) e la relativa **GitHub Release**.
4. **Aggiorna `develop`**: Usa `mvn versions:set-property -Dproperty=revision` per impostare automaticamente la patch
   successiva su `develop` e fa il push.

> [!IMPORTANT]
> **Configurazione Ambiente**: L'approvazione manuale dei deploy sui branch di sviluppo richiede che sia configurato
l'environment **`san-martino-registry`** nelle impostazioni del repository (*Settings -> Environments*) con la regola
*Required reviewers* attiva.
