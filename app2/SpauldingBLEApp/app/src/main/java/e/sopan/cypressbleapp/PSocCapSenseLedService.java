package e.sopan.cypressbleapp;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGattCharacteristic;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static android.nfc.NfcAdapter.EXTRA_DATA;

/**
 * Service for managing the BLE data connection with the GATT database.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP) // This is required to allow us to use the lollipop and later scan APIs
public class PSocCapSenseLedService extends Service {
    private final static String TAG = PSocCapSenseLedService.class.getSimpleName();

    // Bluetooth objects that we need to interact with
    private static BluetoothManager mBluetoothManager;
    private static BluetoothAdapter mBluetoothAdapter;
    private static BluetoothLeScanner mLEScanner;
    private static BluetoothDevice mLeDevice;
    private static BluetoothGatt mBluetoothGatt;

    // Bluetooth characteristics that we need to read/write
    private static BluetoothGattCharacteristic mfmCharacteristic;
    private static BluetoothGattDescriptor mMFMCccd;

    // UUIDs for the service and characteristics that the custom CapSenseLED service uses
    private final static String mfmServiceUUID =            "6df57bea-58e0-11ec-bf63-0242ac130002";
    public final static String mfmCharacteristicUUID =      "6df57e1a-58e0-11ec-bf63-0242ac130002";
    public final static String mfmCccdUUID =                "00002902-0000-1000-8000-00805f9b34fb";

    public static byte[] data1 = EXTRA_DATA.getBytes();

    public  static int FreqVal = 0;



    // Variables to keep track of the LED switch state and CapSense Value
    private static String mfmValue = "-1"; // This is the No Touch value (0xFFFF)
    private static String discovered_service_uuid = "";
//    private static String mCapSenseValue2 = "-1"; // This is the No Touch value (0xFFFF)

    // Actions used during broadcasts to the main activity
    public final static String ACTION_BLESCAN_CALLBACK =
            "e.sopan.cypressbleapp.ACTION_BLESCAN_CALLBACK";
    public final static String ACTION_CONNECTED =
            "e.sopan.cypressbleapp.ACTION_CONNECTED";
    public final static String ACTION_DISCONNECTED =
            "e.sopan.cypressbleapp.ACTION_DISCONNECTED";
    public final static String ACTION_SERVICES_DISCOVERED =
            "e.sopan.cypressbleapp.ACTION_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_RECEIVED =
            "e.sopan.cypressbleapp.ACTION_DATA_RECEIVED";

    public PSocCapSenseLedService() {
    }

    /**
     * This is a binder for the PSoCCapSenseLedService
     */
    public class LocalBinder extends Binder {
        PSocCapSenseLedService getService() {
            return PSocCapSenseLedService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // The BLE close method is called when we unbind the service to free up the resources.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Scans for BLE devices that support the service we are looking for
     */
    public void scan() {

        Log.d(TAG, "Beginning Scan");
        /* Scan for devices and look for the one with the service that we want */
        UUID   mfmService =       UUID.fromString(mfmServiceUUID);
        UUID[] mfmServiceArray = {mfmService};

        // Use old scan method for versions older than lollipop
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //noinspection deprecation
            mBluetoothAdapter.startLeScan(mfmServiceArray, mLeScanCallback);
        } else { // New BLE scanning introduced in LOLLIPOP
            ScanSettings settings;
            List<ScanFilter> filters;
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<>();
            // We will scan just for the CAR's UUID
            ParcelUuid PUuid = new ParcelUuid(mfmService);
            //ScanFilter filter = new ScanFilter.Builder().setServiceUuid(PUuid).build();
            ScanFilter filter = new ScanFilter.Builder().setDeviceName("Spaulding-BLE").build();
            filters.add(filter);
            mLEScanner.startScan(filters, settings, mScanCallback);
            Log.d(TAG, "Scan Started");
        }
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect() {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = mLeDevice.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        return true;
    }

    /**
     * Runs service discovery on the connected device.
     */
    public void discoverServices() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.discoverServices();
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * This method is used to read the state of the LED from the device
     */
    public void readMFMCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(mfmCharacteristic);
    }

    /**
     * This method is used to turn the LED on or off
     *
     * @param value Turns the LED on (1) or off (0)
     */
    /*public void writeLedCharacteristic(boolean value) {
        byte[] byteVal = new byte[1];
        if (value) {
            byteVal[0] = (byte) (1);
        } else {
            byteVal[0] = (byte) (0);
        }
        Log.i(TAG, "LED " + value);
        mLedSwitchState = value;
        mLedCharacterisitc.setValue(byteVal);
        mBluetoothGatt.writeCharacteristic(mLedCharacterisitc);
    }*/

    /**
     * This method enables or disables notifications for the CapSense slider
     *
     * @param value Turns notifications on (1) or off (0)
     */
    public void writeMfmNotification(boolean value) {
        // Set notifications locally in the CCCD
        mBluetoothGatt.setCharacteristicNotification(mfmCharacteristic, value);
        byte[] byteVal = new byte[1];
        if (value) {
            byteVal[0] = (byte)(1);
        } else {
            byteVal[0] = (byte) (0);
        }
        // Write Notification value to the device
        mMFMCccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        while(!mBluetoothGatt.writeDescriptor(mMFMCccd));

        Log.i(TAG, "mMFMCccd Descriptor Written Successfully!!!");
//        mBluetoothGatt.writeDescriptor(mMFMCccd);

    }

//    public void writeCapSenseNotification2(boolean value) {
//        // Set notifications locally in the CCCD
//        mBluetoothGatt.setCharacteristicNotification(mCapsenseCharacteristic2, value);
//        byte[] byteVal = new byte[1];
//        if (value) {
//            byteVal[0] = (byte)(1);
//        } else {
//            byteVal[0] = (byte) (0);
//        }
//        // Write Notification value to the device
//        Log.i(TAG, "CapSense2 Notification " + value);
//        mCapSenseCccd2.setValue(byteVal);
//        mBluetoothGatt.writeDescriptor(mCapSenseCccd2);
//    }

    /**
     * This method returns the state of the LED switch
     *
     * @return the value of the LED swtich state
     */
 /*   public boolean getLedSwitchState() {
        return mLedSwitchState;
    }
*/
    /**
     * This method returns the value of th CapSense Slider
     *
     * @return the value of the CapSense Slider
     */
    public String getmfmValue() {
        //Log.i(TAG, "cap1 value" + mCapSenseValue1);
        return mfmValue;
    }
    public int getFreqVal() {
        //Log.i(TAG, "cap1 value" + mCapSenseValue1);
        return FreqVal;
    }

    public static String getUUID() {
        return discovered_service_uuid;
    }
//    public String getCapSenseValue2() {
//        //Log.i(TAG, "cap2 value" + mCapSenseValue2);
//        return mCapSenseValue2;
//    }


    /**
     * Implements the callback for when scanning for devices has found a device with
     * the service we are looking for.
     *
     * This is the callback for BLE scanning on versions prior to Lollipop
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    mLeDevice = device;
                    //noinspection deprecation
                    mBluetoothAdapter.stopLeScan(mLeScanCallback); // Stop scanning after the first device is found
                    broadcastUpdate(ACTION_BLESCAN_CALLBACK); // Tell the main activity that a device has been found
                }
            };

    /**
     * Implements the callback for when scanning for devices has faound a device with
     * the service we are looking for.
     *
     * This is the callback for BLE scanning for LOLLIPOP and later
     */
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mLeDevice = result.getDevice();
            discovered_service_uuid = mLeDevice.getName();
            mLEScanner.stopScan(mScanCallback); // Stop scanning after the first device is found
            broadcastUpdate(ACTION_BLESCAN_CALLBACK); // Tell the main activity that a device has been found
        }
    };


    /**
     * Implements callback methods for GATT events that the app cares about.  For example,
     * connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_CONNECTED);
                Log.i(TAG, "Connected to GATT server.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_DISCONNECTED);
            }
        }

        /**
         * This is called when a service discovery has completed.
         *
         * It gets the characteristics we are interested in and then
         * broadcasts an update to the main activity.
         *
         * @param gatt The GATT database object
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // Get just the service that we are looking for
            BluetoothGattService mService = gatt.getService(UUID.fromString(mfmServiceUUID));
            /* Get characteristics from our desired service */
            mfmCharacteristic = mService.getCharacteristic(UUID.fromString(mfmCharacteristicUUID));
            Log.d(TAG, "UUID: " + mfmCharacteristic.getUuid().toString());
            /* Get the mfm CCCD */
            Log.d(TAG, "Descriptor: " + mfmCharacteristic.getDescriptor(UUID.fromString(mfmCccdUUID)));
            mMFMCccd = mfmCharacteristic.getDescriptor(UUID.fromString(mfmCccdUUID));

            // Read the current state of the LED from the device
            readMFMCharacteristic();

            // Broadcast that service/characteristic/descriptor discovery is done
            broadcastUpdate(ACTION_SERVICES_DISCOVERED);
        }

        /**
         * This is called when a read completes
         *
         * @param gatt the GATT database object
         * @param characteristic the GATT characteristic that was read
         * @param status the status of the transaction
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Verify that the read was the LED state
                String uuid = characteristic.getUuid().toString();
                // In this case, the only read the app does is the LED state.
                // If the application had additional characteristics to read we could
                // use a switch statement here to operate on each one separately.
                if(uuid.equalsIgnoreCase(mfmCharacteristicUUID)) {
                    final byte[] data = characteristic.getValue();
                    // Set the LED switch state variable based on the characteristic value ttat was read
                    mfmValue = ((data[0] & 0xff) != 0x00) + "";
                }

                Log.d(TAG, "READ: " + mfmValue);
                // Notify the main activity that new data is available
                broadcastUpdate(ACTION_DATA_RECEIVED);
            }
        }
        /**
         * This is called when a characteristic with notify set changes.
         * It broadcasts an update to the main activity with the changed data.
         *
         * @param gatt The GATT database object
         * @param characteristic The characteristic that was changed
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "WE GOTS ANOTIFICATION");

            String uuid = characteristic.getUuid().toString();


            if(uuid.equalsIgnoreCase(mfmCharacteristicUUID)) {
                data1 = characteristic.getValue();
                FreqVal = ((data1[1] & 0xff) << 8) | (data1[0] & 0xff);
                mfmValue = Integer.toHexString(FreqVal);

                //Log.i(TAG, "getting cap 2 val 1 "+data[0]);
                //Log.i(TAG, "getting cap 2 val 2 "+data[1]);
                //mCapSenseValue1 = Integer.toHexString(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,0));
                //mCapSenseValue1 =  mCapSenseValue1.substring(4);
                //broadcastUpdate(ACTION_DATA_RECEIVED);
                Log.i(TAG, "getting cap 1 charecteristics " + mfmValue);
            }

//            if(uuid.equalsIgnoreCase(capsenseCharacteristicUUID2)) {
//                data2 = characteristic.getValue();
//                C2Val = ((data2[1] & 0xff) << 8) | (data2[0] & 0xff);
//                mCapSenseValue2 = Integer.toHexString(C2Val);
//                //mCapSenseValue2 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,0).toString();
//                //mCapSenseValue2 = Integer.toHexString(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,0));
//                //mCapSenseValue2 =  mCapSenseValue2.substring(4);
//                //broadcastUpdate(ACTION_DATA_RECEIVED);
//                Log.i(TAG, "getting cap 2 charecteristics "+mCapSenseValue2);
//            }
            broadcastUpdate(ACTION_DATA_RECEIVED);
        }
    }; // End of GATT event callback methods

    /**
     * Sends a broadcast to the listener in the main activity.
     *
     * @param action The type of action that occurred.
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

}