//"services.h/spi.h/boards.h" is needed in every new project
#include <SPI.h>
#include <boards.h>
#include <RBL_nRF8001.h>
#include <services.h>
#include <Servo.h> 
#include <SoftwareSerial.h>
#include <Wire.h>
#include "Adafruit_LEDBackpack.h"
#include "Adafruit_GFX.h"
 
#define DIGITAL_OUT_PIN    2
#define DIGITAL_IN_PIN     A4
#define PWM_PIN            3
#define SERVO_PIN          5
#define ANALOG_IN_PIN      A5

Adafruit_8x8matrix matrix = Adafruit_8x8matrix();
String str;

unsigned char buf[16] = {0};
unsigned char len = 0;

void setup()
{
  // Default pins set to 9 and 8 for REQN and RDYN
  // Set your REQN and RDYN here before ble_begin() if you need
  //ble_set_pins(3, 2);
  
  // Set your BLE Shield name here, max. length 10
  //ble_set_name("My Name");
  
  // Init. and start BLE library.
  ble_begin();
  
  Serial.begin(9600);  // Begin the serial monitor at 9600bps - Just so we can see on the serial monitor
  matrix.begin(0x70);
  Serial.println("8x8 LED Matrix Test");
  
//  // Enable serial debug
  Serial.begin(57600);

  matrix.clear();
}

void loop() {

  matrix.clear();
  matrix.setTextSize(1);
  matrix.setTextWrap(false);  // we dont want text to wrap so it scrolls nicely
  matrix.setTextColor(LED_ON);
  
  // If data is ready
  if (ble_available())
  {

    while ( ble_available() ) {
      char c = ble_read();
      str += (String)c;
    };
    Serial.println(str);
    int l = str.length()*6-1;
    
    for (int8_t x=0; x>=-l; x--) {
      matrix.clear();
      matrix.setCursor(x,0);
      matrix.print(str);
      matrix.writeDisplay();
      delay(100);
    }
    str = "";
  }
  
  // Allow BLE Shield to send/receive data
  ble_do_events();  
}

