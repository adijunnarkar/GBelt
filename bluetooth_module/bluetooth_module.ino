#include <SoftwareSerial.h>//Software Serial Port
#define RxD 10
#define TxD 11
#define Reset 12
#define PIO11 8
#define Led 13

SoftwareSerial blueToothSerial(RxD,TxD);

void setup()
{
  Serial.begin(9600);
  pinMode(RxD, INPUT);
  pinMode(TxD, OUTPUT);
  pinMode(Led, OUTPUT);
  pinMode(PIO11, OUTPUT);
  digitalWrite(PIO11, HIGH);
  pinMode(Reset, OUTPUT);
  digitalWrite(Reset, LOW);
  setupBlueToothConnection();
  // digitalWrite(Led,HIGH); //Indicate Connection is Established
}
 
void loop()
{
  char recvChar;
  //check if there's any data sent from bluetooth master
  if(blueToothSerial.available()){
    recvChar = blueToothSerial.read();
    Serial.println(recvChar);
  }
}
 
void setupBlueToothConnection()
{
  enterATMode();
  sendATCommand();
  sendATCommand("NAME=2nd");
  sendATCommand("UART=57600,0,0");
  sendATCommand("ROLE=0"); // Label as Slave
  sendATCommand("PSWD=1234");
  sendATCommand("CMODE=1");
  enterComMode();
}
 
void resetBT()
{
  digitalWrite(Reset, LOW);
  delay(2000);
  digitalWrite(Reset, HIGH);
}
 
void enterComMode()
{
  blueToothSerial.flush();
  delay(500);
  digitalWrite(PIO11, LOW);
  resetBT();
  delay(500);
  blueToothSerial.begin(57600);
}
 
void enterATMode()
{
  blueToothSerial.flush();
  delay(500);
  digitalWrite(PIO11, HIGH);
  resetBT();
  delay(500);
  blueToothSerial.begin(38400);
}

void sendATCommand(char *command)
{
  blueToothSerial.print("AT");
  if(strlen(command) > 1){
    blueToothSerial.print("+");
    blueToothSerial.print(command);
    delay(100);
  }
  blueToothSerial.print("\r\n");
}
 
void sendATCommand()
{
  blueToothSerial.print("AT\r\n");
  delay(100);
}
