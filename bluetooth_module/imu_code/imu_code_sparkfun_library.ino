#include <Arduino.h>
#include <SoftwareSerial.h>
#include <SPI.h>

#include "MPU9250.h"
#include "math.h"

#define SerialDebug true  // Set to true to get Serial output for debugging
#define PWM true
#define avgCount 5 // set the number of samples to take to calculate an average
#define testingWithoutPhone false // certain small changes to fake bluetooth messages if no phone available
#define MAGCode "DC_main_NEW" // location code here for mag calibration (Manual, STC, Tung, Home_OLD, Home_NEW, Outside_NEW,)

SoftwareSerial BTSerial(8, 9); // RX | Tx (10, 11 for Arduino Mega)

// Pin definitions
int intPin = 7;  // These can be changed, 2 and 3 are the Arduinos ext int pins
int motorEast = 11; // mega is 3
int motorSouth = 6; // mega is 4
int motorWest = 5; // mega is 2
int motorNorth = 10; //mega is 5

int sample_counter = 0;
int accel_gyro_connect_counter = 0;
int magnetometer_connect_counter = 0;

// PWM related variables
int pwm_intensity_min = 70;
int pwm_intensity_max = 150;
int right_motor_intensity = 0;
int range = 70;

unsigned long motor_start_time, motor_deactive_start_time, motor_already_active_deactive_start_time = 0; // to activate motors for an interval of time
int motor_active_time = 1000; // length of time each motor will be active in [ms]
int motor_deactive_time = 750; // length of time motor will be deactive in [ms]
int motor_already_active_deactive_time = 250; // length of time motors will stay off if we are constantly changing between two directions that are next to each other

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

// initialize timers used
struct timer_flags {
    bool motor_deactivate_timer;
    bool motor_already_active_deactive_timer;
    bool timer_north_started;
    bool timer_northeast_started;
    bool timer_east_started;
    bool timer_southeast_started;
    bool timer_south_started;
    bool timer_southwest_started;
    bool timer_west_started;
    bool timer_northwest_started;
};

String direction = "";

byte c = 0x00, d = 0x00;

timer_flags timers;

MPU9250 myIMU;

void setup()
{
    delay(2000);
    Wire.begin();
    Serial.begin(38400);
    BTSerial.begin(9600);
    delay(1000);

    // Set up the interrupt pin, its set as active high, push-pull
    pinMode(intPin, INPUT);
    pinMode(motorNorth, OUTPUT);
    pinMode(motorSouth, OUTPUT);
    pinMode(motorWest, OUTPUT);
    pinMode(motorEast, OUTPUT);

    digitalWrite(intPin, LOW);
    digitalWrite(motorNorth, LOW);
    digitalWrite(motorSouth, LOW);
    digitalWrite(motorWest, LOW);
    digitalWrite(motorEast, LOW);

    // Read the WHO_AM_I register
    do {
        c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);

        #if SerialDebug
          Serial.println(c, HEX);
        #endif

        accel_gyro_connect_counter += 1;
        delay(500);
    } while (c != 0x71);

    #if SerialDebug
        Serial.print("MPU9250 "); Serial.print("I AM "); Serial.print(c, HEX);
        Serial.print(" I should be "); Serial.println(0x71, HEX); // WHO_AM_I should always be 0x71
        Serial.print("Tried connecting to MPU9250: "); Serial.print(accel_gyro_connect_counter); Serial.println(" time(s)");

        Serial.println("MPU9250 (accel and gyro) is online...");
        myIMU.initMPU9250();
        // Initialize device for active mode read of acclerometer, gyroscope, and
        // temperature
        Serial.println("MPU9250 initialized for active data mode....");
    #endif

    delay(500);

    accelgyrocalMPU9250(myIMU.gyroBias, myIMU.accelBias);
    delay(1000);

    myIMU.writeByte(MPU9250_ADDRESS, INT_PIN_CFG, 0x22);

    // Read the WHO_AM_I register of the magnetometer
    do {
    d = myIMU.readByte(AK8963_ADDRESS, WHO_AM_I_AK8963);
    Serial.println(d, HEX);
    magnetometer_connect_counter += 1;
    delay(500);
    } while (d != 0x48);

    #if SerialDebug
        Serial.print("WHO_AM_I_AK8963: "); Serial.println(WHO_AM_I_AK8963, HEX);

        Serial.print("AK8963 "); Serial.print("I AM "); Serial.print(d, HEX);
        Serial.print(" I should be "); Serial.println(0x48, HEX); // AK8963 WHO_AM_I should always be 0x48
        Serial.print("Tried connecting to Magnetometer: "); Serial.print(magnetometer_connect_counter); Serial.println(" time(s)");

        Serial.println("AK8963 (magnetometer) is online...");
    #endif

    myIMU.initAK8963(myIMU.magCalibration);
    calibrateMagnetometerBias(myIMU.magbias);

    #if SerialDebug
        Serial.println("AK8963 initialized for active data mode....");
    #endif

    turnAllMotorsOff();

    timers = { false, false, false, false, false, false, false, false, false, false }; // initialize timers struct

    #if SerialDebug
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
    #endif
    delay(100);
}

