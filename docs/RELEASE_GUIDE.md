# Guida ai Rilasci e Versionamento (Backend)

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

## 🤖 Cosa succede dietro le quinte?

Ad ogni merge su `master`, il workflow di GitHub:
1. Determina la versione corretta tramite **Smart Versioning**.
2. Crea un **Tag Git** (es. `v0.1.0`).
3. Crea una **GitHub Release**.
4. **Aggiorna `develop`**: Usa `mvn versions:set` per impostare la versione successiva su `develop` e fa il push automatico.

> [!IMPORTANT]
> Non modificare mai manualmente la versione nei `pom.xml` dei singoli sottomoduli. Se devi fare un cambio manuale, fallo solo nel `pom.xml` root o usa `./mvnw versions:set`.
