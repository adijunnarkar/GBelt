int led = 13;
//Char used for reading in Serial characters
char inbyte = 0;
//*******************************************************************************************

void setup() {
  // initialise serial communications at 9600 bps:
  Serial.begin(9600);
  pinMode(led, OUTPUT);
  digitalWrite(led, HIGH);
}

void loop() {
  if (Serial.available() > 0)
  {
    inbyte = Serial.read();
    if (inbyte == '0')
    {
      //LED off
      digitalWrite(led, LOW);
    }
    if (inbyte == '1')
    {
      //LED on
      digitalWrite(led, HIGH);
    }
  }
  //delay by 2s. Meaning we will be sent values every 2s approx
  //also means that it can take up to 2 seconds to change LED state
  delay(2000);
}