void loop()
{
    // If intPin goes high, all data registers have new data
    // On interrupt, check if data ready interrupt
    if (myIMU.readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01)
    {
        myIMU.readAccelData(myIMU.accelCount);  // Read the x/y/z adc values
        myIMU.getAres();

        myIMU.ax = (float)myIMU.accelCount[0] * myIMU.aRes - myIMU.accelBias[0];
        myIMU.ay = (float)myIMU.accelCount[1] * myIMU.aRes - myIMU.accelBias[1];
        myIMU.az = (float)myIMU.accelCount[2] * myIMU.aRes - myIMU.accelBias[2];

        // save accelData for averaging
        accelData[sample_counter][0] = myIMU.ax;
        accelData[sample_counter][1] = myIMU.ay;
        accelData[sample_counter][2] = myIMU.az;

        myIMU.readGyroData(myIMU.gyroCount);  // Read the x/y/z adc values
        myIMU.getGres();
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
    }

    if (averageCalculated)
    {
        /* PITCH: tilting the body from side to side, ROLL: tilting the body forwards and backwards*/
        // March 15, 2017 - pitch, roll and yaw formulas for final prototype design orientation
        pitch = atan2(-accelAverage[0], sqrt(accelAverage[2] * accelAverage[2] + accelAverage[1] * accelAverage[1])) * RAD_TO_DEG;
        roll = atan2(-accelAverage[2], accelAverage[1]) * RAD_TO_DEG;
        //if (!(inRange(int(roll), -15, 15))) // if roll gives us problematic data, then hardcode pitch, roll
        //{
          //pitch = 0;
          //roll = 180;
        //}
        pitch = 0;
        roll = 0;

        Xh = magAverage[2] * cos(pitch * DEG_TO_RAD) + magAverage[1] * sin(roll * DEG_TO_RAD) * sin(pitch * DEG_TO_RAD) - magAverage[0] * cos(roll * DEG_TO_RAD) * sin(pitch * DEG_TO_RAD);
        Yh = magAverage[1] * cos(roll * DEG_TO_RAD) + magAverage[0] * sin(roll * DEG_TO_RAD);
        yaw = atan2(-Yh, Xh) * RAD_TO_DEG - 9.65;
        yaw = ((360 + (int)yaw) % 360);

        //Serial.println("Yaw: " + String(yaw) + "; Pitch: " + String(pitch) + "; Roll: " + String(roll));

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
            if (direction == "Stop")
            {
                turnAllMotorsOff();
                Serial.println("STOP");
                receiving_bluetooth = false;
                while(Serial.available()) {  //is there anything to read?
                    char getData = Serial.read();  //if yes, read it
                }  // don't do anything with it.
            }
            else if (direction == "Finish")
            {
                playStopVibrationSequence();
                //Serial.println("FINISH");
                receiving_bluetooth = false;
                while(Serial.available()){  //is there anything to read?
                    char getData = Serial.read();  //if yes, read it
                }  // don't do anything with it.
            }
            else
            {
                thetaDesired = direction.toInt();
                //Serial.println("Theta Received: " + String(thetaDesired));
            }
            newDirectionReady = false;
            direction = "";
        }

        #if testingWithoutPhone
            thetaDesired = 0;
            //thetaDesired = 360 - thetaDesired;
            receiving_bluetooth = true;
        #endif

        if (receiving_bluetooth)
        {
            // calculate theta to decide which motors to activate only if IMU reading average has been calculated
            if (yaw > thetaDesired)
            {
                theta = int(360 - (yaw - thetaDesired));
            }
            else
            {
                theta = int(thetaDesired - yaw);
            }

            #if testingWithoutPhone
                Serial.println("Yaw: " + String(yaw));
                Serial.println("Theta_Received: " + String(thetaDesired));
                Serial.println("Theta: " + String(theta));
            #endif

            if (inRange(theta, 350, 360) || inRange(theta, 0, 10)) // North
            {
                if (timers.timer_northeast_started || timers.timer_northwest_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer && !timers.motor_already_active_deactive_timer)
                {
                    if (!timers.timer_north_started)
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        analogWrite(motorNorth, pwm_intensity_max+100);
                        timers.timer_north_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.println("North Motors Active");
                    #endif
                }
            }
            else if (inRange(theta, 10, 80)) // Northeast
            {
                if (timers.timer_north_started || timers.timer_east_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer && !timers.motor_already_active_deactive_timer)
                {
                    if (!(timers.timer_northeast_started))
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        right_motor_intensity = map(theta - 5, 0, range, pwm_intensity_min, pwm_intensity_max);
                        analogWrite(motorNorth, pwm_intensity_max - (right_motor_intensity - pwm_intensity_min));
                        analogWrite(motorEast, right_motor_intensity);
                        timers.timer_northeast_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.print("North and East Motors Active: ");
                        Serial.println(right_motor_intensity);
                    #endif
                }
            }
            else if (inRange(theta, 80, 100)) // East
            {
                if (timers.timer_northeast_started || timers.timer_southeast_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer && !timers.motor_already_active_deactive_timer)
                {
                    if (!(timers.timer_east_started))
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        analogWrite(motorEast, pwm_intensity_max);
                        timers.timer_east_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.println("East Motors Active");
                    #endif
                }
            }
            else if (inRange(theta, 100, 170)) // Southeast
            {
                if (timers.timer_south_started || timers.timer_east_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer && !timers.motor_already_active_deactive_timer)
                {
                    if (!(timers.timer_southeast_started))
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        right_motor_intensity = map(theta - 95, 0, range, pwm_intensity_min, pwm_intensity_max);
                        analogWrite(motorSouth, right_motor_intensity);
                        analogWrite(motorEast, pwm_intensity_max - (right_motor_intensity - pwm_intensity_min));
                        timers.timer_southeast_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.print("South and East Motors Active: ");
                        Serial.println(right_motor_intensity);
                    #endif
                }
            }
            else if (inRange(theta, 170, 190)) // South
            {
                if (timers.timer_southeast_started || timers.timer_southwest_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer && !timers.motor_already_active_deactive_timer)
                {
                    if (!(timers.timer_south_started))
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        analogWrite(motorSouth, pwm_intensity_max+100);
                        timers.timer_south_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.println("South Motors Active");
                    #endif
                }
            }
            else if (inRange(theta, 190, 260)) // Southwest
            {
                if (timers.timer_south_started || timers.timer_west_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer)
                {
                    if (!(timers.timer_southwest_started))
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        right_motor_intensity = map(theta - 185, 0, range, pwm_intensity_min, pwm_intensity_max);
                        analogWrite(motorSouth, pwm_intensity_max - (right_motor_intensity - pwm_intensity_min));
                        analogWrite(motorWest, right_motor_intensity);
                        timers.timer_southwest_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.print("South and West Motors Active: ");
                        Serial.println(right_motor_intensity);
                    #endif
                }
            }
            else if (inRange(theta, 260, 280)) // West
            {
                if (timers.timer_northwest_started || timers.timer_southwest_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer && !timers.motor_already_active_deactive_timer)
                {
                    if (!(timers.timer_west_started))
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        analogWrite(motorWest, pwm_intensity_max);
                        timers.timer_west_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.println("West Motors Active");
                    #endif
                }
            }
            else if (inRange(theta, 280, 350)) // Northwest
            {
                if (timers.timer_north_started || timers.timer_west_started)
                {
                    turnAllTimersOff();
                    turnAllMotorsOff();
                    timers.motor_already_active_deactive_timer = true;
                    motor_already_active_deactive_start_time = millis();
                }
                else if (!timers.motor_deactivate_timer && !timers.motor_already_active_deactive_timer)
                {
                    if (!(timers.timer_northwest_started))
                    {
                        turnAllTimersOff();
                        turnAllMotorsOff();
                        right_motor_intensity = map(theta - 275, 0, range, pwm_intensity_min, pwm_intensity_max);
                        analogWrite(motorNorth, right_motor_intensity);
                        analogWrite(motorWest, pwm_intensity_max - (right_motor_intensity - pwm_intensity_min));
                        timers.timer_northwest_started = true;
                        motor_start_time = millis();
                    }
                    #if SerialDebug
                        Serial.print("North and West Motors Active: ");
                        Serial.println(right_motor_intensity);
                    #endif
                }
            }
            averageCalculated = false;
        }

        // turn off motors if the required ON duration has been completed
        if(!(timers.motor_deactivate_timer) && motor_function_duration_complete(motor_start_time, motor_active_time))
        {
            turnAllMotorsOff();
            turnAllTimersOff();
            timers.motor_deactivate_timer = true;
            motor_deactive_start_time = millis();
            #if SerialDebug
                Serial.println("------------------MOTORS OFF-----------------------------");
            #endif
        }

        if (timers.motor_deactivate_timer && motor_function_duration_complete(motor_deactive_start_time, motor_deactive_time))
        {
            timers.motor_deactivate_timer = false;
            #if SerialDebug
                Serial.println("------------------MOTORS DEACTIVATED---------------------");
            #endif
        }

        if (timers.motor_already_active_deactive_timer && motor_function_duration_complete(motor_already_active_deactive_start_time, motor_already_active_deactive_time))
        {
            timers.motor_already_active_deactive_timer = false;
            #if SerialDebug
                Serial.println("------------------MOTORS DEACTIVATED FOR SHORTER TIME---------------------");
            #endif
        }
    }

    #if SerialDebug
        Serial.print("Sample Count: "); Serial.println(sample_counter);

        // Print acceleration values in milligs!
        Serial.print("X-accel: "); Serial.print(1000 * accelAverage[0]);
        Serial.print(" mg ");
        Serial.print("Y-accel: "); Serial.print(1000 * accelAverage[1]);
        Serial.print(" mg ");
        Serial.print("Z-accel: "); Serial.print(1000 * accelAverage[2]);
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
    #endif
    delay(10);
}


