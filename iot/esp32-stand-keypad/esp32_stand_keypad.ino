/**
 * =============================================================================
 * SAN MARTINO — PULSANTIERA IoT 10 SLOT (3x ESP32 + 5x DISPLAY TM1637)
 * =============================================================================
 * Firmware unico multi-postazione per le 3 schede ESP32 di una cantina:
 *   1. STATION_CASHIER  (Postazione Cassa: 10 pulsanti slot + Conferma + Reset + 1x TM1637)
 *   2. STATION_KITCHEN  (Postazione Cucina: 10 pulsanti slot + 10 LED + 2x TM1637)
 *   3. STATION_DELIVERY (Postazione Consegna/Bancone: 10 pulsanti slot + 10 LED + 2x TM1637)
 *
 * Supporta il caricamento diretto dei parametri dal Web Panel tramite USB
 * (Web Serial API a 115200 baud -> salvataggio persistente in memoria Flash NVS
 * tramite <Preferences.h>, senza dover ricompilare su Arduino IDE!).
 * =============================================================================
 */

#include
<WiFi.h>
#include
<HTTPClient.h>
#include
<Preferences.h>
#include
<ArduinoJson.h>
#include
<TM1637Display.h>
#include
<Adafruit_NeoPixel.h>

// =============================================================================
// 1. CONFIGURAZIONE DI DEFAULT (SOVRASCRIVIBILE DA WEB PANEL VIA USB SERIAL NVS)
// =============================================================================
enum StationRole {
STATION_CASHIER  = 1, // ESP32 #1 — Cassa
STATION_KITCHEN  = 2, // ESP32 #2 — Cucina
STATION_DELIVERY = 3  // ESP32 #3 — Consegna / Bancone
};

const char* DEFAULT_STATION_ROLE = "DELIVERY";
const char* DEFAULT_WIFI_SSID    = "SAN_MARTINO_STAFF_WIFI";
const char* DEFAULT_WIFI_PASS    = "sanmartino-password";
const char* DEFAULT_API_BASE_URL = "http://192.168.1.100:8082/v1/stands";
const char* DEFAULT_STAND_ID     = "22222222-2222-2222-2222-000000000001";
const char* DEFAULT_DEVICE_TOKEN = "ESP32-CANTINA-01-DEMO2026";

Preferences preferences;
StationRole currentStation = STATION_DELIVERY;
String cfgWifiSsid;
String cfgWifiPass;
String cfgApiBaseUrl;
String cfgStandId;
String cfgDeviceToken;

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
39  // Slot 10 (es. Liquore / Extra)    [ext 10k pull-up]
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
int cashierDraftQty[NUM_SLOTS]  = {
0
};
bool kitchenLedOn[NUM_SLOTS]    = {
false
};
int deliveryLedState[NUM_SLOTS] = {
0
}; // 0 = OFF, 1 = BLINKING, 2 = SOLID

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

StationRole parseStationRole(const String& roleStr) {
if (roleStr.equalsIgnoreCase("CASHIER")) return STATION_CASHIER;
if (roleStr.equalsIgnoreCase("KITCHEN")) return STATION_KITCHEN;
return STATION_DELIVERY;
}

String stationQueryParam() {
if (currentStation == STATION_CASHIER) return "CASHIER";
if (currentStation == STATION_KITCHEN) return "KITCHEN";
return "DELIVERY";
}

void loadConfigFromNvs() {
preferences.begin("sanmartino", true);
String roleStr = preferences.getString("stationRole", DEFAULT_STATION_ROLE);
cfgWifiSsid    = preferences.getString("wifiSsid", DEFAULT_WIFI_SSID);
cfgWifiPass    = preferences.getString("wifiPass", DEFAULT_WIFI_PASS);
cfgApiBaseUrl  = preferences.getString("apiBaseUrl", DEFAULT_API_BASE_URL);
cfgStandId     = preferences.getString("standId", DEFAULT_STAND_ID);
cfgDeviceToken = preferences.getString("deviceToken", DEFAULT_DEVICE_TOKEN);
preferences.end();

currentStation = parseStationRole(roleStr);
Serial.printf("[CONFIG NVS] Station=%s | StandId=%s | API=%s | WiFi=%s\n",
stationQueryParam().c_str(), cfgStandId.c_str(), cfgApiBaseUrl.c_str(), cfgWifiSsid.c_str());
}

/**
 * Ascolta sulla porta USB Serial (115200 baud) i comandi inviati dal Web Panel
 * tramite Web Serial API (`PROVISION:{"stationRole":...}` o `STATUS`).
 */
