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
        config.c_cflag = (config.c_cflag & ~CSIZE) | CS8; 
        tcgetattr(fd, &config);
        // Set baudrate
        cfsetispeed(&config, B57600);
        cfsetospeed(&config, B57600);
        printf("Port opened\n");
}

uint8_t rightShift1Byte(uint8_t n,int bits){
    //printf("%i\n",(n>>bits & 0xFF));
    return (n>>bits & 0xFF);
}

uint8_t rightShift2Byte(uint16_t n,int bits){
    //printf("%i\n",(n>>bits & 0xFF));
    return (n>>bits & 0xFF);
}

uint8_t rightShift4Byte(uint32_t n,int bits){
    //printf("%i\n",(n>>bits & 0xFF));
    return (n>>bits & 0xFF);
}


uint8_t leftShift1Byte(uint8_t n,int bits){
    //printf("%i\n",(n<<bits));
    return (n<<bits);
}

uint8_t leftShift2Byte(uint16_t n,int bits){
    //printf("%i\n",(n<<bits));
    return (n<<bits);
}

uint8_t leftShift4Byte(uint32_t n,int bits){
    //printf("%i\n",(n<<bits));
    return (n<<bits);
}




void writeData(uint8_t* data){
     printf("writing %x \n",*data);
     write(fd,data,1);
}



void readPacket(){
     int i = 0;
     uint8_t packetType = 0;
     uint8_t payload[100];
     uint8_t recievedPacketData[100];
     while(1){
          uint8_t recievedFragment = 0;
          read(fd,&recievedFragment,1);
          if(recievedFragment!=0){
              recievedPacketData[i] = recievedFragment;
              printf("%x\n",recievedPacketData[i]);
              i++;
                   //Packet could be complete (minimal size)
          if(i>=12){
             if(recievedPacketData[0] != rightShift2Byte(FINGERPRINT_STARTCODE,8) || recievedPacketData[1]!= rightShift2Byte(FINGERPRINT_STARTCODE,0)){
               printf("The received packet do not begin with a valid header!");
               return;
               }
               int packetPayloadLength = leftShift1Byte(recievedPacketData[7],8);
               packetPayloadLength = packetPayloadLength | leftShift1Byte(recievedPacketData[8], 0);
               // Check if the packet is still fully received
        //Condition: index counter < packet payload length + packet frame
               if(i<packetPayloadLength+9){
                  continue;
               }
               //At this point the packet should be fully received
               
               packetType = recievedPacketData[6];
               
               //Calculate checksum
               //Checksum = Packet Type (1 byte) + packet length (2 bytes)+packet payload (n bytes)
               uint16_t packetChecksum = packetType + recievedPacketData[7] + recievedPacketData[8];
               int k = 0;
               for(int j=9;j<(9+packetPayloadLength-2);j++){
                    payload[k] = recievedPacketData[j];
                    k++;
                    packetChecksum += recievedPacketData[j];
               }
               
               uint16_t recievedChecksum = leftShift1Byte(recievedPacketData[i-2],8);
               recievedChecksum = recievedChecksum | leftShift1Byte(recievedPacketData[i-1],0);
               
               if(recievedChecksum != packetChecksum){
                   printf("The recieved packet is corrutped\n");
                   return;
               }
               
               printf("reached");
               return;
          }
          }else{
             continue;
          }
     }
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
     printf("all out\n");
}


int verifyPassword(){
    payloadBuffer[0] = FINGERPRINT_VERIFYPASSWORD;
    payloadBuffer[1] = rightShift4Byte(gPassword,24);
    payloadBuffer[2] = rightShift4Byte(gPassword,16);
    payloadBuffer[3] = rightShift4Byte(gPassword,8);
    payloadBuffer[4] = rightShift4Byte(gPassword,0);
    payloadSize = 5;
    packetTypeBuffer = FINGERPRINT_COMMANDPACKET;
    writePacket();
    readPacket();
}


int setPassword(uint32_t newPassword){
    if(newPassword < 0x00000000 || newPassword > 0xFFFFFFFF){
         printf("The given password is invalid\n");
         return 0;
    }
    packetTypeBuffer = FINGERPRINT_COMMANDPACKET;
    payloadBuffer[0] = FINGERPRINT_SETPASSWORD;
    payloadBuffer[1] = rightShift4Byte(newPassword,24);
    payloadBuffer[2] = rightShift4Byte(newPassword,16);
    payloadBuffer[3] = rightShift4Byte(newPassword,8);
    payloadBuffer[4] = rightShift4Byte(newPassword,0);
    payloadSize = 5;
    writePacket();
}


int setAddress(uint32_t newAddress){
    if(newAddress<0x00000000 || newAddress>0xFFFFFFFF){
        printf("The given address is invalid\n");
        return 0;
    }
    packetTypeBuffer = FINGERPRINT_COMMANDPACKET;
    payloadBuffer[0] = FINGERPRINT_SETADDRESS,
    payloadBuffer[1] = rightShift4Byte(newAddress,24);
    payloadBuffer[2] = rightShift4Byte(newAddress,16);
    payloadBuffer[3] = rightShift4Byte(newAddress,8);
    payloadBuffer[4] = rightShift4Byte(newAddress,0);
    payloadSize = 5;
    writePacket();
}


void convertImage(uint8_t buffer){
    packetTypeBuffer = FINGERPRINT_COMMANDPACKET;
    payloadBuffer[0] = FINGERPRINT_CONVERTIMAGE;
    payloadBuffer[1] = buffer;
    payloadSize = 2;
    writePacket();
}



void readImage(){
     packetTypeBuffer = FINGERPRINT_COMMANDPACKET;
     payloadSize = 1;
     payloadBuffer[0] = FINGERPRINT_READIMAGE;
     writePacket();
     readPacket();
}
















