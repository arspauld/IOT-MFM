/*  ESP32 BT Bridge
 *   
 *   This program is designed to continuously monitor 
 *   the status of the Muscle Fatigue Monitor on Serial1
 *   transmitting any values received over bluetooth
 *   
 *   Adapted from BLE_notify by Neil Koban and Evandro Copercini
 *   https://github.com/nkolban/esp32-snippets/blob/master/cpp_utils/tests/BLE%20Tests/SampleNotify.cpp
 *   
 *   
 *   Author: Alex Spaulding
 *   Date: November 16, 2021
 *   Class: CPE621 - Advanced Embedded Systems
 *   Professor: Dr. Emil Jovanov
 *   
 *   Serial Port: Serial1
 * 
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// server and characteristic pointers
BLEServer *pServer = NULL;
BLECharacteristic *pCharacteristic = NULL;
bool deviceConnected = false;
uint32_t value = 0;

// server and characteristic uuid
#define SERVICE_UUID          "6df57bea-58e0-11ec-bf63-0242ac130002"
#define CHARACTERISTIC_UUID   "6df57e1a-58e0-11ec-bf63-0242ac130002"

// Callbacks for connecting and disconnecting
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
    }
};


void setup() {
  // Initialize serial to Teensy and host
  Serial.begin(115200);
  Serial1.begin(115200);

  // Create a BLE device
  BLEDevice::init("Spaulding MFM");

  // create BLE server
  pserver = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic - just need to notify
  pCharacteristic - pService->createCharacteristic(
      CHARACTERIC_UUID,
      BLECharacteristic::PROPERTY_READ |
      BLECharcateristic::PROPERTY_NOTIFY
    );
}

void loop() {
  // put your main code here, to run repeatedly:
//  if(Serial1.available())
//    Serial.write(Serial1.read());
SerialBT.write(1);

}
