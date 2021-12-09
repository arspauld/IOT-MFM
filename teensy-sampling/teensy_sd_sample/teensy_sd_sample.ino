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
//#include <SD.h>

// Handles most of the signal processing
#include "arduinoFFT.h"

#define EMG_PIN A0                        // Analog pin connected to EMG sensor
//#define chipSelect BUILTIN_SDCARD         // Identify SD card for writing
#define EMG_FREQ 1024                     // EMG sampling frequency
#define FFT_SIZE 1024                     // Size of FFT buffer (~1.0s)
#define RMS_SIZE 128                      // Size of RMS buffer (~0.125s)
#define BUFF_SIZE 5120                    // Number of samples to take for normalization (~3s)

#define LED_isr_trigger 26                // Signals sampling in progress

// Declare arduino objects
ADC *adc = new ADC();
arduinoFFT fft_obj = arduinoFFT();

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
uint32_t RMS_buff_0[RMS_SIZE];            // RMS ping pong buffer 0
uint32_t RMS_buff_1[RMS_SIZE];            // RMS ping pong buffer 1
uint32_t *sample_RMS_buff = RMS_buff_0;   // RMS buffer currently receiving samples
uint32_t *process_RMS_buff = RMS_buff_0;  // RMS buffer currently being processed


/* Control Signals */
uint32_t process_RMS = 0;                 // flag to indicate that RMS should be processed
uint32_t RMS_delay = 0;                // timer to delay before allowing process_rms
uint32_t rep = 0;                         // Repetition count
uint32_t startup_counter = 4096;          // Wait four seconds before calculating reps
uint32_t rep_trigger = 0;                 // Tracks if a repetition has been performed


/*
 * @param *buff - buffer containing data to calculate RMS
 * @param size - size of buff array
 * 
 * @return unsigned int - Normalized RMS value of buffer
 */
unsigned int calculate_RMS(uint32_t *buff, unsigned int size);

/*
 * @param *buff0 - initiali buffer to clear
 * @param *buff1 - second buffer to clear
 * @param size - number of indices to clear
 */
 void clear_buffers(void *buff0, void *buff1, uint32_t size);

/*
 * @param *buff - pointer to buffer containing frequency magnitude
 * @param size - size of buffer 
 * @param fs - sampling frequency
 */
uint32_t calculate_median_frequency(double *buff, uint32_t size, uint32_t fs);

void setup() {

  // Initialize serial for debugging

  Serial.begin(115200);
  while(!Serial);  

  // Initialize serial for bluetooth
//  Serial7.begin(115200);
//  while(!Serial7);

//  Serial.println("Initializing SD card...");
//
//  
//  // Initialize SD card
//  if (!SD.begin(chipSelect)) {
//    Serial.println("Card failed, or not present");
//    while (1);
//  }
//  Serial.println("card initialized.");

  // Initialize ADC
  pinMode(EMG_PIN, INPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(LED_isr_trigger, OUTPUT);
  digitalWrite(LED_isr_trigger, 1);
  digitalWrite(LED_BUILTIN, HIGH);
  adc->adc0->setAveraging(0);
  adc->adc0->setResolution(10);
  adc->adc0->setConversionSpeed(ADC_CONVERSION_SPEED::VERY_HIGH_SPEED);
  adc->adc0->setSamplingSpeed(ADC_SAMPLING_SPEED::VERY_HIGH_SPEED);


  // clear the average buffer
  memset(ADC0_BUFFER, 480, sizeof(unsigned int) * BUFF_SIZE);
  
  // Initialize the timer to EMG Frequency (1024Hz) and enable ISR
  adc->adc0->stopTimer();
  adc->adc0->startSingleRead(EMG_PIN);
  adc->adc0->enableInterrupts(adc0_isr);
  adc->adc0->startTimer(EMG_FREQ);

  // Initialize FFT
  clear_buffers(vreal, vimag, FFT_SIZE*sizeof(double));



}

void loop() {

  uint32_t val = 0;
  if(process_RMS)
  {
    val = calculate_RMS(process_RMS_buff, RMS_SIZE) ;
    if(val > 8)
    {
      Serial.println("REP");
      rep++;
      rep_trigger = 1;
      RMS_delay = 2048;
    }
    
    process_RMS = 0;
  }

  // process fft for muscle fatigue
  if(rep_trigger)
  {

    // disable next repetition
    rep_trigger = 0;

    clear_buffers(vreal, vimag, FFT_SIZE*sizeof(double));
    
    // fill vreal array
    for(uint32_t i = 0; i < FFT_SIZE; i++)
      vreal[i] = process_fft_buff[i];

    // apply window to signal
    fft_obj.Windowing(vreal, FFT_SIZE, FFT_WIN_TYP_HANN, FFT_FORWARD);

    // compute fft
    fft_obj.Compute(vreal, vimag, FFT_SIZE, FFT_FORWARD);

    // translate to magnitude
    fft_obj.ComplexToMagnitude(vreal, vimag, FFT_SIZE);

/*
    // determine if majority component is noise
    uint32_t largest_comp = fft_obj.MajorPeak(vreal, FFT_SIZE, EMG_FREQ);

    // remove 60hz noise and sidebands
    switch(largest_comp)
    {
      case 58:
      case 59:
      case 60:
      case 61:
      case 62:
          vreal[59] = 0;
          vreal[60] = 0;
          vreal[61] = 0;
          vreal[62] = 0;
          vreal[63] = 0;

          vreal[119] = 0;
          vreal[120] = 0;
          vreal[121] = 0;
      default: break;
    }*/


    // Calculate median frequency
    Serial.println(calculate_median_frequency(vreal, FFT_SIZE, EMG_FREQ));
//    Serial7.println(calculate_median_frequency(vreal, FFT_SIZE, EMG_FREQ));


    // clear the buffers :(
    clear_buffers(vreal, vimag, FFT_SIZE*sizeof(double));
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

  if(startup_counter > 0) startup_counter--;
  if(RMS_delay > 0) RMS_delay--;

  // Swap buffers if sample count is full
  if(RMS_sample_count == RMS_SIZE)
  {
    sample_RMS_buff = (sample_RMS_buff == RMS_buff_0) ? RMS_buff_1 : RMS_buff_0;
    process_RMS_buff = (sample_RMS_buff == RMS_buff_0) ? RMS_buff_0 : RMS_buff_1;
    if(startup_counter == 0 && RMS_delay == 0)
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


 void clear_buffers(void *buff0, void *buff1, uint32_t size)
 {
  memset(buff0, 0, size);
  memset(buff1, 0, size);
 }


uint32_t calculate_median_frequency(double *buff, uint32_t size, uint32_t fs)
{
  // calculate frequency/index
  double quant_step = fs/size;
  
  // FFT result is mirrored, so we can ignore the back half
  uint32_t sum_freq = 0;
  double weighted_sum = 0;
  for(uint32_t i = 0; i<size/2; i++)
  {
    // calculate sum of frequency bins
    sum_freq += i*quant_step;

    // calculate the weighted sum of each frequency
    weighted_sum += i*quant_step*buff[i];

  }

  // return quotient of sums
  return (uint32_t) (weighted_sum/sum_freq);
}
