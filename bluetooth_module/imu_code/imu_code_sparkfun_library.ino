#include <Arduino.h>
#include <SPI.h>
#include "quaternionFilters.h"
#include "MPU9250.h"
#include "math.h"
#include <SoftwareSerial.h>

#define SerialDebug false  // Set to true to get Serial output for debugging
#define avgCount 5 // set the number of samples to take to calculate an average
SoftwareSerial BTSerial(8, 9); // RX | Tx (10, 11 for Arduino Mega)

// Pin definitions
int intPin = 7;  // These can be changed, 2 and 3 are the Arduinos ext int pins
int ledNorth = 11; // mega is 3
int ledSouth = 6; // mega is 4
int ledWest = 5; // mega is 2
int ledEast = 10; //mega is 5

int sample_counter = 0;
int accel_gyro_connect_counter = 0;
int magnetometer_connect_counter = 0;
int pwm_intensity = 128;

float pitch, yaw, roll, Xh, Yh, theta = -1.0, thetaDesired;

float accelData[avgCount][3], magData[avgCount][3], gyroData[avgCount][3]; // raw IMU data values read stored here
float accelDataSum[3] = {0}, gyroDataSum[3] = {0}, magDataSum[3] = {0}; // sum of samples stored here
float accelAverage[3], gyroAverage[3], magAverage[3]; // averaged values stored here

//Char used for reading in Serial characters
char inByte = 0;

bool startReadingData = false;
bool newDirectionReady = false; // indicates when a new direction from phone is available
bool averageCalculated = false; // indicates when the average for 5 direction values have been calculated

static bool receiving_bluetooth = false; // set to true when we first receive info from bluetooth

String ledToLightUp = "";
String direction = "";

byte c = 0x00, d = 0x00;

MPU9250 myIMU;

void setup()
{
  delay(2000);
  Wire.begin();
  // TWBR = 12;  // 400 kbit/sec I2C speed
  Serial.begin(38400);
  BTSerial.begin(9600);
  delay(1000);
  
  // Set up the interrupt pin, its set as active high, push-pull
  pinMode(intPin, INPUT);
  pinMode(ledNorth, OUTPUT);
  pinMode(ledSouth, OUTPUT);
  pinMode(ledWest, OUTPUT);
  pinMode(ledEast, OUTPUT);

  digitalWrite(intPin, LOW);
  digitalWrite(ledNorth, LOW);
  digitalWrite(ledSouth, LOW);
  digitalWrite(ledWest, LOW);
  digitalWrite(ledEast, LOW);
  Serial.print("MPU9250 ");
  // Read the WHO_AM_I register
  do {
    c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);
    Serial.println(c, HEX);
    accel_gyro_connect_counter += 1;
    delay(500);
  } while (c != 0x71);

  Serial.print("MPU9250 "); Serial.print("I AM "); Serial.print(c, HEX);
  Serial.print(" I should be "); Serial.println(0x71, HEX); // WHO_AM_I should always be 0x71
  Serial.print("Tried connecting to MPU9250: "); Serial.print(accel_gyro_connect_counter); Serial.println(" time(s)");

  Serial.println("MPU9250 (accel and gyro) is online...");
  myIMU.initMPU9250();
  // Initialize device for active mode read of acclerometer, gyroscope, and
  // temperature
  Serial.println("MPU9250 initialized for active data mode....");
  delay(500);
  /*
      // Start by performing self test and reporting values
      myIMU.MPU9250SelfTest(myIMU.SelfTest);
      Serial.print("x-axis self test: acceleration trim within : ");
      Serial.print(myIMU.SelfTest[0],1); Serial.println("% of factory value");
      Serial.print("y-axis self test: acceleration trim within : ");
      Serial.print(myIMU.SelfTest[1],1); Serial.println("% of factory value");
      Serial.print("z-axis self test: acceleration trim within : ");
      Serial.print(myIMU.SelfTest[2],1); Serial.println("% of factory value");
      Serial.print("x-axis self test: gyration trim within : ");
      Serial.print(myIMU.SelfTest[3],1); Serial.println("% of factory value");
      Serial.print("y-axis self test: gyration trim within : ");
      Serial.print(myIMU.SelfTest[4],1); Serial.println("% of factory value");
      Serial.print("z-axis self test: gyration trim within : ");
      Serial.print(myIMU.SelfTest[5],1); Serial.println("% of factory value");
  */
  accelgyrocalMPU9250(myIMU.gyroBias, myIMU.accelBias);
  delay(1000);

  myIMU.writeByte(MPU9250_ADDRESS, INT_PIN_CFG, 0x22);
  Serial.print("WHO_AM_I_AK8963: "); Serial.println(WHO_AM_I_AK8963, HEX);

  // Read the WHO_AM_I register of the magnetometer
  do {
    d = myIMU.readByte(AK8963_ADDRESS, WHO_AM_I_AK8963);
    Serial.println(d, HEX);
    magnetometer_connect_counter += 1;
    delay(500);
  } while (d != 0x48);

  Serial.print("AK8963 "); Serial.print("I AM "); Serial.print(d, HEX);
  Serial.print(" I should be "); Serial.println(0x48, HEX); // AK8963 WHO_AM_I should always be 0x48
  Serial.print("Tried connecting to Magnetometer: "); Serial.print(magnetometer_connect_counter); Serial.println(" time(s)");

  Serial.println("AK8963 (magnetometer) is online...");
  myIMU.initAK8963(myIMU.magCalibration);
  calibrateMagnetometerBias(myIMU.magbias);
  Serial.println("AK8963 initialized for active data mode....");
  turnAllMotorsOff();

  if (SerialDebug)
  {
    Serial.print("X-Axis sensitivity adjustment value ");
    Serial.println(myIMU.magCalibration[0], 2);
    Serial.print("Y-Axis sensitivity adjustment value ");
    Serial.println(myIMU.magCalibration[1], 2);
    Serial.print("Z-Axis sensitivity adjustment value ");
    Serial.println(myIMU.magCalibration[2], 2);

    Serial.print("Magnetometer Bias Values calculated: ");
    Serial.print("X-Axis Bias: "); Serial.print(myIMU.magbias[0]);
    Serial.print("  Y-Axis Bias: "); Serial.print(myIMU.magbias[1]);
    Serial.print("  Z-Axis Bias: "); Serial.println(myIMU.magbias[2]);
  }
  delay(100);
}

