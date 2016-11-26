#include <MPU9250.h>
#include <quaternionFilters.h>

#include <Arduino.h>

#include <MPU9250.h>
#include <quaternionFilters.h>
#include <Wire.h>

#define MPU9250_ADDRESS 0x68

// WHO_AM_I register addresses
#define WHO_AM_I_MPU9250 0x75
#define WHO_AM_I_MAG     0x00 // should return 0x48

//Magnetometer Registers
#define MAG_ADDRESS   0x0C
#define INFO          0x01
#define MAG_ST1       0x02  // data ready status bit 0
#define MAG_XOUT_L    0x03  // data
#define MAG_XOUT_H    0x04
#define MAG_YOUT_L    0x05
#define MAG_YOUT_H    0x06
#define MAG_ZOUT_L    0x07
#define MAG_ZOUT_H    0x08
#define MAG_ST2       0x09  // Data overflow bit 3 and data read error status bit 2
#define MAG_CNTL      0x0A  // Power down (0000), single-measurement (0001), self-test (1000) and Fuse ROM (1111) modes on bits 3:0
#define MAG_ASTC      0x0C  // Self test control
#define MAG_I2CDIS    0x0F  // I2C disable
#define MAG_ASAX      0x10  // Fuse ROM x-axis sensitivity adjustment value
#define MAG_ASAY      0x11  // Fuse ROM y-axis sensitivity adjustment value
#define MAG_ASAZ      0x12  // Fuse ROM z-axis sensitivity adjustment value

//Accelerometer and Gyroscope Registers
#define CONFIG                   0x1A
#define GYRO_CONFIG              0x1B
#define ACCEL_CONFIG             0x1C
#define ACCEL_CONFIG2            0x1D

#define PWR_MGMT_1               0x6B
#define PWR_MGMT_2               0x6C

#define GYRO_FULL_SCALE_250_DPS  0x00
#define GYRO_FULL_SCALE_500_DPS  0x08
#define GYRO_FULL_SCALE_1000_DPS 0x10
#define GYRO_FULL_SCALE_2000_DPS 0x18

#define ACC_FULL_SCALE_2_G       0x00
#define ACC_FULL_SCALE_4_G       0x08
#define ACC_FULL_SCALE_8_G       0x10
#define ACC_FULL_SCALE_16_G      0x18


// Set initial input parameters
enum Ascale {
    AFS_2G = 0,
    AFS_4G,
    AFS_8G,
    AFS_16G
};

enum Gscale {
    GFS_250DPS = 0,
    GFS_500DPS,
    GFS_1000DPS,
    GFS_2000DPS
};

enum Mscale {
    MFS_14BITS = 0, // 0.6 mG per LSB
    MFS_16BITS      // 0.15 mG per LSB
};

// Specify sensor full scale
uint8_t Gscale = GFS_250DPS;
uint8_t Ascale = AFS_2G;
uint8_t Mscale = MFS_16BITS; // Choose either 14-bit or 16-bit magnetometer resolution
uint8_t Mmode = 0x02;        // 2 for 8 Hz, 6 for 100 Hz continuous magnetometer data read
float aRes, gRes, mRes;      // scale resolutions per LSB for the sensors

float ax, ay, az, gx, gy, gz, mx, my, mz; // variables to hold latest sensor data values 

long int cpt=0; //loop counter

// Read a single byte of data from I2C device at address Address
// Put read byte in register Register and return data read
uint8_t I2CreadByte(uint8_t Address, uint8_t Register)
{
    uint8_t data; // `data` will store the register data     
    Wire.beginTransmission(Address);         // Initialize the Tx buffer
    Wire.write(Register);                  // Put slave register address in Tx buffer
    Wire.endTransmission(false);             // Send the Tx buffer, but send a restart to keep connection alive
    Wire.requestFrom(Address, (uint8_t) 1);  // Read one byte from slave register address 
    data = Wire.read();                      // Fill Rx buffer with result
    return data;                             // Return data read from slave register
}
// This function read Nbytes bytes from I2C device at address Address. 
// Put read bytes starting at register Register in the Data array. 
void I2CreadBytes(uint8_t Address, uint8_t Register, uint8_t Nbytes, uint8_t* Data)
{
    // Set register address
    Wire.beginTransmission(Address);
    Wire.write(Register);
    Wire.endTransmission(false);
 
    // Read Nbytes
    Wire.requestFrom(Address, Nbytes); 
    uint8_t index=0;
    while (Wire.available())
        Data[index++]=Wire.read();
}

// Write a byte (Data) in device (Address) at register (Register)
void I2CwriteByte(uint8_t Address, uint8_t Register, uint8_t Data)
{
    // Set register address
    Wire.beginTransmission(Address);
    Wire.write(Register);
    Wire.write(Data);
    Wire.endTransmission();
}

