// Arduino Firmware for openC4D
// version 4.00

#include "Arduino.h"
#include <Serine.h>

// Virtual devices living in this microcontroller
SerineVirtualDevice detectorVD = { 'd', "SdL013003" };

// Definitions for the microcontroller
const byte PIN_START = 2;  // This is the digital port that corresponds to the INT above.
const byte PORT_STOP = 3;  // This is the digital port that corresponds to the INT above.
const byte SCK_AD = 7;     // Serial Clock for the ADC MCP3551
const byte CS_AD = 13;     // Chip Select for the ADC MCP3551
// Digital pins for Serial Digital Output (SDO) of the ADCs
const byte SDO_AD[] = { 4, 8, 12, 11 };

SerineChronometer mainChrono, ADCchrono, slowThingsChrono, connectionChrono;
boolean notConnected = true;
unsigned long timeOfConnection = 4294967295UL;
char sender = detectorVD.id;  // At the beginning, the sender is the detector (We need one... it is not a big deal)
boolean continuousMode = false;
boolean waitForStart = false;
boolean waitForStop = false;
char separador = 'f';
boolean includeTime = true;
boolean incluiAD[] = { true, true, false, false };

SerineBuffer usbChannel;

/**
 * The ADCs are continuously read, and the new values are posted in regData
 * structures. Every time the user ask for new ADC values, the most recent 
 * not-read values are sent back.
 */

struct regData {
  unsigned long time;
  unsigned long adc[4];
};

union regGet {
  regData adValue;
  byte stream[20];
};
regGet rGet[2] = {
  { 0, 0, 0, 0, 0 },
  { 0, 0, 0, 0, 0 }
};
byte toBeGet = 0;

void answerGetStatus() {
  if (separador == 'f') {
    Serial.print(sender);
    Serial.print(detectorVD.id);
    Serial.print("gS");
    Serial.print(continuousMode ? 'T' : 'F');
    Serial.print(waitForStart ? 'T' : 'F');
    Serial.print(waitForStop ? 'T' : 'F');
    Serial.print(';');
  }
}

void answerGet() {
  char answer[] = "0000000";
  if (separador == 'f') {
    Serial.print(sender);
    Serial.print(detectorVD.id);
    Serial.print('g');
    if (incluiAD[0] || incluiAD[1]) {
      Serial.print('A');
      usbChannel.putUL(rGet[toBeGet].adValue.time, 0, 6, answer);
      Serial.print(answer);
      usbChannel.putUL(rGet[toBeGet].adValue.adc[0], 0, 6, answer);
      Serial.print(answer);
      usbChannel.putUL(rGet[toBeGet].adValue.adc[1], 0, 6, answer);
      Serial.print(answer);
      Serial.print(';');
    };
    if (incluiAD[2] || incluiAD[3]) {
      Serial.print('B');
      usbChannel.putUL(rGet[toBeGet].adValue.time, 0, 6, answer);
      Serial.print(answer);
      usbChannel.putUL(rGet[toBeGet].adValue.adc[2], 0, 6, answer);
      Serial.print(answer);
      usbChannel.putUL(rGet[toBeGet].adValue.adc[3], 0, 6, answer);
      Serial.print(answer);
      Serial.print(';');
    };
  } else {
    if (includeTime) {
      usbChannel.putUL(rGet[toBeGet].adValue.time, 0, 6, answer);
      Serial.print(answer);
    }
    for (int i = 0; i < 4; i++)
      if (incluiAD[i]) {
        Serial.print(separador);
        usbChannel.putUL(rGet[toBeGet].adValue.adc[i], 0, 6, answer);
        Serial.print(answer);
      }
    Serial.println();
  }
}

void inline handleDetector(char *message) {
  switch (message[2]) {
    case 'G':  // Get
      sender = message[1];
      switch (message[3]) {
        case 'r':
          continuousMode = true;
          waitForStart = false;
          waitForStop = false;
          answerGetStatus();
          break;
        case 'w':
          continuousMode = false;
          waitForStart = true;
          waitForStop = false;
          answerGetStatus();
          break;
        case 't':
          continuousMode = false;
          waitForStart = true;
          waitForStop = true;
          answerGetStatus();
          break;
        case 'h':
          continuousMode = false;
          waitForStart = false;
          waitForStop = false;
          answerGetStatus();
          break;
        case 'S':
          answerGetStatus();
          break;
        default: answerGet();
      };
      break;
    case 'S':  // Set
      switch (message[3]) {
        case 't':
          separador = '\t';
          break;
        case 's':
          separador = ' ';
          break;
        default:
          separador = message[3];
      }
      includeTime = (message[4] == '1');
      for (int i = 0; i < 4; i++)
        incluiAD[i] = (message[5 + i] == '1');

      break;
    case 'Z':  // Zero
      mainChrono.zero();
      break;
    case 'X':  // Connect
      connectionChrono.zero();
      notConnected = (message[3] == 'F');
      if (!notConnected) {
        if (message[4] != ';')
          timeOfConnection = usbChannel.getUL(4, 7, message);
        else
          timeOfConnection = 4294967295UL;
      }
      if (notConnected) {
        continuousMode = false;
        waitForStart = false;
        waitForStop = false;
      }
      char answer[32];
      answer[0] = message[1];
      answer[1] = message[0];
      answer[2] = 'x';
      answer[3] = notConnected ? 'F' : 'N';
      answer[4] = ';';
      answer[5] = 0;
      Serial.print(answer);
      break;
    case 'I':
      if (message[3] == 'x') {
        serineChangeID(&detectorVD, message);
      } else {
        serineAnswerID(message[1], detectorVD, answer);
        Serial.print(answer);
      }
      serineAnswerID(message[1], detectorVD, answer);
      Serial.print(answer);
      break;
    default:
      serineIdontKnow(message, detectorVD.id, answer);
      Serial.print(answer);
  }
}

