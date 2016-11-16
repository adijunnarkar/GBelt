#include <SPI.h>
#include "MPU9250.h"

#define SerialDebug true  // Set to true to get Serial output for debugging

// Pin definitions
int intPin = 12;  // These can be changed, 2 and 3 are the Arduinos ext int pins

MPU9250 myIMU;
int sample_counter = 0;

void setup()
{
    delay(5000);
    Wire.begin();
    // TWBR = 12;  // 400 kbit/sec I2C speed
    Serial.begin(38400);

    // Set up the interrupt pin, its set as active high, push-pull
    pinMode(intPin, INPUT);
    digitalWrite(intPin, LOW);

    // Read the WHO_AM_I register, this is a good test of communication
    byte c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);
    Serial.print("MPU9250 "); Serial.print("I AM "); Serial.print(c, HEX);
    Serial.print(" I should be "); Serial.println(0x71, HEX);
    if (c == 0x71) // WHO_AM_I should always be 0x71
    {
        Serial.println("MPU9250 is online...");
        myIMU.initMPU9250();
        // Initialize device for active mode read of acclerometer, gyroscope, and
        // temperature
        Serial.println("MPU9250 initialized for active data mode....");
        delay(500);
        // Read the WHO_AM_I register of the magnetometer, this is a good test of
        // communication
        byte d = myIMU.readByte(AK8963_ADDRESS, WHO_AM_I_AK8963);
        Serial.print("AK8963 "); Serial.print("I AM "); Serial.print(d, HEX);
        Serial.print(" I should be "); Serial.println(0x48, HEX);
    }
}

void loop()
{
  // If intPin goes high, all data registers have new data
  // On interrupt, check if data ready interrupt
  if (myIMU.readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01)
  {
    sample_counter++;
    myIMU.readAccelData(myIMU.accelCount);  // Read the x/y/z adc values
    myIMU.getAres();

    // Now we'll calculate the accleration value into actual g's
    // This depends on scale being set
    myIMU.ax = (float)myIMU.accelCount[0]*myIMU.aRes; // - accelBias[0];
    myIMU.ay = (float)myIMU.accelCount[1]*myIMU.aRes; // - accelBias[1];
    myIMU.az = (float)myIMU.accelCount[2]*myIMU.aRes; // - accelBias[2];

    myIMU.readGyroData(myIMU.gyroCount);  // Read the x/y/z adc values
    myIMU.getGres();

    // Calculate the gyro value into actual degrees per second
    // This depends on scale being set
    myIMU.gx = (float)myIMU.gyroCount[0]*myIMU.gRes;
    myIMU.gy = (float)myIMU.gyroCount[1]*myIMU.gRes;
    myIMU.gz = (float)myIMU.gyroCount[2]*myIMU.gRes;

    myIMU.readMagData(myIMU.magCount);  // Read the x/y/z adc values
    myIMU.getMres();
    // User environmental x-axis correction in milliGauss, should be
    // automatically calculated
    myIMU.magbias[0] = +470.;
    // User environmental x-axis correction in milliGauss TODO axis??
    myIMU.magbias[1] = +120.;
    // User environmental x-axis correction in milliGauss
    myIMU.magbias[2] = +125.;

    // Calculate the magnetometer values in milliGauss
    // Include factory calibration per data sheet and user environmental
    // corrections
    // Get actual magnetometer value, this depends on scale being set
    myIMU.mx = (float)myIMU.magCount[0]*myIMU.mRes*myIMU.magCalibration[0] -
               myIMU.magbias[0];
    myIMU.my = (float)myIMU.magCount[1]*myIMU.mRes*myIMU.magCalibration[1] -
               myIMU.magbias[1];
    myIMU.mz = (float)myIMU.magCount[2]*myIMU.mRes*myIMU.magCalibration[2] -
               myIMU.magbias[2];
  } // if (readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01)

  if(SerialDebug)
  {
    Serial.print("Sample Count: "); Serial.println(sample_counter);

    // Print acceleration values in milligs!
    Serial.print("X-acceleration: "); Serial.print(1000*myIMU.ax);
    Serial.print(" mg ");
    Serial.print("Y-acceleration: "); Serial.print(1000*myIMU.ay);
    Serial.print(" mg ");
    Serial.print("Z-acceleration: "); Serial.print(1000*myIMU.az);
    Serial.println(" mg ");
  
    // Print gyro values in degree/sec
    Serial.print("X-gyro rate: "); Serial.print(myIMU.gx, 3);
    Serial.print(" degrees/sec ");
    Serial.print("Y-gyro rate: "); Serial.print(myIMU.gy, 3);
    Serial.print(" degrees/sec ");
    Serial.print("Z-gyro rate: "); Serial.print(myIMU.gz, 3);
    Serial.println(" degrees/sec");
  
    // Print mag values in degree/sec
    Serial.print("X-mag field: "); Serial.print(myIMU.mx);
    Serial.print(" mG ");
    Serial.print("Y-mag field: "); Serial.print(myIMU.my);
    Serial.print(" mG ");
    Serial.print("Z-mag field: "); Serial.print(myIMU.mz);
    Serial.println(" mG");

    Serial.print("\n\n");
  }
  delay(100);
}
