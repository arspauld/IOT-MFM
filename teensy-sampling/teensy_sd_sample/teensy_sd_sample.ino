/*  TEENSY SD SAMPLING
 *   
 *   This program is designed to continuously poll
 *   the analog input pins to record an accurate EMG 
 *   signal from the Myoware EMG sensor. The data from
 *   the EMG sensor will be written to the inserted SD
 *   to be analyzed separately.
 *   
 *   Author: Alex Spaulding
 *   Date: November 16, 2021
 *   Class: CPE621 - Advanced Embedded Systems
 *   Professor: Dr. Emil Jovanov
 *   
 *   EMG_PIN - pin 14 (A0)
 * 
 */

#include <ADC.h>
#include <SD.h>

// Handles most of the signal processing
#include "arduinoFFT.h"

#define EMG_PIN A0                        // Analog pin connected to EMG sensor
#define chipSelect BUILTIN_SDCARD         // Identify SD card for writing
#define EMG_FREQ 1024                     // EMG sampling frequency
#define FFT_SIZE 1024                     // Size of FFT buffer (~1.0s)
#define RMS_SIZE 128                      // Size of RMS buffer (~0.125s)
#define BUFF_SIZE 5120                    // Number of samples to take for normalization (~3s)

#define LED_isr_trigger 26                // Signals sampling in progress

// Declare arduino objects
ADC *adc = new ADC();
arduinoFFT fft_obj = new arduinoFFT();

uint32_t ADC0_BUFFER[BUFF_SIZE];          // Holds previous values for rolling average
uint32_t adc_count = 0;                   // Holds the current index for ADC0_BUFFER
uint32_t write_needed = 1;                // For SD card buffering

uint32_t fft_sample_count = 0;            // Current index for fft ping pong buffer
uint32_t fft_buff_0[FFT_SIZE];            // FFT ping pong buffer 0 
uint32_t fft_buff_1[FFT_SIZE];            // FFT ping pong buffer 1
uint32_t *sample_fft_buff = fft_buff_0;   // FFT buffer currently receiving samples
uint32_t *process_fft_buff = fft_buff_0;  // FFT buffer currently being processed

double vreal[FFT_SIZE];                   // real component of fft function
double vimag[FFT_SIZE];                   // imaginary component of fft function



uint32_t RMS_sample_count = 0;            // current index for RMS ping pong buffer
uint32_t process_RMS = 0;                 // flag to indicate that RMS should be processed
uint32_t RMS_buff_0[RMS_SIZE];            // RMS ping pong buffer 0
uint32_t RMS_buff_1[RMS_SIZE];            // RMS ping pong buffer 1
uint32_t *sample_RMS_buff = RMS_buff_0;   // RMS buffer currently receiving samples
uint32_t *process_RMS_buff = RMS_buff_0;  // RMS buffer currently being processed
uint32_t rep_trigger = 0;                 // Tracks if a repetition has been performed


uint32_t rep = 0;                         // Repetition count


/*
 * @param *buff - buffer containing data to calculate RMS
 * @param size - size of buff array
 * 
 * @return unsigned int - Normalized RMS value of buffer
 */
unsigned int calculate_RMS(uint32_t *buff, unsigned int size);

void setup() {
  // put your setup code here, to run once:

  // Initialize serial for debugging
  Serial.begin(115200);
  while(!Serial);  

  Serial.println("Initializing SD card...");

  // Try to initialize SD card
  
  // Initialize SD card
  if (!SD.begin(chipSelect)) {
    Serial.println("Card failed, or not present");
    while (1);
  }
  Serial.println("card initialized.");

  // Initialize ADC
  pinMode(EMG_PIN, INPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(LED_isr_trigger, OUTPUT);
  digitalWrite(LED_isr_trigger, isr_poll_flag);
  digitalWrite(LED_BUILTIN, HIGH);
  adc->adc0->setAveraging(0);
  adc->adc0->setResolution(10);
  adc->adc0->setConversionSpeed(ADC_CONVERSION_SPEED::VERY_HIGH_SPEED);
  adc->adc0->setSamplingSpeed(ADC_SAMPLING_SPEED::VERY_HIGH_SPEED);

  // Initialize the timer to EMG Frequency (1024Hz) and enable ISR
  adc->adc0->stopTimer();
  adc->adc0->startSingleRead(EMG_PIN);
  adc->adc0->enableInterrupts(adc0_isr);
  adc->adc0->startTimer(EMG_FREQ);


}

void loop() {
  // put your main code here, to run repeatedly:
//  Serial.println("test");
  uint32_t val = 0;
  if(process_RMS)
  {
    val = calculate_RMS(process_RMS_buff, RMS_SIZE) ;
    if(val > 4)
    {
      Serial.print("REP");
      Serial.println(rep++);
    }
    
    process_RMS = 0;
  }

  if(rep_trigger)
  {
    // Process fft for muscle fatigue value
  }

}




// ISR to fill FFT and RMS Ping Pong buffers
void adc0_isr()
{
  // Read the current sample value
  unsigned int sample = adc->adc0->readSingle();

  // Place data into normalization buffer
  ADC0_BUFFER[adc_count++] = sample;
  if(adc_count == BUFF_SIZE) adc_count = 0;
  
  // Swap buffers if sample count is full
  if(fft_sample_count == FFT_SIZE)
  {
    sample_fft_buff = (sample_fft_buff == fft_buff_0) ? fft_buff_1 : fft_buff_0;
    fft_sample_count = 0;
  }

  // Read the adc value into the active buffer
  sample_fft_buff[fft_sample_count++] = sample;

  // Swap buffers if sample count is full
  if(RMS_sample_count == RMS_SIZE)
  {
    sample_RMS_buff = (sample_RMS_buff == RMS_buff_0) ? RMS_buff_1 : RMS_buff_0;
    process_RMS_buff = (sample_RMS_buff == RMS_buff_0) ? RMS_buff_0 : RMS_buff_1;
    process_RMS = 1;
    RMS_sample_count = 0; 
  }

  // Read the adc value into the active buffer
  sample_RMS_buff[RMS_sample_count++] = sample;
}


unsigned int calculate_RMS(uint32_t *buff, unsigned int size)
{

  // Take the rolling average for normalization
  int32_t mean = 0;
  for(int32_t i = 0; i<BUFF_SIZE; i++)
    mean += ADC0_BUFFER[i];

  mean = mean / BUFF_SIZE;

  // Accumulate the squared values
  int32_t accumulator = 0;
  for(uint32_t i = 0; i< size; i++)
  {
    accumulator += pow(buff[i], 2);
  }

  // take the square root and normalize
  return (uint32_t) abs(sqrt(accumulator / (float) size) - mean) ;
  
}




/*
void adc0_isr()
{

  isr_poll_flag = !isr_poll_flag;
  digitalWrite(LED_isr_trigger, isr_poll_flag);
  String data = "";
  uint16_t adc_val = adc->adc0->readSingle();
  if(adc_count < BUFF_SIZE || !write_needed)
  {
    ADC0_BUFFER[adc_count++ % BUFF_SIZE] = adc_val;
  } else if (write_needed)
  {
    write_needed = 0;
    File dataFile = SD.open("log13.csv", FILE_WRITE);
    for(int i=0; i<BUFF_SIZE; i++)
      dataFile.println(String(ADC0_BUFFER[i]));
    digitalWrite(LED_BUILTIN, LOW);
    dataFile.close();
    //Serial.println(String(adc_val));
  }
  
  
  asm("DSB");  
}*/
