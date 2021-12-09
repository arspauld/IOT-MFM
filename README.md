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
    -> ESP Transmit
        -> esp
            -> esp.ino - Arduino script for Huzzah32 Microcontroller intended to allow for 
               bluetooth communication with an android application. The huzzah32 would simply
               act as a bridge and bluetooth transmitted. Unforutnately the android appliction
               was not complete at the time of submission but this is an opportunity for future
               work on the project.

## Usage

    Teensy4.1           Myoware Muscle Sensor
    ----------          ---------------------
    |3.3V     |<------->| + (Power)
    |GND      |<------->| - (Ground)
    |A0       |<------->| RAW (EMG Output)
    |           
    |                    Huzzah32 bluetooth
    |                   ---------------------
    |D28(RX7) |<------->| D17(TX1)
    |D29(TX7) |<------->| D16(RX1)
    |GND      |<------->| GND
    ----------

## Future Work
Within this implementation there are several areas with potential for improvement. Likely the
most beneficial area for improvement would be in optimizing the rep detection handled within the 
loop function. As of now the RMS of the EMG signal is calculated several times per second. Once
The RMS value reaches a minimum threshold it can be determined that a rep has been performed and
additional reps are disabled for a short period of time. This threshold is hard coded and may not 
apply for other muscle groups or for other users, however it was an effective metric for determining
repetitions during testing

Additional work could also be completed in the display and calculation of muscle fatigue.