void initMPU9250()
{
    // wake up device
    I2CwriteByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x00); // Clear sleep mode bit (6), enable all sensors 
    delay(100); // Wait for all registers to reset

    // get stable time source
    I2CwriteByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x01); // Auto select clock source to be PLL gyroscope reference if ready else
    delay(200); 

    // Configure Gyro 
    I2CwriteByte(MPU9250_ADDRESS, CONFIG, 0x03);

    // Set gyroscope full scale range
    // Range selects FS_SEL and AFS_SEL are 0 - 3, so 2-bit values are left-shifted into positions 4:3
    uint8_t c = I2CreadByte(MPU9250_ADDRESS, GYRO_CONFIG); // get current GYRO_CONFIG register value
    // c = c & ~0xE0; // Clear self-test bits [7:5] 
    c = c & ~0x02; // Clear Fchoice bits [1:0] 
    c = c & ~0x18; // Clear AFS bits [4:3]
    c = c | Gscale << 3; // Set full scale range for the gyro
    // c =| 0x00; // Set Fchoice for the gyro to 11 by writing its inverse to bits 1:0 of GYRO_CONFIG
    I2CwriteByte(MPU9250_ADDRESS, GYRO_CONFIG, c); // Write new GYRO_CONFIG value to register


    // Set accelerometer full-scale range configuration
    c = I2CreadByte(MPU9250_ADDRESS, ACCEL_CONFIG); // get current ACCEL_CONFIG register value
    // c = c & ~0xE0; // Clear self-test bits [7:5] 
    c = c & ~0x18;  // Clear AFS bits [4:3]
    c = c | Ascale << 3; // Set full scale range for the accelerometer 
    I2CwriteByte(MPU9250_ADDRESS, ACCEL_CONFIG, c); // Write new ACCEL_CONFIG register value

    delay(100);
}


// // Initializations
// void setup()
// {
//     delay(5000);
//     // Arduino initializations
//     Wire.begin();
//     Serial.begin(38400);
    
//     initMPU9250();

//     // Read the WHO_AM_I register, this is a good test of communication
//     byte c = I2CreadByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);  // Read WHO_AM_I register for MPU-9250
//     Serial.print("MPU9250 "); Serial.print("I AM "); Serial.print(c, HEX); Serial.print(" I should be "); Serial.println(0x71, HEX);   
//     // // Configure gyroscope range
//     // I2CwriteByte(MPU9250_ADDRESS,27,GYRO_FULL_SCALE_2000_DPS);
//     // // Configure accelerometers range
//     // I2CwriteByte(MPU9250_ADDRESS,28,ACC_FULL_SCALE_16_G);
//     // // Set by pass mode for the magnetometers
//     // I2CwriteByte(MPU9250_ADDRESS,0x37,0x02);
 
//     // // Request first magnetometer single measurement
//     // I2CwriteByte(MAG_ADDRESS,0x0A,0x01);
//     delay(1000);

//     if (c == 0x71) // WHO_AM_I_MPU9250 should always be return 0x71
//     {
//         Serial.println("MPU9250 is online...");

//         initMPU9250();
//         Serial.println("MPU9250 initialized for active data mode...."); // Initialize device for active mode read of accelerometer, gyroscope, magnetometer

//         // Read the WHO_AM_I register of the magnetometer, this is a good test of communication
//         byte d = I2CreadByte(MAG_ADDRESS, WHO_AM_I_MAG);  // Read WHO_AM_I register for MAG
//         Serial.print("MAG "); Serial.print("I AM "); Serial.print(d, HEX); Serial.print(" I should be "); Serial.println(0x48, HEX);
//     }
//     else
//     {
//         Serial.print("Could not connect to MPU9250: 0x");
//         Serial.println(c, HEX);
//         while(1); // Loop forever if communication doesn't happen
//     }
// }


// // Main loop, read and display data
// void loop()
// {
 
//     // _______________
//     // ::: Counter :::
 
//     // Display data counter
//     Serial.print (cpt++,DEC);
//     Serial.print ("\t");
 
 
 
//     // ____________________________________
//     // :::    accelerometer and gyroscope ::: 
 
//     // Read accelerometer and gyroscope
//     uint8_t Buf[14];
//     I2CreadBytes(MPU9250_ADDRESS,0x3B,14,Buf);
 
 
//     // Create 16 bits values from 8 bits data
 
//     // Accelerometer
//     int16_t ax=-(Buf[0]<<8 | Buf[1]);
//     int16_t ay=-(Buf[2]<<8 | Buf[3]);
//     int16_t az=(Buf[4]<<8 | Buf[5]);
 