void turnAllMotorsOff()
{
    analogWrite(motorNorth, 0);
    analogWrite(motorSouth, 0);
    analogWrite(motorEast, 0);
    analogWrite(motorWest, 0);
}


void turnAllTimersOff()
{
    timers.timer_north_started = false;
    timers.timer_northeast_started = false;
    timers.timer_east_started = false;
    timers.timer_southeast_started = false;
    timers.timer_south_started = false;
    timers.timer_southwest_started = false;
    timers.timer_west_started = false;
    timers.timer_northwest_started = false;
}


bool inRange(int val, int min, int max)
{
  return ((min <= val) && (val <= max));
}


bool motor_function_duration_complete(unsigned long &since, unsigned long time) {
    // return false if we're still "delaying", true if time ms has passed.
    unsigned long currentmillis = millis();
    if (currentmillis - since >= time) {
        since = currentmillis;
        return true;
    }
    return false;
}


void playStopVibrationSequence()
{
    turnAllMotorsOff();
    delay(100);

    analogWrite(motorNorth, pwm_intensity_max);
    delay(500);
    analogWrite(motorNorth, pwm_intensity_min);

    analogWrite(motorEast, pwm_intensity_max);
    delay(500);
    analogWrite(motorEast, pwm_intensity_min);

    analogWrite(motorSouth, pwm_intensity_max);
    delay(500);
    analogWrite(motorSouth, pwm_intensity_min);

    analogWrite(motorWest, pwm_intensity_max);
    delay(500);
    analogWrite(motorWest, pwm_intensity_min);

    analogWrite(motorNorth, pwm_intensity_max);
    delay(500);
    analogWrite(motorNorth, pwm_intensity_min);

    turnAllMotorsOff();
}


