package e.sopan.cypressbleapp;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class MainActivity extends AppCompatActivity {

    // TAG is used for informational messages
    private final static String TAG = MainActivity.class.getSimpleName();

    // Variables to access objects from the layout such as buttons, switches, values
    private static Button start_button;
    private static Button search_button;
    private static Button connect_button;
    private static Button discover_button;
    private static Button disconnect_button;
    private static ToggleButton LoggerButton;
    private static TextView fatigue_display;
    private static TextView uuid_label;

    private OutputStreamWriter outputStreamWriter;

    // Variables to manage BLE connection
    private static boolean mConnectState;
    private static boolean mServiceConnected;
    private static PSocCapSenseLedService mPSoCCapSenseLedService;

    private static final int REQUEST_ENABLE_BLE = 1;

    //This is required for Android 6.0 (Marshmallow)
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    // Keep track of whether CapSense Notifications are on or off
    private static boolean CapSenseNotifyState1 = false;
    private static boolean CapSenseNotifyState2 = false;

    private File outputFile;
    private FileOutputStream outputStream;
    private PrintWriter pWriter;
    private boolean writeOn = false;
    private long t;

    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object and initialize the service.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        /**
         * This is called when the PSoCCapSenseLedService is connected
         *
         * @param componentName the component name of the service that has been connected
         * @param service service being bound
         */
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mPSoCCapSenseLedService = ((PSocCapSenseLedService.LocalBinder) service).getService();
            mServiceConnected = true;
            mPSoCCapSenseLedService.initialize();
        }

        /**
         * This is called when the PSoCCapSenseService is disconnected.
         *
         * @param componentName the component name of the service that has been connected
         */
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected");
            mPSoCCapSenseLedService = null;
        }
    };

    /**
     * This is called when the main activity is first created
     *
     * @param savedInstanceState is any state saved from prior creations of this activity
     */
    @TargetApi(Build.VERSION_CODES.M) // This is required for Android 6.0 (Marshmallow) to work
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up a variable to point to the CapSense value on the display
        fatigue_display = findViewById(R.id.frequency_disp);

        // Set up variables for accessing buttons and slide switches
        start_button = findViewById(R.id.start_button);
        search_button = findViewById(R.id.search_button);
        connect_button = findViewById(R.id.connect_button);
        discover_button = findViewById(R.id.discoverSvc_button);
        disconnect_button = findViewById(R.id.disconnect_button);
        LoggerButton = findViewById(R.id.Logger);
        uuid_label = findViewById(R.id.uuid_label);



                // Initialize service and connection state variable
        mServiceConnected = false;
        mConnectState = false;

