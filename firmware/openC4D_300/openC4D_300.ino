// Arduino Firmware for openC4D

#include "Arduino.h"
#include <Serine.h>

// Virtual devices living in this microcontroller
SerineVirtualDevice detectorVD = {'d', "SdL013002"};

// Definitions for this microcontroller
const byte INT_START = 2; // Interrupt (INT) to be used when CE starts the data acquisition
const byte PORT_START = 7; // This is the digital port that corresponds to the INT above.
const byte INT_STOP = 3; // Interrupt (INT) to be used when CE stops the data acquisition
const byte PORT_STOP = 8; // This is the digital port that corresponds to the INT above.
const byte LED = 11;
const byte SCK_AD = 20; // Serial Clock for the ADC MCP3551
const byte CS_AD = 16; // Chip Select for the ADC MCP3551
const byte SDO_AD[] = {// pins for all the ADCs
    18, 17, 19, 21
};

SerineChronometer mainChrono, ADCchrono, slowThingsChrono, connectionChrono;
boolean notConnected = true;
unsigned long timeOfConnection = 4294967295UL;
char sender = detectorVD.id; // At the beginning, the sender is the detector (We need one... not a big deal)
boolean continuousMode = false;
boolean waitForStart = false;
boolean waitForStop = false;
char separador = 'f';
boolean includeTime = true;
boolean incluiAD[] = {
    true, true, false, false
};

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
    {
        0, 0, 0, 0, 0
    }
    ,
    {
        0, 0, 0, 0, 0
    }
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
        case 'G': // Get
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
        case 'S': // Set
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
        case 'Z': // Zero
            mainChrono.zero();
            break;
        case 'X': // Connect
            connectionChrono.zero();
            notConnected = (message[3] == 'F');
            if (!notConnected) {
                if (message[4] != ';')
                    timeOfConnection = usbChannel.getUL(4, 7, message);
                else timeOfConnection = 4294967295UL;
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
        if (digitalRead(PORT_START) == LOW) { // check if this is a real start
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
        if (digitalRead(PORT_STOP) == LOW) { // check if this is a real stop
            continuousMode = false;
            waitForStart = false;
            waitForStop = false;
            answerGetStatus();
        }
    };
}

void setup() {
    Serial.begin(115200);
    pinMode(LED, OUTPUT);
    digitalWrite(LED, LOW);
    // Prepare for external start
    pinMode(PORT_START, INPUT_PULLUP);
    attachInterrupt(INT_START, started, FALLING);
    // Prepare for external stop
    pinMode(PORT_STOP, INPUT_PULLUP);
    attachInterrupt(INT_STOP, stopped, FALLING);
    // Prepare the ADCs
    for (int i = 0; i < 4; i++) digitalWrite(SDO_AD[i], LOW);
    for (int i = 0; i < 4; i++) pinMode(SDO_AD[i], INPUT);
    pinMode(SCK_AD, OUTPUT);
    digitalWrite(SCK_AD, LOW);
    pinMode(CS_AD, OUTPUT);
    digitalWrite(CS_AD, HIGH);
    delay(100);
    digitalWrite(CS_AD, LOW);
    // Prepare the timers
    mainChrono.zero();
    slowThingsChrono.zero();
    connectionChrono.zero();
}

void inline takeCareADC() {
    byte keepGoing = HIGH;
    byte numberOfTimes = 0;
    while (keepGoing == HIGH) {
        delay(1);
        keepGoing = LOW;
        for (int i = 0; i < 4; i++)
            keepGoing = keepGoing | digitalRead(SDO_AD[i]);
        numberOfTimes++;
        if (numberOfTimes > 100) keepGoing = false;
    };

    unsigned long timeADC = mainChrono.now();

    /**
     * Read the first three bits. The following patterns may happen:
     * 000 - 0 - OK
     * 001 - 1 - positive overflow
     * 010 - 2 - negative overflow
     */
    byte ovl[4];
    for (int i = 0; i < 4; i++) ovl[i] = 0;
    for (byte j = 0; j < 3; j++) {
        digitalWrite(SCK_AD, HIGH);
        delayMicroseconds(1);
        for (int i = 0; i < 4; i++) ovl[i] = (ovl[i] << 1) | digitalRead(SDO_AD[i]);
        digitalWrite(SCK_AD, LOW);
    }
    /* Reads the first bit and inverts it to produce positive numbers only*/
    unsigned long ad[4];
    digitalWrite(SCK_AD, HIGH);
    delayMicroseconds(1);
    for (int i = 0; i < 4; i++) ad[i] = (digitalRead(SDO_AD[i]) == HIGH) ? 0 : 1;
    digitalWrite(SCK_AD, LOW);
    /* Read the other bits*/
    for (byte j = 4; j < 25; j++) {
        digitalWrite(SCK_AD, HIGH);
        delayMicroseconds(1);
        for (int i = 0; i < 4; i++) ad[i] = (ad[i] << 1) | digitalRead(SDO_AD[i]);
        digitalWrite(SCK_AD, LOW);
    }
    /* Indicates overflow whether it happens. */
    for (int i = 0; i < 4; i++)
        if (ovl[i] == 1) ad[i] = 16777215UL;
        else if (ovl[i] == 2) ad[i] = 0;
    /**
     * Transfers the information to the register Get. This register has two 
     * positions: 0 and 1.
     */
    byte pos = 1 - toBeGet;
    rGet[pos].adValue.time = timeADC;
    for (int i = 0; i < 4; i++) rGet[pos].adValue.adc[i] = ad[i];
    toBeGet = pos; // point to these new ADC values
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
            else if (message[0] == 'B') handleBroadcast(message);
        };
        // Executes all the timed procedures here (if some)
    };
    // Get new values from ADC 
    digitalWrite(LED, HIGH);
    takeCareADC();
    digitalWrite(LED, LOW);
    // Other tasks
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
