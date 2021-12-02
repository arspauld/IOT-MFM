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
#include <ADC_util.h>
#include <SD.h>

#define BUFF_SIZE 10240
#define EMG_PIN A0
#define EMG_FREQ 1024
#define chipSelect BUILTIN_SDCARD
#define FFT_SIZE 256

#define isr_poll_pin 26

ADC *adc = new ADC();

uint16_t ADC0_BUFFER[BUFF_SIZE];
uint16_t isr_poll_flag = HIGH;
uint32_t adc_count = 0;
uint16_t write_needed = 1;

uint32_t fft_sample_count = 0;
uint32_t fft_buff_0[FFT_SIZE];
uint32_t fft_buff_1[FFT_SIZE];
uint32_t *fft_buff_active;

uint32_t mav_sample_count = 0;
uint32_t mav_buff_0[FFT_SIZE];
uint32_t mav_buff_1[FFT_SIZE];
uint32_t *mav_buff_active;

void setup() {
  // put your setup code here, to run once:

  // Initialize serial for debugging
  Serial.begin(115200);
  while(!Serial)
  {
    ;  
  }

  Serial.println("Initializing SD card...");

  // Try to initialize SD card
  
  // see if the card is present and can be initialized:
  if (!SD.begin(chipSelect)) {
    Serial.println("Card failed, or not present");
    // don't do anything more:
    while (1);
  }
  Serial.println("card initialized.");

  // Initialize ADC
  pinMode(EMG_PIN, INPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(isr_poll_pin, OUTPUT);
  digitalWrite(isr_poll_pin, isr_poll_flag);
  digitalWrite(LED_BUILTIN, HIGH);
  adc->adc0->setAveraging(0);
  adc->adc0->setResolution(10);
  adc->adc0->setConversionSpeed(ADC_CONVERSION_SPEED::VERY_HIGH_SPEED);
  adc->adc0->setSamplingSpeed(ADC_SAMPLING_SPEED::VERY_HIGH_SPEED);

  adc->adc0->stopTimer();
  adc->adc0->startSingleRead(EMG_PIN);
  adc->adc0->enableInterrupts(adc0_isr);
  adc->adc0->startTimer(EMG_FREQ);


}

void loop() {
  // put your main code here, to run repeatedly:

}

void adc0_isr()
{

  isr_poll_flag = !isr_poll_flag;
  digitalWrite(isr_poll_pin, isr_poll_flag);
  String data = "";
  uint16_t adc_val = adc->adc0->readSingle();
  if(adc_count < BUFF_SIZE)
  {
    ADC0_BUFFER[adc_count++] = adc_val;
  } else if (write_needed)
  {
    write_needed = 0;
    File dataFile = SD.open("log8.csv", FILE_WRITE);
    for(int i=0; i<BUFF_SIZE; i++)
      dataFile.println(String(ADC0_BUFFER[i]));
    digitalWrite(LED_BUILTIN, LOW);
    dataFile.close();
    //Serial.println(String(adc_val));
  }
  
  
  asm("DSB");  
}

/*
// ISR to fill FFT and MAV Ping Pong buffers
void adc0_isr()
{
  // Read the current sample value
  unsigned int sample = adc->adc0->readSingle();
  
  // Swap buffers if sample count is full
  if(fft_sample_count == FFT_SIZE)
  {
    fft_buff_active = (fft_buff_active == fft_buff_0) ? fft_buff_1 : fft_buff_0;
    fft_sample_count = 0;
  }

  // Read the adc value into the active buffer
  fft_buff_active[fft_sample_count++] = sample;

  // Swap buffers if sample count is full
  if(mav_sample_count == MAV_SIZE)
  {
    mav_buff_active = (mav_buff_active == mav_buff_0) ? mav_buff_1 : mav_buff_0;
    mav_sample_count = 0;
  }

  // Read the adc value into the active buffer
  mav_buff_active[mav_sample_count++] = sample;
}*/

/*
unsigned int calculate_MAV(unsigned int &buff, unsigned int size, unsigned int sampling frequency)
{
  unsigned int mean = 0;
  for (int i = 0; i<size; i++)
  {
    mean += buff[i];
  }
  mean = mean/size;

  int norm_buff[size];
  
}*/
