#include <Arduino.h>


///////////////////////////
// CONSTANTS DECLARATION //
///////////////////////////

int ledNorth = 5, ledEast = 4, ledSouth = 2, ledWest = 3;
int secondLEDIndex, secondLEDNumber, i;

int lightUp[2]

//Char used for reading in Serial characters
char inByte = 0;

bool startReadingData = false;
bool newDirectionReady = false; // indicates when a new direction from phone is available

String ledToLightUp = "";
String direction = "";


void setup() {
    Serial.begin(9600);
    pinMode(ledNorth, OUTPUT);
    pinMode(ledEast, OUTPUT);
    pinMode(ledSouth, OUTPUT);
    pinMode(ledWest, OUTPUT);
}

void loop() {
    if (Serial.available() > 0)
    {
        inbyte = Serial.read();
        if (inbyte == '#')
        {
            start_reading_data = true;
            //LED off
            //digitalWrite(led, LOW);
        }
        if (inbyte == '~')
        {
            Serial.println(direction);
            start_reading_data = false;
            new_direction_ready = true;
        }
        if ((start_reading_data) && (inbyte != "#") && (inbyte != "~"))
        {
            direction += inbyte;
        }
    }
    if (new_direction_ready)
    {
        switch (theta) {
            case (inRange(theta, 350, 360) || inRange(theta, 0, 10)): // North
                digitalWrite(ledNorth, HIGH);
                break;
            case inRange(theta, 10, 80): // Northeast
                digitalWrite(ledNorth, HIGH);
                digitalWrite(ledEast, HIGH);
                break;
            case inRange(theta, 80, 100): // East
                digitalWrite(ledEast, HIGH);
                break;
            case inRange(theta, 100, 170): // Southeast
                digitalWrite(ledSouth, HIGH);
                digitalWrite(ledEast, HIGH);
                break;
            case inRange(theta, 170, 190): // South
                digitalWrite(ledSouth, HIGH);
                break;
            case inRange(theta, 190, 260): // Southwest
                digitalWrite(ledSouth, HIGH);
                digitalWrite(ledWest, HIGH);
                break;
            case inRange(theta, 260, 280): // West
                digitalWrite(ledWest, HIGH);
                break;
            case inRange(theta, 280, 350): // Northwest
                digitalWrite(ledNorth, HIGH);
                digitalWrite(ledWest, HIGH);
        }
        new_direction_ready = false; 
        direction = "";
    }
}

bool inRange(int val, int min, int max)
{
    return ((minimum <= val) && (val <= maximum));
}