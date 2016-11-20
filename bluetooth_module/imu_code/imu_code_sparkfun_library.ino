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
        Serial.println("MPU9250 (accel and gyro) is online...");
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

        if (d == 0x48) // AK8963 WHO_AM_I should always be 0x48
        {
            Serial.println("AK8963 (magnetometer) is online...");
            myIMU.initAK8963(myIMU.magCalibration);
            //calibrateMagnetometerBias(myIMU.magbias);
            Serial.println("AK8963 initialized for active data mode....");
            if (SerialDebug)
            {
                Serial.print("X-Axis sensitivity adjustment value ");
                Serial.println(myIMU.magCalibration[0], 2);
                Serial.print("Y-Axis sensitivity adjustment value ");
                Serial.println(myIMU.magCalibration[1], 2);
                Serial.print("Z-Axis sensitivity adjustment value ");
                Serial.println(myIMU.magCalibration[2], 2);  

                //Serial.print("Magnetometer Bias Values calculated: ");
                //Serial.print("X-Axis Bias: "); Serial.print(myIMU.magbias[0]);
                //Serial.print("  Y-Axis Bias: "); Serial.print(myIMU.magbias[1]);
                //Serial.print("  Z-Axis Bias: "); Serial.println(myIMU.magbias[2]);
            }
            delay(100);
        }
    }
    else
    {
        Serial.print("Could not connect to MPU9250: 0x");
        Serial.println(c, HEX);
        while(1); // Loop forever if communication doesn't happen
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
    //getActualAccelerometerValues();
    myIMU.ax = (float)myIMU.accelCount[0]*myIMU.aRes; // - accelBias[0];
    myIMU.ay = (float)myIMU.accelCount[1]*myIMU.aRes; // - accelBias[1];
    myIMU.az = (float)myIMU.accelCount[2]*myIMU.aRes; // - accelBias[2];
    
    myIMU.readGyroData(myIMU.gyroCount);  // Read the x/y/z adc values
    myIMU.getGres();
    //getActualGyroscopeValues();
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
    //getActualMagnetometerValues();
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
    Serial.print("X-accel: "); Serial.print(1000*myIMU.ax);
    Serial.print(" mg ");
    Serial.print("Y-accel: "); Serial.print(1000*myIMU.ay);
    Serial.print(" mg ");
    Serial.print("Z-accel: "); Serial.print(1000*myIMU.az);
    Serial.println(" mg ");
  
    // Print gyro values in degree/sec
    Serial.print("X-gyro rate: "); Serial.print(myIMU.gx, 3);
    Serial.print(" deg/s ");
    Serial.print("Y-gyro rate: "); Serial.print(myIMU.gy, 3);
    Serial.print(" deg/s ");
    Serial.print("Z-gyro rate: "); Serial.print(myIMU.gz, 3);
    Serial.println(" deg/s");
  
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

void getActualMagnetometerValues()
{
    /*
    Calculate the magnetometer values in milliGauss
    Include factory calibration per data sheet and user environmental corrections
    Get actual magnetometer value, this depends on scale being set.
    */

    myIMU.mx = (float)myIMU.magCount[0]*myIMU.mRes*myIMU.magCalibration[0] -
               myIMU.magbias[0];
    myIMU.my = (float)myIMU.magCount[1]*myIMU.mRes*myIMU.magCalibration[1] -
               myIMU.magbias[1];
    myIMU.mz = (float)myIMU.magCount[2]*myIMU.mRes*myIMU.magCalibration[2] -
               myIMU.magbias[2];
}

void getActualAccelerometerValues()
{
    /*
    Calculate the acceleration value into actual g's
    This depends on scale being set.
    */

    myIMU.ax = (float)myIMU.accelCount[0]*myIMU.aRes; // - accelBias[0];
    myIMU.ay = (float)myIMU.accelCount[1]*myIMU.aRes; // - accelBias[1];
    myIMU.az = (float)myIMU.accelCount[2]*myIMU.aRes; // - accelBias[2];
} 

void getActualGyroscopeValues()
{
    /*
    Calculate the gyro value into actual degrees per second
    This depends on scale being set.
    */

    myIMU.gx = (float)myIMU.gyroCount[0]*myIMU.gRes;
    myIMU.gy = (float)myIMU.gyroCount[1]*myIMU.gRes;
    myIMU.gz = (float)myIMU.gyroCount[2]*myIMU.gRes;
}

void calibrateMagnetometerBias(float * dest1)
{
    /*
    Calibrate magnetometer by waving device in a variety of figure eight patters.
    
    The 'magBias' (x, y, and z) values are needed to ensure that the appropriate offset 
    is used when calculating the actual magnetometer values.

    Currently only implementing the hard iron correction for magnetometer.
    */

    uint16_t ii = 0, sample_count = 0;
    int32_t mag_bias[3] = {0, 0, 0};
    int16_t mag_max[3] = {-32767, -32767, -32767}, mag_min[3] = {32767, 32767, 32767}, mag_temp[3] = {0, 0, 0};

    Serial.println("Mag Calibration: Wave device in a figure eight until done!");
    delay(4000);

    // shoot for ~fifteen seconds of mag data
    if(Mmode == 0x02) sample_count = 128;  // at 8 Hz ODR, new mag data is available every 125 ms
    if(Mmode == 0x06) sample_count = 1500;  // at 100 Hz ODR, new mag data is available every 10 ms
    for(ii = 0; ii < sample_count; ii++) {
        myIMU.readMagData(mag_temp);  // Read the mag data   
        for (int jj = 0; jj < 3; jj++) {
          if(mag_temp[jj] > mag_max[jj]) mag_max[jj] = mag_temp[jj];
          if(mag_temp[jj] < mag_min[jj]) mag_min[jj] = mag_temp[jj];
        }
        if(Mmode == 0x02) delay(135);  // at 8 Hz ODR, new mag data is available every 125 ms
        if(Mmode == 0x06) delay(12);  // at 100 Hz ODR, new mag data is available every 10 ms
    }

    Serial.println("mag x min/max:"); Serial.println(mag_max[0]); Serial.println(mag_min[0]);
    Serial.println("mag y min/max:"); Serial.println(mag_max[1]); Serial.println(mag_min[1]);
    Serial.println("mag z min/max:"); Serial.println(mag_max[2]); Serial.println(mag_min[2]);

    // Get hard iron correction
    mag_bias[0]  = (mag_max[0] + mag_min[0])/2;  // get average x mag bias in counts
    mag_bias[1]  = (mag_max[1] + mag_min[1])/2;  // get average y mag bias in counts
    mag_bias[2]  = (mag_max[2] + mag_min[2])/2;  // get average z mag bias in counts

    dest1[0] = (float) mag_bias[0]*myIMU.mRes*myIMU.magCalibration[0];  // save mag biases in G for main program
    dest1[1] = (float) mag_bias[1]*myIMU.mRes*myIMU.magCalibration[1];   
    dest1[2] = (float) mag_bias[2]*myIMU.mRes*myIMU.magCalibration[2];
    Serial.println("Mag Calibration done!");
}