void loop()
{
  // If intPin goes high, all data registers have new data
  // On interrupt, check if data ready interrupt
  /*analogWrite(ledNorth, 128);
  delay(2000);
  analogWrite(ledNorth, 0);
  analogWrite(ledSouth, 128);
  delay(2000);
  analogWrite(ledSouth, 0);
  analogWrite(ledWest, 128);
  delay(2000);
  analogWrite(ledWest, 0);
  analogWrite(ledEast, 128);
  delay(2000);
  analogWrite(ledEast, 0);*/
  if (myIMU.readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01)
  {
    myIMU.readAccelData(myIMU.accelCount);  // Read the x/y/z adc values
    myIMU.getAres();
    //getActualAccelerometerValues();
    myIMU.ax = (float)myIMU.accelCount[0] * myIMU.aRes - myIMU.accelBias[0];
    myIMU.ay = (float)myIMU.accelCount[1] * myIMU.aRes - myIMU.accelBias[1];
    myIMU.az = (float)myIMU.accelCount[2] * myIMU.aRes - myIMU.accelBias[2];

    // save accelData for averaging
    accelData[sample_counter][0] = myIMU.ax;
    accelData[sample_counter][1] = myIMU.ay;
    accelData[sample_counter][2] = myIMU.az;

    myIMU.readGyroData(myIMU.gyroCount);  // Read the x/y/z adc values
    myIMU.getGres();
    //getActualGyroscopeValues();
    myIMU.gx = (float)myIMU.gyroCount[0] * myIMU.gRes;
    myIMU.gy = (float)myIMU.gyroCount[1] * myIMU.gRes;
    myIMU.gz = (float)myIMU.gyroCount[2] * myIMU.gRes;

    // save gyroData for averaging
    gyroData[sample_counter][0] = myIMU.gx;
    gyroData[sample_counter][1] = myIMU.gy;
    gyroData[sample_counter][2] = myIMU.gz;

    myIMU.readMagData(myIMU.magCount);  // Read the x/y/z adc values
    myIMU.getMres();
    // User environmental x-axis correction in milliGauss, should be
    // automatically calculated
    // myIMU.magbias[0] = +470.;
    // User environmental x-axis correction in milliGauss TODO axis??
    // myIMU.magbias[1] = +120.;
    // User environmental x-axis correction in milliGauss
    // myIMU.magbias[2] = +125.;
    //getActualMagnetometerValues();
    myIMU.mx = (float)myIMU.magCount[0] * myIMU.mRes * myIMU.magCalibration[0] -
               myIMU.magbias[0];
    myIMU.my = (float)myIMU.magCount[1] * myIMU.mRes * myIMU.magCalibration[1] -
               myIMU.magbias[1];
    myIMU.mz = (float)myIMU.magCount[2] * myIMU.mRes * myIMU.magCalibration[2] -
               myIMU.magbias[2];

    // save magData for averaging
    magData[sample_counter][0] = myIMU.mx;
    magData[sample_counter][1] = myIMU.my;
    magData[sample_counter][2] = myIMU.mz;

    // average data if appropriate number of samples have been taken
    if (sample_counter == (avgCount - 1))
    {
      for (int i=0; i < avgCount; i++)
      {
        accelDataSum[0] += accelData[i][0];
        accelDataSum[1] += accelData[i][1];
        accelDataSum[2] += accelData[i][2];

        magDataSum[0] += magData[i][0];
        magDataSum[1] += magData[i][1];
        magDataSum[2] += magData[i][2];

        gyroDataSum[0] += gyroData[i][0];
        gyroDataSum[1] += gyroData[i][1];
        gyroDataSum[2] += gyroData[i][2];
      }
      accelAverage[0] = accelDataSum[0] / float(avgCount);
      accelAverage[1] = accelDataSum[1] / float(avgCount);
      accelAverage[2] = accelDataSum[2] / float(avgCount);

      magAverage[0] = magDataSum[0] / float(avgCount);
      magAverage[1] = magDataSum[1] / float(avgCount);
      magAverage[2] = magDataSum[2] / float(avgCount);

      gyroAverage[0] = gyroDataSum[0] / float(avgCount);
      gyroAverage[1] = gyroDataSum[1] / float(avgCount);
      gyroAverage[2] = gyroDataSum[2] / float(avgCount);
       
      averageCalculated = true;

      // reset sum arrays to zero
      memset(accelDataSum, 0, sizeof(accelDataSum));
      memset(magDataSum, 0, sizeof(magDataSum));
      memset(gyroDataSum, 0, sizeof(gyroDataSum));
      
      //Serial.println("[" + String(accelAverage[0]) + ", " + String(accelAverage[1]) + ", " + String(accelAverage[2]) + "]");
      //Serial.println("[" + String(magAverage[0]) + ", " + String(magAverage[1]) + ", " + String(magAverage[2]) + "]");
      //Serial.println("[" + String(gyroAverage[0]) + ", " + String(gyroAverage[1]) + ", " + String(gyroAverage[2]) + "]");
    }
    sample_counter = (sample_counter + 1) % avgCount;
  } // if (readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01)

  // Must be called before updating quaternions!
  //myIMU.updateTime();

  /* MahonyQuaternionUpdate(myIMU.ax, myIMU.ay, myIMU.az, myIMU.gx*PI/180.0f, myIMU.gy*PI/180.0f, myIMU.gz*PI/180.0f, myIMU.my, myIMU.mx, myIMU.mz, myIMU.deltat);
    myIMU.yaw = atan2(2.0f * (*(getQ()+1) * *(getQ()+2) + *getQ() *
            (getQ()+3)), *getQ() * *getQ() + *(getQ()+1) * *(getQ()+1)
           - *(getQ()+2) * *(getQ()+2) - *(getQ()+3) * *(getQ()+3));
    myIMU.pitch = -asin(2.0f * (*(getQ()+1) * *(getQ()+3) - *getQ() *
            (getQ()+2)));
    myIMU.roll = atan2(2.0f * (*getQ() * *(getQ()+1) + *(getQ()+2) *
            (getQ()+3)), *getQ() * *getQ() - *(getQ()+1) * *(getQ()+1)
           - *(getQ()+2) * *(getQ()+2) + *(getQ()+3) * *(getQ()+3));
    myIMU.pitch *= RAD_TO_DEG;
    myIMU.yaw   *= RAD_TO_DEG;
    myIMU.yaw   -= 9.65; // Declination at Waterloo, Ontario
    myIMU.roll  *= RAD_TO_DEG;
  */
  //pitch = atan2(myIMU.ay, sqrt(myIMU.ax * myIMU.ax + myIMU.az * myIMU.az)) * RAD_TO_DEG;

  /*pitch = atan2(myIMU.ay, sqrt(myIMU.ax*myIMU.ax + myIMU.az*myIMU.az)) * RAD_TO_DEG;
    roll = atan2(-myIMU.ax, myIMU.az) * RAD_TO_DEG;

    Xh = myIMU.mx * cos(pitch*DEG_TO_RAD) + myIMU.my * sin(roll*DEG_TO_RAD)*sin(pitch*DEG_TO_RAD) - myIMU.mz * cos(roll*DEG_TO_RAD)*sin(pitch*DEG_TO_RAD);
    Yh = myIMU.my * cos(roll*DEG_TO_RAD) + myIMU.mz * sin(roll*DEG_TO_RAD);
    yaw = atan2(Yh, Xh) * RAD_TO_DEG - 9.65;
    yaw = (360 - (int)yaw) % 360;
  */
  /* PITCH: tilting the body from side to side, ROLL: tilting the body forwards and backwards*/
    if (averageCalculated)
    {
        pitch = atan2(-accelAverage[0], sqrt(accelAverage[1] * accelAverage[1] + accelAverage[2] * accelAverage[2])) * RAD_TO_DEG; // bias of 15 degrees added
        roll = atan2(accelAverage[1], accelAverage[2]) * RAD_TO_DEG;

        /*Xh = -myIMU.my * cos(pitch * DEG_TO_RAD) - myIMU.mx * sin(roll * DEG_TO_RAD) * sin(pitch * DEG_TO_RAD) - myIMU.mz * cos(roll * DEG_TO_RAD) * sin(pitch * DEG_TO_RAD);
        Yh = -myIMU.mx * cos(roll * DEG_TO_RAD) + myIMU.mz * sin(roll * DEG_TO_RAD);
        yaw = atan2(Yh, Xh) * RAD_TO_DEG - 9.65;
        yaw = (360 - (int)yaw) % 360;*/
        Xh = magAverage[0] * cos(pitch * DEG_TO_RAD) + magAverage[1] * sin(roll * DEG_TO_RAD) * sin(pitch * DEG_TO_RAD) + magAverage[2] * cos(roll * DEG_TO_RAD) * sin(pitch * DEG_TO_RAD);
        Yh = magAverage[1] * cos(roll * DEG_TO_RAD) - magAverage[2] * sin(roll * DEG_TO_RAD);
        yaw = atan2(-Yh, Xh) * RAD_TO_DEG - 9.65;
        yaw = ((360 + (int)yaw) % 360); // bias of 15 degrees added
        Serial.println("Yaw: " + String(yaw) + "; Pitch: " + String(pitch) + "; Roll: " + String(roll));
    }

  if (BTSerial.available() > 0)
  {
    inByte = BTSerial.read();
    //Serial.println(inByte);
    if (inByte == '#')
    {
      receiving_bluetooth = true;
      startReadingData = true;
    }
    if (inByte == '~')
    {
      startReadingData = false;
      newDirectionReady = true;
    }
    if ((startReadingData) && (inByte != '#') && (inByte != '~'))
    {
      direction += inByte;
    }
  }

  if (newDirectionReady)
  {
    //turnAllLEDsOff();
    if (direction == "Stop")
    {
      turnAllMotorsOff();
      //Serial.println("STOP");
      receiving_bluetooth = false;
      while(Serial.available()){  //is there anything to read?
        char getData = Serial.read();  //if yes, read it
      }   // don't do anything with it.
    }
    else
    {
        thetaDesired = direction.toInt();
        Serial.println("Theta Received: " + String(thetaDesired));
    }
    newDirectionReady = false;
    direction = "";
  }


  if (receiving_bluetooth && averageCalculated) {
    // calculate theta to decide which motors to activate only if IMU reading average has been calculated
    if (yaw > thetaDesired) 
    {
        theta = 360 - (yaw - thetaDesired);
    }
    else {
        theta = thetaDesired - yaw;
    }

    Serial.println("Theta: " + String(theta));

    if (inRange(theta, 345, 360) || inRange(theta, 0, 15)) // North
    {
        turnAllMotorsOff();
        Serial.println("North Motors Active");
        analogWrite(ledNorth, pwm_intensity);
    }
    else if (inRange(theta, 15, 75)) // Northeast
    {
      turnAllMotorsOff();
      Serial.println("North and East Motors Active");
      analogWrite(ledNorth, pwm_intensity);
      analogWrite(ledEast, pwm_intensity);
    }
    else if (inRange(theta, 75, 105)) // East
    {
      turnAllMotorsOff();
      Serial.println("East Motors Active");
      analogWrite(ledEast, pwm_intensity);
    }
    else if (inRange(theta, 105, 165)) // Southeast
    {
      turnAllMotorsOff();
      Serial.println("South and East Motors Active");
      analogWrite(ledSouth, pwm_intensity);
      analogWrite(ledEast, pwm_intensity);
    }
    else if (inRange(theta, 165, 195)) // South
    {
      turnAllMotorsOff();
      Serial.println("South Motors Active");
      analogWrite(ledSouth, pwm_intensity);
    }
    else if (inRange(theta, 195, 255)) // Southwest
    {
      turnAllMotorsOff();
      Serial.println("South and West Motors Active");
      analogWrite(ledSouth, pwm_intensity);
      analogWrite(ledWest, pwm_intensity);
    }
    else if (inRange(theta, 255, 285)) // West
    {
      turnAllMotorsOff();
      Serial.println("West Motors Active");
      analogWrite(ledWest, pwm_intensity);
    }
    else if (inRange(theta, 285, 345)) // Northwest
    {
      turnAllMotorsOff();
      Serial.println("North and West Motors Active");
      analogWrite(ledNorth, pwm_intensity);
      analogWrite(ledWest, pwm_intensity);
    }
    delay(2000);
    averageCalculated = false;
  }

  if (SerialDebug)
  {
    Serial.print("Sample Count: "); Serial.println(sample_counter);

    // Print acceleration values in milligs!
    Serial.print("X-accel: "); Serial.print(1000 * myIMU.ax);
    Serial.print(" mg ");
    Serial.print("Y-accel: "); Serial.print(1000 * myIMU.ay);
    Serial.print(" mg ");
    Serial.print("Z-accel: "); Serial.print(1000 * myIMU.az);
    Serial.println(" mg ");

    // Print gyro values in degree/sec
    Serial.print("X-gyro rate: "); Serial.print(myIMU.gx, 3);
    Serial.print(" deg/s ");
    Serial.print("Y-gyro rate: "); Serial.print(myIMU.gy, 3);
    Serial.print(" deg/s ");
    Serial.print("Z-gyro rate: "); Serial.print(myIMU.gz, 3);
    Serial.println(" deg/s");

    // Print mag values in degree/sec
    Serial.print("X-mag field: "); Serial.print(myIMU.magCount[0]);
    Serial.print(" mG ");
    Serial.print("Y-mag field: "); Serial.print(myIMU.magCount[1]);
    Serial.print(" mG ");
    Serial.print("Z-mag field: "); Serial.print(myIMU.magCount[2]);
    Serial.println(" mG");
    /*  Serial.print("X-mag field: "); Serial.print(myIMU.mx);
      Serial.print(" mG ");
      Serial.print("Y-mag field: "); Serial.print(myIMU.my);
      Serial.print(" mG ");
      Serial.print("Z-mag field: "); Serial.print(myIMU.mz);
      Serial.println(" mG");
    */
    Serial.print("Manual Calcul. Yaw, Pitch, Roll: ");
    Serial.print(yaw, 2); Serial.print(", ");
    Serial.print(pitch, 2); Serial.print(", ");
    Serial.println(roll, 2);

    Serial.print("\n\n");
  }
  delay(50);
}