void calibrateMagnetometerBias(float * dest1)
{
    /*
    Calibrate magnetometer by waving device in a variety of figure eight patters.

    The 'magBias' (x, y, and z) values are needed to ensure that the appropriate offset
    is used when calculating the actual magnetometer values.

    Currently only implementing the hard iron correction for magnetometer.
    */

    int32_t mag_bias[3] = {0, 0, 0}, mag_scale[3] = {0, 0, 0};
    int16_t mag_max[3] = {-32767, -32767, -32767}, mag_min[3] = {32767, 32767, 32767}, mag_temp[3] = {0, 0, 0};

    if (MAGCode == "Manual")
    {
        uint16_t ii = 0, sample_count = 0;

        Serial.println("Mag Calibration: Wave device in a figure eight until done!");
        delay(2000);

        //shoot for ~30 seconds of mag data
        if(Mmode == 0x02) sample_count = 240;  // at 8 Hz ODR, new mag data is available every 125 ms
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
        Serial.println("You can stop waving now! Thanks!!! :)");
    }
    else if (MAGCode == "STC")
    {
        mag_max[0] = 400; mag_min[0] = -65;
        mag_max[1] = 486;  mag_min[1] = -160;
        mag_max[2] = 140;  mag_min[2] = -651;
    }
    else if (MAGCode == "Tiff")
    {
        mag_max[0] = 469; mag_min[0] = -141;
        mag_max[1] = 533;  mag_min[1] = -112;
        mag_max[2] = -20;  mag_min[2] = -697;
    }
    else if (MAGCode == "Tung")
    {
        mag_max[0] = 496; mag_min[0] = -167;
        mag_max[1] = 530;  mag_min[1] = -135;
        mag_max[2] = 1;  mag_min[2] = -676;
    }
    else if (MAGCode == "Home_OLD")
    {
        mag_max[0] = 440; mag_min[0] = -121;
        mag_max[1] = 500;  mag_min[1] = -55;
        mag_max[2] = -64;  mag_min[2] = -634;
    }
    else if (MAGCode == "Home_NEW")
    {
        mag_max[0] = 317; mag_min[0] = -227;
        mag_max[1] = 415;  mag_min[1] = -128;
        mag_max[2] = 190;  mag_min[2] = -390;
    }
    else if (MAGCode == "Outside_NEW")
    {
        mag_max[0] = 358; mag_min[0] = -224;
        mag_max[1] = 435;  mag_min[1] = -144;
        mag_max[2] = 250;  mag_min[2] = -370;
    }
    else if (MAGCode == "Needles")
    {
        mag_max[0] = 307; mag_min[0] = -146;
        mag_max[1] = 365;  mag_min[1] = -85;
        mag_max[2] = 159;  mag_min[2] = -311;
    }
    else if (MAGCode == "DC_upstairs_NEW")
    {
        mag_max[0] = 357; mag_min[0] = -214;
        mag_max[1] = 430;  mag_min[1] = -136;
        mag_max[2] = 218;  mag_min[2] = -392;
    }
    else if (MAGCode == "DC_main_NEW")
    {
        mag_max[0] = 330; mag_min[0] = -174;
        mag_max[1] = 401;  mag_min[1] = -107;
        mag_max[2] = 179;  mag_min[2] = -360;
    }

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

    #if SerialDebug
        Serial.println("mag x min/max:"); Serial.println(mag_max[0]); Serial.println(mag_min[0]);
        Serial.println("mag y min/max:"); Serial.println(mag_max[1]); Serial.println(mag_min[1]);
        Serial.println("mag z min/max:"); Serial.println(mag_max[2]); Serial.println(mag_min[2]);
        Serial.println("Mag Calibration done!");
        delay(5000);
    #endif
}

// Function which accumulates gyro and accelerometer data after device initialization. It calculates the average
// of the at-rest readings and then loads the resulting offsets into accelerometer and gyro bias registers.
void accelgyrocalMPU9250(float * dest1, float * dest2)
{
    #if SerialDebug
        Serial.println("Calibrating MPU9250 (accel and gyro)...");
    #endif
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

    #if SerialDebug
        Serial.print("Accelerometer Bias Values: ");
        Serial.print(" X-Axis: "); Serial.print(dest2[0]);
        Serial.print(" Y-Axis: "); Serial.print(dest2[1]);
        Serial.print(" Z-Axis: "); Serial.println(dest2[2]);
        Serial.println("DONE Calibrating MPU9250 (accel and gyro).");
    #endif
}
