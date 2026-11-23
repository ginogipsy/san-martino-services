/**
 * =============================================================================
 * SAN MARTINO — PULSANTIERA IoT 10 SLOT (3x ESP32 + 5x DISPLAY TM1637)
 * =============================================================================
 * Firmware unico multi-postazione per le 3 schede ESP32 di una cantina:
 *   1. STATION_CASHIER  (Postazione Cassa: 10 pulsanti slot + Conferma + Reset + 1x TM1637)
 *   2. STATION_KITCHEN  (Postazione Cucina: 10 pulsanti slot + 10 LED + 2x TM1637)
 *   3. STATION_DELIVERY (Postazione Consegna/Bancone: 10 pulsanti slot + 10 LED + 2x TM1637)
 *
 * Librerie Arduino richieste:
 *   - WiFi.h & HTTPClient.h (incluse nel core ESP32)
 *   - ArduinoJson (>= 7.x)
 *   - TM1637Display (di Avishay Orpaz)
 *   - Adafruit_NeoPixel (opzionale, per pilotare i 10 LED RGB WS2812B con 1 solo pin GPIO 5)
 * =============================================================================
 */

#include
<WiFi.h>
#include
<HTTPClient.h>
#include
<ArduinoJson.h>
#include
<TM1637Display.h>
#include
<Adafruit_NeoPixel.h>

// =============================================================================
// 1. CONFIGURAZIONE POSTAZIONE, RETE E CANTINA
// =============================================================================
enum StationRole {
STATION_CASHIER  = 1, // ESP32 #1 — Cassa
STATION_KITCHEN  = 2, // ESP32 #2 — Cucina
STATION_DELIVERY = 3  // ESP32 #3 — Consegna / Bancone
};

// ---> IMPOSTA QUI IL RUOLO DI QUESTA SCHEDA ESP32 <---
const StationRole CURRENT_STATION = STATION_DELIVERY;

const char* WIFI_SSID     = "SAN_MARTINO_STAFF_WIFI";
const char* WIFI_PASSWORD = "sanmartino-password";

// Endpoint Gateway / stands-service e credenziali hardware generate dal pannello web
const char* API_BASE_URL  = "http://192.168.1.100:8080/v1/stands";
const char* STAND_ID      = "22222222-2222-2222-2222-000000000001";
const char* DEVICE_TOKEN  = "ESP32-CANTINA-01-TOKEN";

// =============================================================================
// 2. PINOUT HARDWARE ESP32 (10 PULSANTI SLOT + CONFERMA/RESET + 2x TM1637 + 10 LED)
// =============================================================================
const int NUM_SLOTS = 10;

// 10 Pulsanti Slot (collegati verso GND; GPIO 34, 35, 39, 36 richiedono pull-up esterna da 10kOhm a 3.3V)
const int SLOT_BUTTON_PINS[NUM_SLOTS] = {
13, // Slot 1  (es. Piatto 1)
14, // Slot 2  (es. Piatto 2)
27, // Slot 3  (es. Piatto 3)
26, // Slot 4  (es. Piatto 4)
25, // Slot 5  (es. Piatto 5)
33, // Slot 6  (es. Piatto 6)
32, // Slot 7  (es. Vino Rosso / Calice)
35, // Slot 8  (es. Vino Bianco / Birra) [ext 10k pull-up]
34, // Slot 9  (es. Dolce / Castagne)   [ext 10k pull-up]
39  // Slot 10 (es. Acqua / Extra)      [ext 10k pull-up]
};

// Pulsanti extra per la Postazione CASSA (Conferma Ordine e Annulla/Reset selezione)
const int PIN_BTN_CONFIRM = 36; // [ext 10k pull-up]
const int PIN_BTN_CANCEL  = 4;  // Pull-up interna

// Display 7-Segmenti 4 cifre TM1637 (Clock condiviso su GPIO 18)
const int PIN_TM1637_CLK  = 18;
const int PIN_TM1637_DIO1 = 19; // Display 1 (presente su Cassa, Cucina e Consegna)
const int PIN_TM1637_DIO2 = 21; // Display 2 (presente su Cucina e Consegna)