void turnAllMotorsOff()
{
    analogWrite(ledNorth, 0);
    analogWrite(ledSouth, 0);
    analogWrite(ledEast, 0);
    analogWrite(ledWest, 0);
}

void turnAllLEDsOff()
{
  digitalWrite(ledNorth, LOW);
  digitalWrite(ledSouth, LOW);
  digitalWrite(ledWest, LOW);
  digitalWrite(ledEast, LOW);
}

bool inRange(int val, int min, int max)
{
  return ((min <= val) && (val <= max));
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
  int32_t mag_bias[3] = {0, 0, 0}, mag_scale[3] = {0, 0, 0};
  int16_t mag_max[3] = { -32767, -32767, -32767}, mag_min[3] = {32767, 32767, 32767}, mag_temp[3] = {0, 0, 0};

  // Serial.println("Mag Calibration: Wave device in a figure eight until done!");
  // delay(4000);

  // shoot for ~fifteen seconds of mag data
  /*  if(Mmode == 0x02) sample_count = 128;  // at 8 Hz ODR, new mag data is available every 125 ms
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
  */
   mag_max[0] = 516; mag_min[0] = -112;
   mag_max[1] = 421; mag_min[1] = -98;
    mag_max[2] = -8; mag_min[2] = -575;

  //mag_max[0] = 493; mag_min[0] = -36;
  //mag_max[1] = 496;  mag_min[1] = -51;
  //mag_max[2] = 172;  mag_min[2] = -362;

  // Get hard iron correction
  mag_bias[0]  = (mag_max[0] + mag_min[0]) / 2; // get average x mag bias in counts
  mag_bias[1]  = (mag_max[1] + mag_min[1]) / 2; // get average y mag bias in counts
  mag_bias[2]  = (mag_max[2] + mag_min[2]) / 2; // get average z mag bias in counts
  myIMU.getMres();
  //Serial.println("Mag bias"); Serial.println(mag_bias[0]); Serial.println(mag_bias[1]);Serial.println(mag_bias[2]);
  dest1[0] = (float) mag_bias[0] * myIMU.mRes * myIMU.magCalibration[0]; // save mag biases in G for main program
  dest1[1] = (float) mag_bias[1] * myIMU.mRes * myIMU.magCalibration[1];
  dest1[2] = (float) mag_bias[2] * myIMU.mRes * myIMU.magCalibration[2];

  //Get Soft iron correction
  mag_scale[0]  = (mag_max[0] - mag_min[0]) / 2; // get average x axis max chord length in counts
  mag_scale[1]  = (mag_max[1] - mag_min[1]) / 2; // get average y axis max chord length in counts
  mag_scale[2]  = (mag_max[2] - mag_min[2]) / 2; // get average z axis max chord length in counts

  float avg_rad = (mag_scale[0] + mag_scale[1] + mag_scale[2]) / 3.0;

  //
  Serial.println("Mag Calibration done!");
}