void inline handleBroadcast(char *message) {
  char answer[MAX_MESSAGE];
  switch (message[2]) {
    case 'I':
      serineAnswerID(message[1], detectorVD, answer);
      Serial.print(answer);
      break;
    default:
      serineIdontKnow(message, detectorVD.id, answer);
      Serial.print(answer);
  }
}

void started() {
  if (waitForStart) {
    delayMicroseconds(100);
    if (digitalRead(PIN_START) == LOW) {  // check if this is a real start
      mainChrono.zero();
      continuousMode = true;
      waitForStart = false;
      answerGetStatus();
    }
  };
}

void stopped() {
  if (waitForStop) {
    delayMicroseconds(100);
    if (digitalRead(PORT_STOP) == LOW) {  // check if this is a real stop
      continuousMode = false;
      waitForStart = false;
      waitForStop = false;
      answerGetStatus();
    }
  };
}

void inline takeCareADC() {
  delayMicroseconds(1);
  byte keepGoing = HIGH;
  byte numberOfTimes = 0;
  while (keepGoing == HIGH) {
    delay(1);
    keepGoing = LOW;
    for (int i = 0; i < 4; i++)
      if (incluiAD[i]) keepGoing = keepGoing | digitalRead(SDO_AD[i]);
    numberOfTimes++;
    if (numberOfTimes > 100) keepGoing = false;
  };
  //  Serial.print("# "); Serial.println(numberOfTimes);
  unsigned long timeADC = mainChrono.now();
  /**
     * Read the first two bits:
     * 00 - 0 - OK
     * 01 - 1 - positive overflow
     * 10 - 2 - negative overflow
     */
  byte ovl[4];
  for (int i = 0; i < 4; i++) ovl[i] = 0;
  for (byte j = 0; j < 2; j++) {
    digitalWrite(SCK_AD, LOW);
    delayMicroseconds(1);
    digitalWrite(SCK_AD, HIGH);
    delayMicroseconds(1);
    for (int i = 0; i < 4; i++) ovl[i] = (ovl[i] << 1) | digitalRead(SDO_AD[i]);
  }
  long ad[4];
  // read B21 and determine if the value is positive or negative;
  digitalWrite(SCK_AD, LOW);
  delayMicroseconds(1);
  digitalWrite(SCK_AD, HIGH);
  delayMicroseconds(1);
  for (int i = 0; i < 4; i++) ad[i] = digitalRead(SDO_AD[i]) ? 0xFFFFFFFF : 0;
  // read the other bits
  for (int j = 0; j < 21; j++) {
    digitalWrite(SCK_AD, LOW);
    delayMicroseconds(1);
    digitalWrite(SCK_AD, HIGH);
    delayMicroseconds(1);
    for (int i = 0; i < 4; i++) ad[i] = (ad[i] << 1) | digitalRead(SDO_AD[i]);
  }
  //start a new A/D conversion
  digitalWrite(CS_AD, HIGH);
  delayMicroseconds(1);
  digitalWrite(CS_AD, LOW);
  // handle overflow and translate to a positive-only range
  for (int i = 0; i < 4; i++) {
    if (ovl[i] == 1) ad[i] = 2097151L;
    else if (ovl[i] == 2)
      ad[i] = -2097152L;
    ad[i] = ad[i] + 2097152L;
  }
  /**
     * Transfers the information to the register Get. This register has two 
     * positions: 0 and 1.
     */
  byte pos = 1 - toBeGet;
  rGet[pos].adValue.time = timeADC;
  for (int i = 0; i < 4; i++) rGet[pos].adValue.adc[i] = (unsigned long) ad[i];
  toBeGet = pos;  // point to these new ADC values
}

void setup() {
  Serial.begin(115200);
  // Prepare for external start and stop hardware commands (falling edge)
  pinMode(PIN_START, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(PIN_START), started, FALLING);
  pinMode(PORT_STOP, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(PORT_STOP), stopped, FALLING);
  // Prepare the ADCs
  for (int i = 0; i < 4; i++) digitalWrite(SDO_AD[i], LOW); // induce a pull down **********************************
  for (int i = 0; i < 4; i++) pinMode(SDO_AD[i], INPUT);
  pinMode(SCK_AD, OUTPUT);
  digitalWrite(SCK_AD, HIGH);
  pinMode(CS_AD, OUTPUT);
  digitalWrite(CS_AD, HIGH);
  delay(100);
  digitalWrite(CS_AD, LOW);
  mainChrono.zero();
  slowThingsChrono.zero();
  connectionChrono.zero();
}

void loop() {
  /**
     * After each A/D conversion, there is room (~73 ms) for other activities
     * before the next A/D datum.
     */
  ADCchrono.zero();
  while (!ADCchrono.expired(55)) {
    // Take care of messages from USB
    while (Serial.available() && (usbChannel.getSpace() > 1)) usbChannel.putChar(Serial.read());
    char message[MAX_MESSAGE];
    usbChannel.getMessage(message);
    if (message[0] != 0) {
      if (message[0] == detectorVD.id) handleDetector(message);
      else if (message[0] == 'B')
        handleBroadcast(message);
    };
    // Executes all the timed procedures here (if some)
  };
  takeCareADC();
  // Other tasks here
  if (continuousMode) answerGet();
  if (slowThingsChrono.expired(800)) {
    slowThingsChrono.zero();
    if (connectionChrono.expired(timeOfConnection)) {
      notConnected = true;
      continuousMode = false;
    }
    if (notConnected) {
      Serial.print('B');
      Serial.print(detectorVD.id);
      Serial.print('i');
      Serial.print(detectorVD.serial);
      Serial.print(';');
    }
  }
}