// Striscia 10 LED RGB indirizzabili WS2812B (1 LED accanto a ciascuno dei 10 pulsanti slot)
const int PIN_NEOPIXEL_LEDS = 5;

TM1637Display display1(PIN_TM1637_CLK, PIN_TM1637_DIO1);
TM1637Display display2(PIN_TM1637_CLK, PIN_TM1637_DIO2);
Adafruit_NeoPixel strip(NUM_SLOTS, PIN_NEOPIXEL_LEDS, NEO_GRB + NEO_KHZ800);

// =============================================================================
// 3. STATO LOCALE DELLA POSTAZIONE
// =============================================================================
int cashierDraftQty[NUM_SLOTS] = {
0
};
bool kitchenLedOn[NUM_SLOTS] = {
false
};
// 0 = OFF, 1 = BLINKING (in attesa cucina), 2 = SOLID (pronto da servire/premere)
int deliveryLedState[NUM_SLOTS] = {
0
};

int lastIssuedOrderNumber      = 0;
int oldestKitchenOrderNumber   = 0;
int totalPendingKitchenDishes  = 0;
int currentDeliveryOrderNumber = 0;
int lastDeliveredOrderNumber   = 0;

bool lastSlotBtnState[NUM_SLOTS];
bool lastConfirmBtnState = HIGH;
bool lastCancelBtnState  = HIGH;

unsigned long lastPollTime  = 0;
unsigned long lastBlinkTime = 0;
bool blinkPhaseOn           = true;

const unsigned long POLL_INTERVAL_MS  = 1500;
const unsigned long BLINK_INTERVAL_MS = 350;

// =============================================================================
// 4. INIZIALIZZAZIONE
// =============================================================================
void setup() {
Serial.begin(115200);

for (int i = 0; i < NUM_SLOTS; i++) {
int pin = SLOT_BUTTON_PINS[i];
if (pin >= 34) {
pinMode(pin, INPUT); // Pull-up esterna 10kOhm
} else {
pinMode(pin, INPUT_PULLUP);
}
lastSlotBtnState[i] = digitalRead(pin);
}

pinMode(PIN_BTN_CONFIRM, INPUT);
pinMode(PIN_BTN_CANCEL, INPUT_PULLUP);

display1.setBrightness(0x0f);
display2.setBrightness(0x0f);
display1.showNumberDec(0, true);
display2.showNumberDec(0, true);

strip.begin();
strip.clear();
strip.show();

WiFi.mode(WIFI_STA);
WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
Serial.print("Connessione Wi-Fi in corso");
while (WiFi.status() != WL_CONNECTED) {
delay(400);
Serial.print(".");
}
Serial.println("\nWi-Fi connesso! IP: " + WiFi.localIP().toString());

fetchKeypadState();
}

// =============================================================================
// 5. LOOP PRINCIPALE
// =============================================================================
void loop() {
unsigned long now = millis();

// Gestione lampeggio LED (per Consegna in attesa della Cucina)
if (now - lastBlinkTime >= BLINK_INTERVAL_MS) {
lastBlinkTime = now;
blinkPhaseOn = !blinkPhaseOn;
refreshLeds();
}

// Lettura dei 10 pulsanti slot
for (int i = 0; i < NUM_SLOTS; i++) {
bool reading = digitalRead(SLOT_BUTTON_PINS[i]);
if (lastSlotBtnState[i] == HIGH && reading == LOW) {
delay(35); // Debounce
if (digitalRead(SLOT_BUTTON_PINS[i]) == LOW) {
onSlotButtonPressed(i + 1);
}
}
lastSlotBtnState[i] = reading;
}

// Lettura pulsanti Conferma / Annulla (usati sulla Postazione CASSA)
if (CURRENT_STATION == STATION_CASHIER) {
bool confirmRead = digitalRead(PIN_BTN_CONFIRM);
if (lastConfirmBtnState == HIGH && confirmRead == LOW) {
delay(35);
if (digitalRead(PIN_BTN_CONFIRM) == LOW) {
submitCashierOrder();
}
}
lastConfirmBtnState = confirmRead;

bool cancelRead = digitalRead(PIN_BTN_CANCEL);
if (lastCancelBtnState == HIGH && cancelRead == LOW) {
delay(35);
if (digitalRead(PIN_BTN_CANCEL) == LOW) {
clearCashierDraft();
}
}
lastCancelBtnState = cancelRead;
}

// Polling periodico dello stato dal backend
if (now - lastPollTime >= POLL_INTERVAL_MS) {
lastPollTime = now;
fetchKeypadState();
}
}

