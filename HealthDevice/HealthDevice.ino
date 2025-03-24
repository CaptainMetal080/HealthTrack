#include <Wire.h>
#include "MAX30105.h"
#include "heartRate.h"
#include "spo2_algorithm.h"
#include <ArduinoBLE.h>
#include <Adafruit_MCP9808.h>
#include <SdFat.h>
#include <ArduinoJson.h>
#include <Adafruit_SSD1306.h>

#define SD_CS_PIN 10
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 32
#define OLED_RESET -1
#define SCREEN_ADDRESS 0x3C

MAX30105 particleSensor;
Adafruit_MCP9808 tempsensor = Adafruit_MCP9808();
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

SdFat SD;
File dataFile;

const byte RATE_SIZE = 10;
byte rates[RATE_SIZE];
byte rateSpot  = 0;
long lastBeat = 0;

float beatsPerMinute;
int beatAvg;

int numReadings = 0;
int sampleCount = 0;

const int BUFFER_LENGTH = 100;
uint32_t irBuffer[BUFFER_LENGTH];
uint32_t redBuffer[BUFFER_LENGTH];
int32_t spo2;
int8_t validSPO2;
int32_t heartRate;
int8_t validHeartRate;
float temperatureC;

int liveService = 0;
int readJson = 1;

BLEService heartrateService("1101");
BLECharacteristic healthDataChar("2101", BLERead | BLENotify, 12);

bool isConnected = false;
bool dataSent = false;
bool isSensorPlacementBad = false;

void setup() {
  Serial.begin(115200);
  Serial.println("Initializing...");

  // Initialize BLE
  if (!BLE.begin()) {
    Serial.println("Starting BLE failed!");
  } else {
    BLE.setLocalName("HealthMonitoringDevice");
    BLE.setAdvertisedService(heartrateService);
    heartrateService.addCharacteristic(healthDataChar); 
    BLE.addService(heartrateService);
    BLE.advertise();
    Serial.println("Bluetooth device active, waiting for connections...");
  }

  // Initialize MAX30102 Sensor
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("MAX30102 was not found. Please check wiring/power.");
    while (1);
  }

  particleSensor.setup();
  particleSensor.setPulseAmplitudeRed(0x7F);
  particleSensor.setPulseAmplitudeGreen(0);

  // Initialize MCP9808 sensor
  if (!tempsensor.begin()) {
    Serial.println("Couldn't find MCP9808!");
    while (1);
  }
  tempsensor.setResolution(3);  // 0.0625 degrees per LSB
  Serial.println("MCP9808 sensor initialized.");

  // Initialize SD card
  if (!SD.begin(SD_CS_PIN)) {
    Serial.println("SD card initialization failed!");
  } else {
    Serial.println("SD card initialized.");
  }

  // Initialize the display with I2C address
  if (!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println(F("SSD1306 allocation failed"));
    for (;;);  // Don't proceed, loop forever
  }

  display.clearDisplay();  // Clear the buffer
  display.setTextSize(1);  // Normal 1:1 pixel scale
  display.setTextColor(SSD1306_WHITE);  // Draw white text
  display.setCursor(0, 10);  // Start at top-left corner
}

void loop() {
  BLEDevice central = BLE.central();

  if (central) {  // If connected
    if (!isConnected) {
      isConnected = true;
      Serial.print("Connected to central: ");
      Serial.println(central.address());
    }
  } else {  // If disconnected
    if (isConnected) {
      Serial.println("Disconnected from central.");
      isConnected = false;
      dataSent = false;
      BLE.advertise();  // Restart advertising
    }
  }

  long irValue = particleSensor.getIR();
  long redValue = particleSensor.getRed();

  if (irValue < 50000) {  // Bad placement
    if (!isSensorPlacementBad) {  // Only update if there's a change in placement status
      Serial.print("Check Placement");
      isSensorPlacementBad = true;
      display.clearDisplay();
      display.setTextSize(2);
      display.setCursor(0, 0);

      display.println("Check");
      display.setCursor(0, 16);
      display.println("Placement");

      display.display();
    }
  } else {  // Good placement
    if (isSensorPlacementBad) {  // Only update if there's a change in placement status
      isSensorPlacementBad = false;
      display.clearDisplay();  // Clear the previous message
      display.setTextSize(2);
      display.setCursor(0, 12);
      display.println("Reading...");
      display.display();
    }

    if (checkForBeat(irValue)) {
      long delta = millis() - lastBeat;
      lastBeat = millis();

      beatsPerMinute = 60 / (delta / 1000.0);

      if (beatsPerMinute < 255 && beatsPerMinute > 20) {
        rates[rateSpot++] = (byte)beatsPerMinute;
        rateSpot %= RATE_SIZE;
        sampleCount++;
      }
    }

    if (irValue > 50000 && redValue > 50000) {
      for (int i = 1; i < BUFFER_LENGTH; i++) {
        redBuffer[i - 1] = redBuffer[i];
        irBuffer[i - 1] = irBuffer[i];
      }
      redBuffer[BUFFER_LENGTH - 1] = redValue;
      irBuffer[BUFFER_LENGTH - 1] = irValue;
    }

    if (sampleCount >= 10) {
      beatAvg = 0;
      for (byte x = 0; x < RATE_SIZE; x++) {
        beatAvg += rates[x];
      }
      beatAvg /= RATE_SIZE;

      maxim_heart_rate_and_oxygen_saturation(irBuffer, BUFFER_LENGTH, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);

      Serial.print("Avg BPM = ");
      Serial.println(beatAvg);

      if (validSPO2) {
        Serial.print("SpO₂ = ");
        Serial.print(spo2);
        Serial.println(" %");
      } else {
        Serial.println("Invalid SpO₂ reading.");
      }

      // Read temperature
      temperatureC = tempsensor.readTempC();
      Serial.print("Temperature: ");
      Serial.print(temperatureC);
      Serial.println(" C");

      int temperatureInt = (int)temperatureC;
      int temperatureDec = (int)((temperatureC - temperatureInt) * 100);
      
      // Update OLED display with the latest readings
      display.clearDisplay();
      display.setCursor(0, 0);
      display.print("BPM: ");
      display.println(beatAvg);
      display.print("SpO₂: ");
      display.println(validSPO2 ? spo2 : 0);
      display.print("Temp: ");
      display.print(temperatureC, 2);
      display.println(" C");
      display.display();
      
      byte healthData[5];
      healthData[0] = (byte)beatAvg;
      healthData[1] = (uint8_t)spo2;
      healthData[2] = (uint8_t)temperatureInt;
      healthData[3] = (uint8_t)temperatureDec;
      healthData[4] = (uint8_t)liveService;

      if (isConnected) {
        if (!dataSent){
          sendJSONData();
          dataSent = true;
        }
        healthDataChar.writeValue(healthData, sizeof(healthData));
      } else if (!isConnected){
        saveDataAsJSON(beatAvg, spo2, temperatureC);
      }

      sampleCount = 0;
      rateSpot = 0;
      numReadings = 0;
    }
  }
}

