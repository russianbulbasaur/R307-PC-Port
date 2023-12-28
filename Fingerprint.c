#include "data.h"
#include <termios.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>

uint32_t gAddress = 0;
uint32_t gPassword = 0;
int fd = 0;

uint8_t packetTypeBuffer;
unsigned char payloadBuffer[9];
int payloadSize = 0;

void init(){
       char* port="/dev/ttyUSB0";
       int baudRate=57600;
       uint32_t address=0xFFFFFFFF;
       uint32_t password=0x00000000;
      if(baudRate < 9600 || baudRate > 115200 || baudRate%9600!= 0){
            printf("The given baud rate is invalid!");
            return;
         }

        if ( address < 0x00000000 || address > 0xFFFFFFFF ){
            printf("The given address is invalid!");
          }

        if (password < 0x00000000 || password > 0xFFFFFFFF){
            printf("The given password is invalid!"); 
        }
        gAddress = address;
        gPassword = password;
         // Open port
        fd = open(port, O_RDWR);
        if (fd < 0)
        {
           printf("Error opening port\n");
           return;
        }

        // Config
        struct termios config;

        tcgetattr(fd, &config);

        // Set baudrate
        cfsetispeed(&config, baudRate);
        cfsetospeed(&config, baudRate);
}

uint8_t rightShift1Byte(uint8_t n,int bits){
    printf("%i\n",(n>>bits & 0xFF));
    return (n>>bits & 0xFF);
}

uint8_t rightShift2Byte(uint16_t n,int bits){
    printf("%i\n",(n>>bits & 0xFF));
    return (n>>bits & 0xFF);
}

uint8_t rightShift4Byte(uint32_t n,int bits){
    printf("%i\n",(n>>bits & 0xFF));
    return (n>>bits & 0xFF);
}


uint8_t leftShift1Byte(uint8_t n,int bits){
    printf("%i\n",(n<<bits));
    return (n<<bits);
}

uint8_t leftShift2Byte(uint16_t n,int bits){
    printf("%i\n",(n<<bits));
    return (n<<bits);
}

uint8_t leftShift4Byte(uint32_t n,int bits){
    printf("%i\n",(n<<bits));
    return (n<<bits);
}




void writeData(uint8_t* data){
     write(fd,data,1);
}





void writePacket(){
     uint8_t startCodeHigh = rightShift2Byte(FINGERPRINT_STARTCODE,8);
     uint8_t startCodeLow = rightShift2Byte(FINGERPRINT_STARTCODE,0);
     uint8_t addressOne = rightShift4Byte(gAddress,24);
     uint8_t addressTwo = rightShift4Byte(gAddress,16);
     uint8_t addressThree = rightShift4Byte(gAddress,8);
     uint8_t addressFour = rightShift4Byte(gAddress,0);
     uint8_t packetType = packetTypeBuffer;
     uint8_t payload[payloadSize];
     for(int i=0;i<payloadSize;i++){
     //Copy buffer
          payload[i] = payloadBuffer[i];
     }
     uint16_t packetLength = sizeof(payload) + 2;
     uint8_t packetLengthOne = rightShift2Byte(packetLength,8);
     uint8_t packetLengthTwo = rightShift2Byte(packetLength,0);
     uint16_t packetChecksum = packetTypeBuffer + rightShift2Byte(packetLength,8) + rightShift2Byte(packetLength,0);
      writeData(&startCodeHigh);
     writeData(&startCodeLow);
     writeData(&addressOne);
     writeData(&addressTwo);
     writeData(&addressThree);
     writeData(&addressFour);
     writeData(&packetType);
     writeData(&packetLengthOne);
     writeData(&packetLengthTwo);
     for(int i=0;i<sizeof(payload);i++){
         writeData(&payload[i]);
         packetChecksum += payload[i];
     }
     uint8_t checksumOne = rightShift2Byte(packetChecksum,8);
     uint8_t checksumTwo = rightShift2Byte(packetChecksum,0);
     writeData(&checksumOne);
     writeData(&checksumTwo);
}



void readImage(){
     packetTypeBuffer = FINGERPRINT_COMMANDPACKET;
     payloadSize = 1;
     payloadBuffer[0] = FINGERPRINT_READIMAGE;
     writePacket();
}
