//        seekBarC2.setEnabled(false);

        //This section required for Android 6.0 (Marshmallow)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access ");
                builder.setMessage("Please grant location access so this app can detect devices.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        } //End of section for Android 6.0 (Marshmallow)

        /* This will be called when the LED On/Off switch is touched
        cap_switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Turn the LED on or OFF based on the state of the switch
                mPSoCCapSenseLedService.writeLedCharacteristic(isChecked);
            }
        });
        */



        /* This will be called when the CapSense Notify On/Off switch is touched */
//        cap_switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                // Turn CapSense Notifications on/off based on the state of the switch
////                mPSoCCapSenseLedService.writeCapSenseNotification1(isChecked);
//                CapSenseNotifyState1 = isChecked;  // Keep track of CapSense notification state
//                if (isChecked) { // Notifications are now on so text has to say "No Touch"
//                    Log.i(TAG, "cap_switch1 " + true);
//                    mCapsenseValue.setText(R.string.NoTouch);
//                } else { // Notifications are now off so text has to say "Notify Off"
//                    mCapsenseValue.setText(R.string.NotifyOff);
//                }
//            }
//        });


        LoggerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeOn = !((ToggleButton) v).isChecked();
                if (writeOn) {

                    try {
                        outputStreamWriter = new OutputStreamWriter(MainActivity.this.openFileOutput("Spaulding_out.csv", Context.MODE_PRIVATE));
                        String timeStamp = new java.text.SimpleDateFormat(
                                "yyyyMMdd_HHmmss").format(java.util.Calendar.getInstance().getTime());
                        timeStamp = new java.text.SimpleDateFormat(
                                "yyyy/MM/dd,HH:mm:ss").format(java.util.Calendar.getInstance().getTime());
                        String header = "CPE 621, HW#4: Alex Spaulding\n" + timeStamp + "\n" + "t,C1,C2\n" ;
                        outputStreamWriter.write(header);
                        Toast.makeText(MainActivity.this,
                                "File created", Toast.LENGTH_LONG).show();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this,
                                "failed to create file, file not found", Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    t = System.nanoTime();
                    //Log.i(TAG, "OUTPUT FILE DIRECTORY "+dir);

                } else {
                    try {
                        outputStreamWriter.close();
                        Toast.makeText(MainActivity.this,
                                "File Closed", Toast.LENGTH_LONG).show();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this,
                                "failed to close file", Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this,
                                "failed to close file", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    //This method required for Android 6.0 (Marshmallow)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission for 6.0:", "Coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
            }
        }
    } //End of section for Android 6.0 (Marshmallow)

    @Override
    protected void onResume() {
        super.onResume();
        // Register the broadcast receiver. This specified the messages the main activity looks for from the PSoCCapSenseLedService
        final IntentFilter filter = new IntentFilter();
        filter.addAction(PSocCapSenseLedService.ACTION_BLESCAN_CALLBACK);
        filter.addAction(PSocCapSenseLedService.ACTION_CONNECTED);
        filter.addAction(PSocCapSenseLedService.ACTION_DISCONNECTED);
        filter.addAction(PSocCapSenseLedService.ACTION_SERVICES_DISCOVERED);
        filter.addAction(PSocCapSenseLedService.ACTION_DATA_RECEIVED);
        registerReceiver(mBleUpdateReceiver, filter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BLE && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBleUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close and unbind the service when the activity goes away
        mPSoCCapSenseLedService.close();
        unbindService(mServiceConnection);
        mPSoCCapSenseLedService = null;
        mServiceConnected = false;
    }

    /**
     * This method handles the start bluetooth button
     *
     * @param view the view object
     */
    public void startBluetooth(View view) {

        // Find BLE service and adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLE);
        }

        // Start the BLE Service
        Log.d(TAG, "Starting BLE Service");
        Intent gattServiceIntent = new Intent(this, PSocCapSenseLedService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Disable the start button and turn on the search  button
        start_button.setEnabled(false);
        search_button.setEnabled(true);
        Log.d(TAG, "Bluetooth is Enabled");
    }

    /**
     * This method handles the Search for Device button
     *
     * @param view the view object
     */
    public void searchBluetooth(View view) {
        if (mServiceConnected) {
            mPSoCCapSenseLedService.scan();
        }

        /* After this we wait for the scan callback to detect that a device has been found */
        /* The callback broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * This method handles the Connect to Device button
     *
     * @param view the view object
     */
    public void connectBluetooth(View view) {
        mPSoCCapSenseLedService.connect();

        /* After this we wait for the gatt callback to report the device is connected */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * This method handles the Discover Services and Characteristics button
     *
     * @param view the view object
     */
    public void discoverServices(View view) {
        /* This will discover both services and characteristics */
        mPSoCCapSenseLedService.discoverServices();

        /* After this we wait for the gatt callback to report the services and characteristics */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * This method handles the Disconnect button
     *
     * @param view the view object
     */
    public void Disconnect(View view) {
        mPSoCCapSenseLedService.disconnect();

        /* After this we wait for the gatt callback to report the device is disconnected */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * Listener for BLE event broadcasts
     */
    private final BroadcastReceiver mBleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case PSocCapSenseLedService.ACTION_BLESCAN_CALLBACK:
                    // Disable the search button and enable the connect button
                    search_button.setEnabled(false);
                    connect_button.setEnabled(true);
                    uuid_label.setText(PSocCapSenseLedService.getUUID());
                    break;

                case PSocCapSenseLedService.ACTION_CONNECTED:
                    /* This if statement is needed because we sometimes get a GATT_CONNECTED */
                    /* action when sending Capsense notifications */
                    if (!mConnectState) {
                        // Dsable the connect button, enable the discover services and disconnect buttons
                        connect_button.setEnabled(false);
                        discover_button.setEnabled(true);
                        disconnect_button.setEnabled(true);
                        mConnectState = true;
                        Log.d(TAG, "Connected to Device");
                    }
                    break;
                case PSocCapSenseLedService.ACTION_DISCONNECTED:
                    // Disable the disconnect, discover svc, discover char button, and enable the search button
                    disconnect_button.setEnabled(false);
                    discover_button.setEnabled(false);
                    search_button.setEnabled(true);
//                    // Turn off and disable the LED and CapSense switches
//                    cap_switch1.setChecked(false);
//                    cap_switch1.setEnabled(false);
                    mConnectState = false;
                    Log.d(TAG, "Disconnected");
                    break;
                case PSocCapSenseLedService.ACTION_SERVICES_DISCOVERED:
                    // Disable the discover services button
                    discover_button.setEnabled(false);
                    // Enable the fatigue display
                    fatigue_display.setEnabled(true);

                    mPSoCCapSenseLedService.writeMfmNotification(true);
                    break;



                case PSocCapSenseLedService.ACTION_DATA_RECEIVED:
                    Log.d(TAG, "DATA RECEIVED");
                    // This is called after a notify or a read completes
                    // Get CapSense Slider Value
                    String MfmValue = mPSoCCapSenseLedService.getmfmValue();
                    int freqVal = mPSoCCapSenseLedService.getFreqVal();


                    if(writeOn)
                    {
                        try {

                            double currentTime = (System.nanoTime() - t)/1000000000.0000000;
                            outputStreamWriter.write(currentTime+","+ freqVal );
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this,
                                    "failed to write", Toast.LENGTH_LONG).show();
                        }
                    }

//                    if (MfmValue.equals("-1")) {  // No Touch returns 0xFFFF which is -1
////                        seekBarC1.setProgress(0);
//                        if (!CapSenseNotifyState1) { // Notifications are off
//                            mCapsenseValue.setText(R.string.NotifyOff);
//                            //Log.i(TAG, "cap 1 notification not received");
//                        } else { // Notifications are on but there is no finger on the slider
//                            mCapsenseValue.setText(R.string.NoTouch);
//                            seekBarC1.setProgress(0);
//                        }
//                    } else { // Valid CapSense value is returned
                        fatigue_display.setText(freqVal+ "");
//                        seekBarC1.setProgress(capval1);
                        //Log.i(TAG, "cap 1 notification received " + Integer.parseInt(MfmValue, 16));
//                    }


                default:
                    break;
            }
        }
    };
}