// =============================================================================
// 6. LOGICA PRESSIONE PULSANTI PER POSTAZIONE
// =============================================================================
void onSlotButtonPressed(int slotNumber) {
int idx = slotNumber - 1;
Serial.printf("Premuto pulsante Slot %d\n", slotNumber);

if (CURRENT_STATION == STATION_CASHIER) {
// CASSA: aggiunge +1 porzione dello slot all'ordine in composizione
cashierDraftQty[idx]++;
int totalItems = 0;
for (int i = 0; i < NUM_SLOTS; i++) totalItems += cashierDraftQty[i];
display1.showNumberDec(totalItems, false);
refreshLeds();
} else if (CURRENT_STATION == STATION_KITCHEN) {
// CUCINA: segna 1 porzione pronta per lo slot premuto
sendSlotPress("/keypad/kitchen/press", slotNumber);
} else if (CURRENT_STATION == STATION_DELIVERY) {
// CONSEGNA: segna 1 porzione servita sul vassoio per l'ordine corrente;
// quando tutti i piatti dell'ordine corrente sono stati premuti, il backend
// avanza automaticamente al prossimo numero d'ordine!
sendSlotPress("/keypad/delivery/press", slotNumber);
}
}

void clearCashierDraft() {
for (int i = 0; i < NUM_SLOTS; i++) {
cashierDraftQty[i] = 0;
}
display1.showNumberDec(lastIssuedOrderNumber, true);
refreshLeds();
}

// =============================================================================
// 7. CHIAMATE HTTP REST AL BACKEND (stands-service)
// =============================================================================
String stationQueryParam() {
if (CURRENT_STATION == STATION_CASHIER) return "CASHIER";
if (CURRENT_STATION == STATION_KITCHEN) return "KITCHEN";
return "DELIVERY";
}

void fetchKeypadState() {
if (WiFi.status() != WL_CONNECTED) return;

HTTPClient http;
String url = String(API_BASE_URL) + "/" + STAND_ID + "/keypad/state?station=" + stationQueryParam();
http.begin(url);
http.addHeader("X-Device-Token", DEVICE_TOKEN);

int code = http.GET();
if (code == 200) {
parseAndApplyState(http.getString());
}
http.end();
}

void submitCashierOrder() {
int totalItems = 0;
for (int i = 0; i < NUM_SLOTS; i++) totalItems += cashierDraftQty[i];
if (totalItems == 0) return;

JsonDocument doc;
JsonArray items = doc["items"].to<JsonArray>();
for (int i = 0; i < NUM_SLOTS; i++) {
if (cashierDraftQty[i] > 0) {
JsonObject item = items.add<JsonObject>();
item["slotNumber"] = i + 1;
item["quantity"]   = cashierDraftQty[i];
}
}

String body;
serializeJson(doc, body);

HTTPClient http;
String url = String(API_BASE_URL) + "/" + STAND_ID + "/keypad/orders";
http.begin(url);
http.addHeader("Content-Type", "application/json");
http.addHeader("X-Device-Token", DEVICE_TOKEN);

int code = http.POST(body);
if (code == 200 || code == 201) {
for (int i = 0; i < NUM_SLOTS; i++) cashierDraftQty[i] = 0;
parseAndApplyState(http.getString());
}
http.end();
}

