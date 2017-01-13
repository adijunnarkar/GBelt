#include <Arduino.h>
#include <SoftwareSerial.h>

///////////////////////////
// CONSTANTS DECLARATION //
///////////////////////////
SoftwareSerial BTSerial(10,11); // RX | Tx

int ledNorth = 5, ledEast = 4, ledSouth = 2, ledWest = 3;
int theta;

int lightUp[2];

//Char used for reading in Serial characters
char inByte = 0;

bool startReadingData = false;
bool newDirectionReady = false; // indicates when a new direction from phone is available

String ledToLightUp = "";
String direction = "";


void setup() {
    Serial.begin(9600);
    BTSerial.begin(9600);
    pinMode(ledNorth, OUTPUT);
    pinMode(ledEast, OUTPUT);
    pinMode(ledSouth, OUTPUT);
    pinMode(ledWest, OUTPUT);
    digitalWrite(ledNorth, LOW);
    digitalWrite(ledSouth, LOW);
    digitalWrite(ledWest, LOW);
    digitalWrite(ledEast, LOW);
}

void loop() {
    if (BTSerial.available() > 0)
    {
        inByte = BTSerial.read();
        //Serial.println(inByte);
        if (inByte == '#')
        {
            startReadingData = true;
        }
        if (inByte == '~')
        {
            Serial.println(direction);
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
        theta = direction.toInt();
        digitalWrite(ledNorth, LOW);
        digitalWrite(ledSouth, LOW);
        digitalWrite(ledWest, LOW);
        digitalWrite(ledEast, LOW);
        if (inRange(theta, 350, 360) || inRange(theta, 0, 10)) // North
            digitalWrite(ledNorth, HIGH);
        else if (inRange(theta, 10, 80)) // Northeast
        {
            digitalWrite(ledNorth, HIGH);
            digitalWrite(ledEast, HIGH);
        }
        else if (inRange(theta, 80, 100)) // East
            digitalWrite(ledEast, HIGH);
        else if (inRange(theta, 100, 170)) // Southeast
        {
            digitalWrite(ledSouth, HIGH);
            digitalWrite(ledEast, HIGH);
        }
        else if (inRange(theta, 170, 190)) // South
            digitalWrite(ledSouth, HIGH);
        else if (inRange(theta, 190, 260)) // Southwest
        {
            digitalWrite(ledSouth, HIGH);
            digitalWrite(ledWest, HIGH);
        }
        else if (inRange(theta, 260, 280)) // West
            digitalWrite(ledWest, HIGH);
        else if (inRange(theta, 280, 350)) // Northwest
        {
            digitalWrite(ledNorth, HIGH);
            digitalWrite(ledWest, HIGH);
        }
        newDirectionReady = false; 
        direction = "";
    }
}

bool inRange(int val, int min, int max)
{
    return ((min <= val) && (val <= max));
}
