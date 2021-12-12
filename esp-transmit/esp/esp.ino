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

// server and characteristic uuid
#define SERVICE_UUID          "6df57bea-58e0-11ec-bf63-0242ac130002"
#define CHARACTERISTIC_UUID   "6df57e1a-58e0-11ec-bf63-0242ac130002"


#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;
uint32_t value = 0;
bool data_changed = false;


class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
    }
};



void setup() {
  Serial.begin(115200);

  // Connection to MFM for 
  Serial1.begin(115200);

  // Create the BLE Device
  BLEDevice::init("Spaulding-BLE");

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE  |
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_INDICATE
                    );

  // https://www.bluetooth.com/spec2ifications/gatt/viewer?attributeXmlFile=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
  // Create a BLE Descriptor
  pCharacteristic->addDescriptor(new BLE2902());

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);  // set value to 0x00 to not advertise this parameter
  BLEDevice::startAdvertising();
  Serial.println("\nWaiting a client connection to notify...");
}

void loop() {
    if(Serial1.available()>0)
    {
      value = Serial1.parseInt();
      data_changed = true;
    }
  
    // notify changed value
    if (deviceConnected && data_changed) {
        pCharacteristic->setValue(value);
        pCharacteristic->notify();
        delay(3); // bluetooth stack will go into congestion, if too many packets are sent, in 6 hours test i was able to go as low as 3ms
        Serial.print("Notifying: ");
        Serial.println(value);
        data_changed = false;
    }
    // disconnecting
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); // give the bluetooth stack the chance to get things ready
        pServer->startAdvertising(); // restart advertising
        Serial.println("start advertising");
        oldDeviceConnected = deviceConnected;
    }
    // connecting
    if (deviceConnected && !oldDeviceConnected) {
        // do stuff here on connecting
        oldDeviceConnected = deviceConnected;
    }
}