// Function which accumulates gyro and accelerometer data after device initialization. It calculates the average
// of the at-rest readings and then loads the resulting offsets into accelerometer and gyro bias registers.
void accelgyrocalMPU9250(float * dest1, float * dest2)
{
  Serial.println("Calibrating MPU9250 (accel and gyro)...");
  delay(2000);
  uint8_t data[12]; // data array to hold accelerometer and gyro x, y, z, data
  uint16_t ii, packet_count, fifo_count;
  int32_t gyro_bias[3]  = {0, 0, 0}, accel_bias[3] = {0, 0, 0};

  // reset device
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x80); // Write a one to bit 7 reset bit; toggle reset device
  delay(100);

  // get stable time source; Auto select clock source to be PLL gyroscope reference if ready
  // else use the internal oscillator, bits 2:0 = 001
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x01);
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_2, 0x00);
  delay(200);

  // Configure device for bias calculation
  myIMU.writeByte(MPU9250_ADDRESS, INT_ENABLE, 0x00);   // Disable all interrupts
  myIMU.writeByte(MPU9250_ADDRESS, FIFO_EN, 0x00);      // Disable FIFO
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x00);   // Turn on internal clock source
  myIMU.writeByte(MPU9250_ADDRESS, I2C_MST_CTRL, 0x00); // Disable I2C master
  myIMU.writeByte(MPU9250_ADDRESS, USER_CTRL, 0x00);    // Disable FIFO and I2C master modes
  myIMU.writeByte(MPU9250_ADDRESS, USER_CTRL, 0x0C);    // Reset FIFO and DMP
  delay(15);

  // Configure MPU6050 gyro and accelerometer for bias calculation
  myIMU.writeByte(MPU9250_ADDRESS, CONFIG, 0x01);      // Set low-pass filter to 188 Hz
  myIMU.writeByte(MPU9250_ADDRESS, SMPLRT_DIV, 0x00);  // Set sample rate to 1 kHz
  myIMU.writeByte(MPU9250_ADDRESS, GYRO_CONFIG, 0x00);  // Set gyro full-scale to 250 degrees per second, maximum sensitivity
  myIMU.writeByte(MPU9250_ADDRESS, ACCEL_CONFIG, 0x00); // Set accelerometer full-scale to 2 g, maximum sensitivity

  uint16_t  gyrosensitivity  = 131;   // = 131 LSB/degrees/sec
  uint16_t  accelsensitivity = 16384;  // = 16384 LSB/g

  // Configure FIFO to capture accelerometer and gyro data for bias calculation
  myIMU.writeByte(MPU9250_ADDRESS, USER_CTRL, 0x40);   // Enable FIFO
  myIMU.writeByte(MPU9250_ADDRESS, FIFO_EN, 0x78);     // Enable gyro and accelerometer sensors for FIFO  (max size 512 bytes in MPU-9150)
  delay(40); // accumulate 40 samples in 40 milliseconds = 480 bytes

  // At end of sample accumulation, turn off FIFO sensor read
  myIMU.writeByte(MPU9250_ADDRESS, FIFO_EN, 0x00);        // Disable gyro and accelerometer sensors for FIFO
  myIMU.readBytes(MPU9250_ADDRESS, FIFO_COUNTH, 2, &data[0]); // read FIFO sample count
  fifo_count = ((uint16_t)data[0] << 8) | data[1];
  packet_count = fifo_count / 12; // How many sets of full gyro and accelerometer data for averaging

  for (ii = 0; ii < packet_count; ii++) {
    int16_t accel_temp[3] = {0, 0, 0}, gyro_temp[3] = {0, 0, 0};
    myIMU.readBytes(MPU9250_ADDRESS, FIFO_R_W, 12, &data[0]); // read data for averaging
    accel_temp[0] = (int16_t) (((int16_t)data[0] << 8) | data[1]  ) ;  // Form signed 16-bit integer for each sample in FIFO
    accel_temp[1] = (int16_t) (((int16_t)data[2] << 8) | data[3]  ) ;
    accel_temp[2] = (int16_t) (((int16_t)data[4] << 8) | data[5]  ) ;
    gyro_temp[0]  = (int16_t) (((int16_t)data[6] << 8) | data[7]  ) ;
    gyro_temp[1]  = (int16_t) (((int16_t)data[8] << 8) | data[9]  ) ;
    gyro_temp[2]  = (int16_t) (((int16_t)data[10] << 8) | data[11]) ;

    accel_bias[0] += (int32_t) accel_temp[0]; // Sum individual signed 16-bit biases to get accumulated signed 32-bit biases
    accel_bias[1] += (int32_t) accel_temp[1];
    accel_bias[2] += (int32_t) accel_temp[2];
    gyro_bias[0]  += (int32_t) gyro_temp[0];
    gyro_bias[1]  += (int32_t) gyro_temp[1];
    gyro_bias[2]  += (int32_t) gyro_temp[2];
  }

  accel_bias[0] /= (int32_t) packet_count; // Normalize sums to get average count biases
  accel_bias[1] /= (int32_t) packet_count;
  accel_bias[2] /= (int32_t) packet_count;
  gyro_bias[0]  /= (int32_t) packet_count;
  gyro_bias[1]  /= (int32_t) packet_count;
  gyro_bias[2]  /= (int32_t) packet_count;

  if (accel_bias[2] > 0L) {
    accel_bias[2] -= (int32_t) accelsensitivity;
  }  // Remove gravity from the z-axis accelerometer bias calculation
  else {
    accel_bias[2] += (int32_t) accelsensitivity;
  }

  // Construct the gyro biases for push to the hardware gyro bias registers, which are reset to zero upon device startup
  data[0] = (-gyro_bias[0] / 4  >> 8) & 0xFF; // Divide by 4 to get 32.9 LSB per deg/s to conform to expected bias input format
  data[1] = (-gyro_bias[0] / 4)       & 0xFF; // Biases are additive, so change sign on calculated average gyro biases
  data[2] = (-gyro_bias[1] / 4  >> 8) & 0xFF;
  data[3] = (-gyro_bias[1] / 4)       & 0xFF;
  data[4] = (-gyro_bias[2] / 4  >> 8) & 0xFF;
  data[5] = (-gyro_bias[2] / 4)       & 0xFF;

  // Push gyro biases to hardware registers
  myIMU.writeByte(MPU9250_ADDRESS, XG_OFFSET_H, data[0]);
  myIMU.writeByte(MPU9250_ADDRESS, XG_OFFSET_L, data[1]);
  myIMU.writeByte(MPU9250_ADDRESS, YG_OFFSET_H, data[2]);
  myIMU.writeByte(MPU9250_ADDRESS, YG_OFFSET_L, data[3]);
  myIMU.writeByte(MPU9250_ADDRESS, ZG_OFFSET_H, data[4]);
  myIMU.writeByte(MPU9250_ADDRESS, ZG_OFFSET_L, data[5]);

  // Output scaled gyro biases for display in the main program
  dest1[0] = (float) gyro_bias[0] / (float) gyrosensitivity;
  dest1[1] = (float) gyro_bias[1] / (float) gyrosensitivity;
  dest1[2] = (float) gyro_bias[2] / (float) gyrosensitivity;

  // Construct the accelerometer biases for push to the hardware accelerometer bias registers. These registers contain
  // factory trim values which must be added to the calculated accelerometer biases; on boot up these registers will hold
  // non-zero values. In addition, bit 0 of the lower byte must be preserved since it is used for temperature
  // compensation calculations. Accelerometer bias registers expect bias input as 2048 LSB per g, so that
  // the accelerometer biases calculated above must be divided by 8.

  int32_t accel_bias_reg[3] = {0, 0, 0}; // A place to hold the factory accelerometer trim biases
  myIMU.readBytes(MPU9250_ADDRESS, XA_OFFSET_H, 2, &data[0]); // Read factory accelerometer trim values
  accel_bias_reg[0] = (int32_t) (((int16_t)data[0] << 8) | data[1]);
  myIMU.readBytes(MPU9250_ADDRESS, YA_OFFSET_H, 2, &data[0]);
  accel_bias_reg[1] = (int32_t) (((int16_t)data[0] << 8) | data[1]);
  myIMU.readBytes(MPU9250_ADDRESS, ZA_OFFSET_H, 2, &data[0]);
  accel_bias_reg[2] = (int32_t) (((int16_t)data[0] << 8) | data[1]);

  uint32_t mask = 1uL; // Define mask for temperature compensation bit 0 of lower byte of accelerometer bias registers
  uint8_t mask_bit[3] = {0, 0, 0}; // Define array to hold mask bit for each accelerometer bias axis

  for (ii = 0; ii < 3; ii++) {
    if ((accel_bias_reg[ii] & mask)) mask_bit[ii] = 0x01; // If temperature compensation bit is set, record that fact in mask_bit
  }

  // Construct total accelerometer bias, including calculated average accelerometer bias from above
  accel_bias_reg[0] -= (accel_bias[0] / 8); // Subtract calculated averaged accelerometer bias scaled to 2048 LSB/g (16 g full scale)
  accel_bias_reg[1] -= (accel_bias[1] / 8);
  accel_bias_reg[2] -= (accel_bias[2] / 8);

  data[0] = (accel_bias_reg[0] >> 8) & 0xFF;
  data[1] = (accel_bias_reg[0])      & 0xFF;
  data[1] = data[1] | mask_bit[0]; // preserve temperature compensation bit when writing back to accelerometer bias registers
  data[2] = (accel_bias_reg[1] >> 8) & 0xFF;
  data[3] = (accel_bias_reg[1])      & 0xFF;
  data[3] = data[3] | mask_bit[1]; // preserve temperature compensation bit when writing back to accelerometer bias registers
  data[4] = (accel_bias_reg[2] >> 8) & 0xFF;
  data[5] = (accel_bias_reg[2])      & 0xFF;
  data[5] = data[5] | mask_bit[2]; // preserve temperature compensation bit when writing back to accelerometer bias registers

  // Apparently this is not working for the acceleration biases in the MPU-9250
  // Are we handling the temperature correction bit properly?
  // Push accelerometer biases to hardware registers
  /*  myIMU.writeByte(MPU9250_ADDRESS, XA_OFFSET_H, data[0]);
    myIMU.writeByte(MPU9250_ADDRESS, XA_OFFSET_L, data[1]);
    myIMU.writeByte(MPU9250_ADDRESS, YA_OFFSET_H, data[2]);
    myIMU.writeByte(MPU9250_ADDRESS, YA_OFFSET_L, data[3]);
    myIMU.writeByte(MPU9250_ADDRESS, ZA_OFFSET_H, data[4]);
    myIMU.writeByte(MPU9250_ADDRESS, ZA_OFFSET_L, data[5]);
  */
  // Output scaled accelerometer biases for display in the main program
  dest2[0] = (float)accel_bias[0] / (float)accelsensitivity;
  dest2[1] = (float)accel_bias[1] / (float)accelsensitivity;
  dest2[2] = (float)accel_bias[2] / (float)accelsensitivity;

  if (SerialDebug) {
    Serial.print("Accelerometer Bias Values: ");
    Serial.print(" X-Axis: "); Serial.print(dest2[0]);
    Serial.print(" Y-Axis: "); Serial.print(dest2[1]);
    Serial.print(" Z-Axis: "); Serial.println(dest2[2]);
  }
  Serial.println("DONE Calibrating MPU9250 (accel and gyro).");
}

