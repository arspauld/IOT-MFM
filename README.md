# IOT-MFM
Contains the code and simulations used to develop the IOT Enabled Muscle Fatigue Monitor.
The current state of the project implements the embedded muscle fatigue monitor on the 
Teensy 4.1 microcontroller. Additional development was completed on creating a bluetooth
interface through the Huzzah32 Bluetooth Enabled microcontroller and an android application
however development of this application was not completed.

    -> Teensy Sampling
        -> logs - Contains several log files used in the development of DSP algorithms
        -> matlab_proc.m - Matlab simulation used to verify signal processing techniques
            - MAV (RMS) value can be used to approximate the amplitude of the EMG signal
              which is used as a trigger to mark when a rep has been completed
            - Frequency specrtra taken during peaks of pre-recorded waves prove that
              there are several low frequency components combined within the signal when
        -> teensy_sd_sample
            -> teensy_sd_sample.ino
                - Main implementation of the embedded system
                - Sampling done using ADC0 and adc_isr at 1024Hz sampling rate
                - Samples loaded into several ping pong buffers used to calculate the RMS and median frequencies
                - RMS calculated every ~0.125 seconds
                - If repetition discovered using threshold detection then median frequency is calculated for the 
                  previous 1024 samples
                - Median frequency calculated using magnitude of windowed FFT, averaging the magnitude of each 
                  frequency bin by dividing the sum of all frequencies
                - Outuput median frequency over Serial7 to ESP32 bridge

    -> ESP Transmit
        -> esp
            -> esp.ino - Arduino script for Huzzah32 Microcontroller intended to allow for 
               bluetooth communication with an android application. The huzzah32 would simply
               act as a bridge and bluetooth transmitted. This is achieved by creating a Bluetooth
               Low Energy (BLE) service which connects to an Android application. The Android 
               application subscribes to notifications on the service, allowing the app to consistently
               read data whenever it is updated. The ESP device monitors Serial1 continuously. Whenever
               an integer is received over the data stream the current value of the service 
               characteristic is updated and a notification is pushed to the application.
    -> app2
        -> MainActivity.java
            - Describes the Main Activity and program flow for the android application
            - Coordinates the user interface and makes calls to the BLE backend
        -> PSoCLedService.java
            - Searches for, conncts to, and discovers attributes of the ESP32 BLE service
            - Acts as the BLE backend for the Android application making call to the GATT API
              provided by Android

## Usage
Below is a diagram showing the hardware connections that should be made to utilize this system.

    Teensy4.1           Myoware Muscle Sensor
    ----------          ---------------------
    |3.3V     |<------->| + (Power)         |
    |GND      |<------->| - (Ground)        |
    |A0       |<------->| RAW (EMG Output)  |
    |         |         ---------------------
    |         |
    |         |
    |         |          Huzzah32 Bluetooth
    |         |         ---------------------
    |D28(RX7) |<------->| D17(TX1)          |
    |D29(TX7) |<------->| D16(RX1)          |
    |GND      |<------->| GND               |
    ----------          ---------------------

Mounting of the Teensy4.1 and Huzzah32 Bluetooth can be done in a breadboard which simplifies the creation of connections. The Myoware Muscle Sensor should be placed on the bicep with one electrode centered along the muscle and the other electrode in line with the muscle fibers in the direction of the elbow. The reference electrode should be placed in a location that will not be affected by muscle activation. Testing has shown that placing the electrode on the inner side of the arm is an effective reference point.

Once the Myoware sensor is mounted and connected to the Teensy4.1 the device should be turned on. The Teensy should be flashed using Teensyduino IDE and teensy_sd_sample.ino. From there either the Serial Monitor or the Serial Plotter can be launced from the Teensyduino IDE. The Serial monitor will output whenever a repetition is performed and the median frequency for that repitition. The Serial Plotter will display in realtime the median frequencies being received for each repetition. 

Optionally the android application can be run to view the median frequency values remotely. The application presents a simple interface in which a series of buttons are enabled to guide a user through the process of connecting to the Huzzah32 BLE server. Once connected completing a repetition will result in the median frequency being displayed on the android application.

## Future Work
Within this implementation there are several areas with potential for improvement. Likely the
most beneficial area for improvement would be in optimizing the rep detection handled within the 
loop function. As of now the RMS of the EMG signal is calculated several times per second. Once
The RMS value reaches a minimum threshold it can be determined that a rep has been performed and
additional reps are disabled for a short period of time. This threshold is hard coded and may not 
apply for other muscle groups or for other users, however it was an effective metric for determining
repetitions during testing

Additional work could also be completed in the display. The current implementation provides the median frequency directly to the user. While a lowering median frequency is associated with increased muscle fatigue this is not the most user friendly interface. One method of quantifying this data more plainly could be to present the slope of the median frequency rather than the median frequency itself. In this case you would simply track the previous several median frequencies and provide the user with the average slope between them. This would provide the user with a value that should generally be considered to be decreasing.

The current implementation determines median frequency by taking the magnitude of each frequency times its frequency component and dividing by the total number of frequencies. This implementation is not widely discussed in literature. Future improvements could focus on the use of a more standard algorithm for computing median frequency such as using Power Spectrum Density (PSD) calculations. The current implementation seems to work but variations in the testing environment and lack of a full scale trial with multiple users makes it difficult to confirm that the current implementation accurately reflects the median frequency of the EMG signal.

Finally the FFT calculations could be improved in the future. This implementation utilizes a single frame of FFT representing one second worth of data. The data is normalized using a Hanning window to ensure that the frequency components on the edge are matching. The Hanning window reduces the magnitude of the frequency components at the top and bottom edges of the window. This could be counteraceted by utilizing overlapped frames and calculating multple frames per repetition.