void saveDataAsJSON(int heartRate, int spo2, float temperature) {
    StaticJsonDocument<1024> doc;
    JsonArray dataArray;

    // Open the file in read mode to check existing content
    if (SD.exists("health_data.json")) {
        dataFile = SD.open("health_data.json", O_RDWR);
        if (dataFile) {
            DeserializationError error = deserializeJson(doc, dataFile);
            if (!error && doc.is<JsonArray>()) {
                dataArray = doc.as<JsonArray>();
            }
            dataFile.close();
        }
    }

    // If file doesn't exist or is invalid, create a new array
    if (!dataArray) {
        dataArray = doc.to<JsonArray>();
    }

    // Create a new entry
    StaticJsonDocument<256> newEntry;
    newEntry["heart_rate"] = heartRate;
    newEntry["spo2"] = spo2;
    newEntry["temperature"] = temperature;
    newEntry["type"] = readJson;

    // Append new entry to the array
    dataArray.add(newEntry);

    // Open file in write mode to overwrite with updated JSON array
    dataFile = SD.open("health_data.json", O_RDWR | O_CREAT | O_TRUNC);
    if (dataFile) {
        serializeJson(dataArray, dataFile);
        dataFile.close();
        Serial.println("Data appended to JSON array in SD card.");
    } else {
        Serial.println("Error opening health_data.json.");
    }
}


void sendJSONData() {
    Serial.println("Sending Data");

    File dataFile = SD.open("health_data.json", FILE_READ);
    if (!dataFile) {
        Serial.println("Error: Cannot open JSON file.");
        return;
    }

    // Read the entire file into a string
    String jsonData = "";
    while (dataFile.available()) {
        jsonData += (char)dataFile.read();
    }
    dataFile.close();

    // Parse JSON
    DynamicJsonDocument doc(4096);
    DeserializationError error = deserializeJson(doc, jsonData);

    if (error) {
        Serial.println("Error: JSON Parsing failed.");
        return;
    }

    if (!doc.is<JsonArray>()) {
        Serial.println("Error: JSON is not an array.");
        return;
    }

    JsonArray dataArray = doc.as<JsonArray>();

    if (dataArray.size() == 0) {
        Serial.println("Error: No data found.");
        return;
    }

    // Loop from last entry to first
    for (int i = dataArray.size() - 1; i >= 0; i--) {
        JsonObject entry = dataArray[i];

        float temperature = entry["temperature"];
        int temperatureInt = (int)temperatureC;
        int temperatureDec = (int)((temperatureC - temperatureInt) * 100);

        // Extract health metrics
        byte healthData[5];
        healthData[0] = (byte)entry["heart_rate"];
        healthData[1] = (uint8_t)entry["spo2"];
        healthData[2] = (uint8_t)temperatureInt;  // Integer part
        healthData[3] = (uint8_t)temperatureDec;  // Decimal part
        healthData[4] = (uint8_t)entry["type"];

        // Send over Bluetooth
        healthDataChar.writeValue(healthData, sizeof(healthData));

        delay(500); // Delay for Bluetooth stability
    }

    // Delete JSON file after sending
    if (SD.exists("health_data.json")) {
        if (SD.remove("health_data.json")) {
            Serial.println("JSON file deleted successfully.");
        } else {
            Serial.println("Error deleting JSON file.");
        }
    }
}



