//
//  ViewController.swift
//  BLE-MFM
//
//  Created by Alex Spaulding on 12/11/21.
//  Copyright © 2021 Alex Spaulding. All rights reserved.
//

import UIKit
import Foundation
import CoreBluetooth


// Initialize global variables
var blePeripheral: CBPeripheral?
var MFMCharacteristic: CBCharacteristic?

class ViewController: UIViewController, CBCentralManagerDelegate, CBPeripheralDelegate {
    
    
    @IBOutlet weak var refresh_button: UIBarButtonItem!
    @IBOutlet weak var connected_label: UIBarButtonItem!
    @IBOutlet weak var repetition_label: UILabel!
    @IBOutlet weak var fatigue_label: UILabel!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // Do any additional setup after loading the view.
        
        
        // Set the label to say "Disconnected" and make the text red
        connected_label.isEnabled = false
        
        // Initialize CoreBluetooth Central Manager object which will be necessary
        // to use CoreBlutooth functions
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    func startScan(){
        print("Now Scanning...")
        print("Service ID Search: \(BLEService_UUID)")
        
        peripheralList = []
        
        self.timer.invalidate()
        
        centralManager?.scanForPeripherals(withServices: [BLEService_UUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey:false])
        Timer.scheduledTimer(withTimeInterval: 10, repeats: false) { _ in
            self.cancelScan()
        }
    }
    
    func cancelScan(){
        self.centralManager?.stopScan()
        print("Scan Stopped")
        print("Number of Peripherals: \(peripheralList.count)")
    }
    
    
    func centralManagerDidUpdateState( _ central: CBCentralManager) {
        if central.state == CBManagerState.poweredOn {
            print("Bluetooth Enabled")
            startScan()
        }
        
        else {
            print("Bluetooth disabled - ensure it is turned on")
            
            let alertVC = UIAlertController(title: "Bluetooth is not enabled",
                message: "Make sure that Bluetooth is enabled",
            preferredStyle: UIAlertController.Style.alert)
            
            let action = UIAlertAction(title: "ok",
                                       style: UIAlertAction.Style.default,
                                       handler: {(action:UIAlertAction) -> Void in self.dismiss(animated:true, completion: nil)})
            
            alertVC.addAction(action)
            self.present(alertVC, animated: true, completion: nil)
        }
    }
    
    
    // Variable initializations
    var centralManager: CBCentralManager!
    var data = NSMutableData()
    var rssiList = [NSNumber]()
    var peripheralList : [CBPeripheral] = []
    var characteristicList = [String: CBCharacteristic]()
    var characteristicValue = [CBUUID: NSData]()
    var timer = Timer()
    
    let BLEService_UUID = CBUUID.init(string: "6df57bea-58e0-11ec-bf63-0242ac130002")
    let BLECharacteristic_uuid_MFM = CBUUID.init(string: "6df57e1a-58e0-11ec-bf63-0242ac13000")
    
    var receivedData = [Int]()
    
    
    
    
}