void sendSlotPress(const char* pathSuffix, int slotNumber) {
if (WiFi.status() != WL_CONNECTED) return;

JsonDocument doc;
doc["slotNumber"] = slotNumber;
doc["quantity"]   = 1;

String body;
serializeJson(doc, body);

HTTPClient http;
String url = String(API_BASE_URL) + "/" + STAND_ID + pathSuffix;
http.begin(url);
http.addHeader("Content-Type", "application/json");
http.addHeader("X-Device-Token", DEVICE_TOKEN);

int code = http.POST(body);
if (code == 200) {
parseAndApplyState(http.getString());
}
http.end();
}

void parseAndApplyState(const String& payload) {
JsonDocument doc;
if (deserializeJson(doc, payload)) return;

lastIssuedOrderNumber      = doc["lastIssuedOrderNumber"] | 0;
oldestKitchenOrderNumber   = doc["oldestKitchenOrderNumber"] | 0;
totalPendingKitchenDishes  = doc["totalPendingKitchenPortions"] | 0;
currentDeliveryOrderNumber = doc["currentDeliveryOrderNumber"] | 0;
lastDeliveredOrderNumber   = doc["lastDeliveredOrderNumber"] | 0;

JsonArray slots = doc["slots"].as<JsonArray>();
for (JsonObject s : slots) {
int slotNum = s["slotNumber"] | 0;
if (slotNum < 1 || slotNum > NUM_SLOTS) continue;
int idx = slotNum - 1;
kitchenLedOn[idx] = s["kitchenLedOn"] | false;
const char* delState = s["deliveryLedState"] | "OFF";
if (strcmp(delState, "SOLID") == 0) {
deliveryLedState[idx] = 2;
} else if (strcmp(delState, "BLINKING") == 0) {
deliveryLedState[idx] = 1;
} else {
deliveryLedState[idx] = 0;
}
}

refreshDisplays();
refreshLeds();
}

// =============================================================================
// 8. AGGIORNAMENTO DISPLAY 7-SEGMENTI TM1637 E 10 LED RGB
// =============================================================================
void refreshDisplays() {
if (CURRENT_STATION == STATION_CASHIER) {
int draftCount = 0;
for (int i = 0; i < NUM_SLOTS; i++) draftCount += cashierDraftQty[i];
if (draftCount > 0) {
display1.showNumberDec(draftCount, false);
} else {
display1.showNumberDec(lastIssuedOrderNumber, true);
}
} else if (CURRENT_STATION == STATION_KITCHEN) {
display1.showNumberDec(oldestKitchenOrderNumber, true);
display2.showNumberDec(totalPendingKitchenDishes, false);
} else if (CURRENT_STATION == STATION_DELIVERY) {
display1.showNumberDec(currentDeliveryOrderNumber, true);
display2.showNumberDec(lastDeliveredOrderNumber, true);
}
}

void refreshLeds() {
for (int i = 0; i < NUM_SLOTS; i++) {
uint32_t color = 0; // Spento

if (CURRENT_STATION == STATION_CASHIER) {
if (cashierDraftQty[i] > 0) {
color = strip.Color(0, 180, 255); // Azzurro: piatto selezionato nello scontrino corrente
}
} else if (CURRENT_STATION == STATION_KITCHEN) {
if (kitchenLedOn[i]) {
color = strip.Color(255, 60, 0); // Arancione/Rosso fisso: piatto da cucinare!
}
} else if (CURRENT_STATION == STATION_DELIVERY) {
if (deliveryLedState[i] == 2) {
color = strip.Color(0, 230, 60); // Verde fisso: piatto/vino pronto da servire e premere!
} else if (deliveryLedState[i] == 1 && blinkPhaseOn) {
color = strip.Color(255, 160, 0); // Giallo lampeggiante: nell'ordine corrente ma ancora in cottura
}
}

strip.setPixelColor(i, color);
}
strip.show();
}