//     // Gyroscope
//     int16_t gx=-(Buf[8]<<8 | Buf[9]);
//     int16_t gy=-(Buf[10]<<8 | Buf[11]);
//     int16_t gz=Buf[12]<<8 | Buf[13];
 
//         // Display values

//     // Accelerometer
//     Serial.print (ax,DEC); 
//     Serial.print ("\t");
//     Serial.print (ay,DEC);
//     Serial.print ("\t");
//     Serial.print (az,DEC);    
//     Serial.print ("\t");

//     // Gyroscope
//     Serial.print (gx,DEC); 
//     Serial.print ("\t");
//     Serial.print (gy,DEC);
//     Serial.print ("\t");
//     Serial.print (gz,DEC);    
//     Serial.print ("\t");
 
//     // _____________________
//     // :::    Magnetometer ::: 
 
 
//     // Read register Status 1 and wait for the DRDY: Data Ready

//     uint8_t ST1;
//     //do
//     //{
//     I2CreadBytes(MAG_ADDRESS,0x02,1,&ST1);
//     //}
//     //while (!(ST1&0x01));
 
//     // Read magnetometer data    
//     uint8_t Mag[7];    
//     I2CreadBytes(MAG_ADDRESS,0x03,7,Mag);
 
 
//     // Create 16 bits values from 8 bits data
 
//     // Magnetometer
//     int16_t mx=-(Mag[3]<<8 | Mag[2]);
//     int16_t my=-(Mag[1]<<8 | Mag[0]);
//     int16_t mz=-(Mag[5]<<8 | Mag[4]);
 
 
//     // Magnetometer
//     Serial.print (mx+200,DEC); 
//     Serial.print ("\t");
//     Serial.print (my-70,DEC);
//     Serial.print ("\t");
//     Serial.print (mz-700,DEC);    
//     Serial.print ("\t");

//     // End of line
//     Serial.println("");
//     delay(100);        
// }

//===================================================================================================================
//====== Set of useful function to access acceleration. gyroscope, magnetometer, and temperature data
//===================================================================================================================

void getMres() {
  switch (Mscale)
  {
    // Possible magnetometer scales (and their register bit settings) are:
    // 14 bit resolution (0) and 16 bit resolution (1)
    case MFS_14BITS:
          mRes = 10.*4912./8190.; // Proper scale to return milliGauss
          break;
    case MFS_16BITS:
          mRes = 10.*4912./32760.0; // Proper scale to return milliGauss
          break;
  }
}

void getGres() {
  switch (Gscale)
  {
    // Possible gyro scales (and their register bit settings) are:
    // 250 DPS (00), 500 DPS (01), 1000 DPS (10), and 2000 DPS  (11). 
        // Here's a bit of an algorith to calculate DPS/(ADC tick) based on that 2-bit value:
    case GFS_250DPS:
          gRes = 250.0/32768.0;
          break;
    case GFS_500DPS:
          gRes = 500.0/32768.0;
          break;
    case GFS_1000DPS:
          gRes = 1000.0/32768.0;
          break;
    case GFS_2000DPS:
          gRes = 2000.0/32768.0;
          break;
  }
}

void getAres() {
  switch (Ascale)
  {
    // Possible accelerometer scales (and their register bit settings) are:
    // 2 Gs (00), 4 Gs (01), 8 Gs (10), and 16 Gs  (11). 
        // Here's a bit of an algorith to calculate DPS/(ADC tick) based on that 2-bit value:
    case AFS_2G:
          aRes = 2.0/32768.0;
          break;
    case AFS_4G:
          aRes = 4.0/32768.0;
          break;
    case AFS_8G:
          aRes = 8.0/32768.0;
          break;
    case AFS_16G:
          aRes = 16.0/32768.0;
          break;
  }
}

void I2Cscan()
{
    // scan for i2c devices
    byte error, address;
    int nDevices;

    Serial.println("Scanning...");

    nDevices = 0;
    for(address = 1; address < 127; address++) 
    {
        // The i2c_scanner uses the return value of
        // the Write.endTransmisstion to see if
        // a device did acknowledge to the address.
        Wire.beginTransmission(address);
        error = Wire.endTransmission();

        if (error == 0)
        {
            Serial.print("I2C device found at address 0x");
            if (address<16) 
                Serial.print("0");
        
            Serial.print(address, HEX);
            Serial.println("  !");

            nDevices++;
        }
        else if (error==4) 
        {
            Serial.print("Unknow error at address 0x");
            if (address<16) 
                Serial.print("0");
            Serial.println(address, HEX);
            }    
        }
        if (nDevices == 0)
            Serial.println("No I2C devices found\n");
        else
            Serial.println("done\n");
}
