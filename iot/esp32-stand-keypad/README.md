# Pulsantiera IoT Cantina — 3x ESP32 + 5x Display TM1637 + 10 Slot LED/Pulsanti

Architettura hardware e software per la gestione sincronizzata di **Cassa**, **Cucina** e **Consegna (Bancone)** di ogni
cantina della festa **Le Cantine di San Martino (Arce)**.

---

## 1. Architettura delle 3 Postazioni ESP32 e 5 Display TM1637

Ogni cantina dispone di **10 Slot (`1..10`)** associabili liberamente dal pannello web ai piatti (`FOOD`) e alle bevande
(`DRINK`) della cantina:

| Postazione                                                | Hardware                                                                                                             | Funzionamento                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|:----------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1. Postazione CASSA** (`STATION_CASHIER`)               | **1x ESP32** + **10 Pulsanti Slot** (con LED) + **Pulsante Conferma** + **Pulsante Annulla** + **1x Display TM1637** | L'operatore clicca i pulsanti dei piatti ordinati dal cliente (es. `Piatto 1` + `Piatto 1` + `Vino` -> i LED degli slot selezionati si illuminano e il display mostra il totale pezzi). Premendo **CONFERMA**, il sistema scala le porzioni disponibili (`availablePlates`), assegna il numero d'ordine progressivo (es. **`0001`**) e lo mostra sul display per scriverlo sullo scontrino del cliente.                                                                                                                                                                                                                                                                                                                                                                                              |
| **2. Postazione CUCINA** (`STATION_KITCHEN`)              | **1x ESP32** + **10 Pulsanti Slot** + **10 LED** + **2x Display TM1637**                                             | **Display 1**: Numero dell'ordine più vecchio in cottura (`0001`). **Display 2**: Totale porzioni calde da preparare in coda. I **LED** corrispondenti ai piatti da cucinare restano **ACCESI** finché la cucina non ha preparato tutte le porzioni richieste. Nell'esempio (`2x Piatto 1`), il cuoco preme una volta `Piatto 1` (1 porzione pronta -> LED resta acceso) e lo ripreme alla seconda porzione pronta (0 rimanenti -> **LED si spegne**).                                                                                                                                                                                                                                                                                                                                               |
| **3. Postazione CONSEGNA / BANCONE** (`STATION_DELIVERY`) | **1x ESP32** + **10 Pulsanti Slot** + **10 LED** + **2x Display TM1637**                                             | **Display 1**: Numero dell'ordine corrente da comporre e servire (es. **`0001`**). **Display 2**: Ultimo ordine completato. Per l'Ordine `#1` (`2x Piatto 1 + 1x Vino`), i LED di `Piatto 1` e `Vino` sono accesi (**Verde fisso** quando pronti da impiattare/versare, **Ambra lampeggiante** se la cucina sta ancora cuocendo il piatto). Il consegnatore mette sul vassoio il primo `Piatto 1` e preme il pulsante `1` -> **il LED 1 resta acceso** (`1/2`); mette il secondo `Piatto 1` e ripreme il pulsante `1` -> **il LED 1 si spegne** (`2/2`); serve il `Vino` e preme il pulsante `7` -> **il LED 7 si spegne** (`1/1`). Non appena tutti i LED dell'Ordine `#1` sono spenti, il display passa **automaticamente all'Ordine `#2` (`0002`)** accendendo i LED dei piatti dell'Ordine `#2`! |

---

## 2. Schema Pinout ESP32 (30 / 38 Pin DevKit V1)

```text
+-----------------------------------------------------------------------+
|                       ESP32 DEVKIT V1 PINOUT                          |
+-------------------+-----------------------+---------------------------+
| Componente        | Pin ESP32             | Note                      |
+-------------------+-----------------------+---------------------------+
| Pulsante Slot 1   | GPIO 13               | INPUT_PULLUP verso GND    |
| Pulsante Slot 2   | GPIO 14               | INPUT_PULLUP verso GND    |
| Pulsante Slot 3   | GPIO 27               | INPUT_PULLUP verso GND    |
| Pulsante Slot 4   | GPIO 26               | INPUT_PULLUP verso GND    |
| Pulsante Slot 5   | GPIO 25               | INPUT_PULLUP verso GND    |
| Pulsante Slot 6   | GPIO 33               | INPUT_PULLUP verso GND    |
| Pulsante Slot 7   | GPIO 32               | INPUT_PULLUP verso GND    |
| Pulsante Slot 8   | GPIO 35               | Pull-up esterna 10k a 3V3 |
| Pulsante Slot 9   | GPIO 34               | Pull-up esterna 10k a 3V3 |
| Pulsante Slot 10  | GPIO 39 (VN)          | Pull-up esterna 10k a 3V3 |
+-------------------+-----------------------+---------------------------+
| Pulsante CONFERMA | GPIO 36 (VP)          | Solo Cassa (ext 10k 3V3)  |
| Pulsante ANNULLA  | GPIO 4                | Solo Cassa (INPUT_PULLUP) |
+-------------------+-----------------------+---------------------------+
| 10x LED WS2812B   | GPIO 5                | 10 LED RGB in cascata     |
| TM1637 CLK        | GPIO 18               | Clock condiviso Display   |
| TM1637 Display 1  | GPIO 19 (DIO1)        | Cassa / Cucina / Consegna |
| TM1637 Display 2  | GPIO 21 (DIO2)        | Cucina / Consegna         |
+-------------------+-----------------------+---------------------------+
```

---

## 3. Permessi Web e Hardware

- **Autenticazione Web Panel (`/pulsantiera`)**:
    - Il proprietario della cantina (`OWNER`, es. `francesco-grossi` per `gino-cantina`) o un amministratore
      (`ROLE_ADMIN` / `ROLE_MEZZ`) può assegnare permessi specifici per postazione a qualsiasi utente registrato (per
      `username`):
        - `CASHIER` (es. `maria-chiara`): abilitata a usare la **Postazione Cassa** della cantina.
        - `KITCHEN`: abilitato a usare la **Postazione Cucina** della cantina.
        - `DELIVERY` (es. `scricci`): abilitato a usare la **Postazione Consegna** della cantina.
        - `VIEWER` (es. `lisa-la`): abilitata a visualizzare in sola lettura il tabellone ordini e le disponibilità
          della cantina.
- **Autenticazione ESP32 (`X-Device-Token`)**:
    - Le 3 schede ESP32 inviano l'header `X-Device-Token` generato dal pannello di configurazione della cantina.