void checkSerialProvisioning() {
if (!Serial.available()) return;
String line = Serial.readStringUntil('\n');
line.trim();
if (line.length() == 0) return;

if (line.equalsIgnoreCase("STATUS")) {
Serial.printf("STATUS_OK:{\"stationRole\":\"%s\",\"standId\":\"%s\",\"apiBaseUrl\":\"%s\",\"wifiSsid\":\"%s\",\"ip\":\"%s\"}\n",
stationQueryParam().c_str(),
cfgStandId.c_str(),
cfgApiBaseUrl.c_str(),
cfgWifiSsid.c_str(),
WiFi.localIP().toString().c_str());
return;
}

if (line.startsWith("PROVISION:")) {
String jsonPart = line.substring(10);
JsonDocument doc;
DeserializationError err = deserializeJson(doc, jsonPart);
if (err) {
Serial.printf("PROVISION_ERR:JSON non valido (%s)\n", err.c_str());
return;
}

String newRole  = doc["stationRole"] | stationQueryParam();
String newSsid  = doc["wifiSsid"]    | cfgWifiSsid;
String newPass  = doc["wifiPass"]    | cfgWifiPass;
String newApi   = doc["apiBaseUrl"]  | cfgApiBaseUrl;
String newStand = doc["standId"]     | cfgStandId;
String newToken = doc["deviceToken"] | cfgDeviceToken;

preferences.begin("sanmartino", false);
preferences.putString("stationRole", newRole);
preferences.putString("wifiSsid", newSsid);
preferences.putString("wifiPass", newPass);
preferences.putString("apiBaseUrl", newApi);
preferences.putString("standId", newStand);
preferences.putString("deviceToken", newToken);
preferences.end();

display1.showNumberDec(8888, true);
display2.showNumberDec(8888, true);
for (int i = 0; i < NUM_SLOTS; i++) {
strip.setPixelColor(i, strip.Color(0, 255, 80));
}
strip.show();

Serial.printf("PROVISION_OK:{\"stationRole\":\"%s\",\"standId\":\"%s\",\"wifiSsid\":\"%s\"}\n",
newRole.c_str(), newStand.c_str(), newSsid.c_str());
delay(600);
ESP.restart();
}
}

// =============================================================================
// 4. INIZIALIZZAZIONE
// =============================================================================
void setup() {
Serial.begin(115200);
loadConfigFromNvs();

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
WiFi.begin(cfgWifiSsid.c_str(), cfgWifiPass.c_str());
Serial.print("Connessione Wi-Fi in corso a " + cfgWifiSsid);
unsigned long startAttempt = millis();
// Attende max 10s all'avvio così la porta seriale USB rimane sempre pronta al provisioning Web Serial
while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 10000) {
checkSerialProvisioning();
delay(250);
Serial.print(".");
}
if (WiFi.status() == WL_CONNECTED) {
Serial.println("\nWi-Fi connesso! IP: " + WiFi.localIP().toString());
fetchKeypadState();
} else {
Serial.println("\nWi-Fi non raggiungibile: in ascolto su USB Serial (115200) per provisioning da Web Panel...");
}
}

// =============================================================================
// 5. LOOP PRINCIPALE
// =============================================================================
void loop() {
checkSerialProvisioning();
unsigned long now = millis();

if (now - lastBlinkTime >= BLINK_INTERVAL_MS) {
lastBlinkTime = now;
blinkPhaseOn = !blinkPhaseOn;
refreshLeds();
}

for (int i = 0; i < NUM_SLOTS; i++) {
bool reading = digitalRead(SLOT_BUTTON_PINS[i]);
if (lastSlotBtnState[i] == HIGH && reading == LOW) {
delay(35);
if (digitalRead(SLOT_BUTTON_PINS[i]) == LOW) {
onSlotButtonPressed(i + 1);
}
}
lastSlotBtnState[i] = reading;
}

if (currentStation == STATION_CASHIER) {
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

if (currentStation == STATION_CASHIER) {
cashierDraftQty[idx]++;
int totalItems = 0;
for (int i = 0; i < NUM_SLOTS; i++) totalItems += cashierDraftQty[i];
display1.showNumberDec(totalItems, false);
refreshLeds();
} else if (currentStation == STATION_KITCHEN) {
sendSlotPress("/keypad/kitchen/press", slotNumber);
} else if (currentStation == STATION_DELIVERY) {
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
void fetchKeypadState() {
if (WiFi.status() != WL_CONNECTED) return;

HTTPClient http;
String url = cfgApiBaseUrl + "/" + cfgStandId + "/keypad/state?station=" + stationQueryParam();
http.begin(url);
http.addHeader("X-Device-Token", cfgDeviceToken);

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
String url = cfgApiBaseUrl + "/" + cfgStandId + "/keypad/orders";
http.begin(url);
http.addHeader("Content-Type", "application/json");
http.addHeader("X-Device-Token", cfgDeviceToken);

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
String url = cfgApiBaseUrl + "/" + cfgStandId + pathSuffix;
http.begin(url);
http.addHeader("Content-Type", "application/json");
http.addHeader("X-Device-Token", cfgDeviceToken);

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
if (currentStation == STATION_CASHIER) {
int draftCount = 0;
for (int i = 0; i < NUM_SLOTS; i++) draftCount += cashierDraftQty[i];
if (draftCount > 0) {
display1.showNumberDec(draftCount, false);
} else {
display1.showNumberDec(lastIssuedOrderNumber, true);
}
} else if (currentStation == STATION_KITCHEN) {
display1.showNumberDec(oldestKitchenOrderNumber, true);
display2.showNumberDec(totalPendingKitchenDishes, false);
} else if (currentStation == STATION_DELIVERY) {
display1.showNumberDec(currentDeliveryOrderNumber, true);
display2.showNumberDec(lastDeliveredOrderNumber, true);
}
}

void refreshLeds() {
for (int i = 0; i < NUM_SLOTS; i++) {
uint32_t color = 0;

if (currentStation == STATION_CASHIER) {
if (cashierDraftQty[i] > 0) {
color = strip.Color(0, 180, 255);
}
} else if (currentStation == STATION_KITCHEN) {
if (kitchenLedOn[i]) {
color = strip.Color(255, 60, 0);
}
} else if (currentStation == STATION_DELIVERY) {
if (deliveryLedState[i] == 2) {
color = strip.Color(0, 230, 60);
} else if (deliveryLedState[i] == 1 && blinkPhaseOn) {
color = strip.Color(255, 160, 0);
}
}

strip.setPixelColor(i, color);
}
strip.show();
}
