#include "Serial.h"
#include <iostream>
#include "Serial.cpp"
#include "R30X_FPS.h"



void init(){
  devicePassword[0] = password & 0xFFU; //these can be altered later
  devicePassword[1] = (password >> 8) & 0xFFU;
  devicePassword[2] = (password >> 16) & 0xFFU;
  devicePassword[3] = (password >> 24) & 0xFFU;
  devicePasswordL = password;

  deviceAddress[0] = address & 0xFFU;
  deviceAddress[1] = (address >> 8) & 0xFFU;
  deviceAddress[2] = (address >> 16) & 0xFFU;
  deviceAddress[3] = (address >> 24) & 0xFFU;
  deviceAddressL = address;

  startCode[0] = FPS_ID_STARTCODE & 0xFFU; //packet start marker
  startCode[1] = (FPS_ID_STARTCODE >> 8) & 0xFFU;
  resetParameters();
}

void resetParameters () {
  deviceBaudrate = FPS_DEFAULT_BAUDRATE;  //this will be later altered by begin()
  baudMultiplier = uint16_t(FPS_DEFAULT_BAUDRATE / 9600);
  securityLevel = FPS_DEFAULT_SECURITY_LEVEL;  //threshold level for fingerprint matching
  dataPacketLength = FPS_DEFAULT_RX_DATA_LENGTH;
  librarySize = 1000;
  systemID = 0;

  txPacketType = FPS_ID_COMMANDPACKET; //type of packet
  txInstructionCode = FPS_CMD_VERIFYPASSWORD; //
  txPacketLength[0] = 0;
  txPacketLength[1] = 0;
  txPacketLengthL = 0;
  txDataBuffer = NULL; //packet data buffer
  txDataBufferLength = 0;
  txPacketChecksum[0] = 0;
  txPacketChecksum[1] = 0;
  txPacketChecksumL = 0;

  rxPacketType = FPS_ID_COMMANDPACKET; //type of packet
  rxConfirmationCode = FPS_CMD_VERIFYPASSWORD; //
  rxPacketLength[0] = 0;
  rxPacketLength[1] = 0;
  rxPacketLengthL = 0;
  rxDataBuffer = NULL; //packet data buffer
  rxDataBufferLength = 0;
  rxPacketChecksum[0] = 0;
  rxPacketChecksum[1] = 0;
  rxPacketChecksumL = 0;

  fingerId = 0; //initialize them
  matchScore = 0;
  templateCount = 0;
}


int main()
{
    init();
    //TEST CASE FOR WRITING DATA
    Serial serial("/dev/ttyUSB0");
    int fid = serial.fd;
    unsigned char startHigh = 0xEFU;
    unsigned char startLow = 0x01U;
    char add1 = 0xFFU;
     char add2 = 0xFFU;
     char add3 = 0xFFU;
     char add4 = 0xFFU;
    unsigned char ident = 0x01U;
    unsigned char length1 = 0x07U;
    unsigned char length2 = 0x00U;
    unsigned char instr = 0x13U;
    int password = 0xFFFFFFFF;
    unsigned char checksum1 = 0x05U;
    unsigned char checksum2 = 0x00U;
    write(fid,&startHigh,1);
    write(fid,&startLow,1);
    write(fid,&add1,1);
    write(fid,&add2,1);
    write(fid,&add3,1);
    write(fid,&add4,1);
    write(fid,&ident,1);
    write(fid,&length1,1);
    write(fid,&length2,1);
    write(fid,&instr,1);
    write(fid,&password,4);
    write(fid,&checksum1,1);
    write(fid,&checksum2,1);
    char mess[100];
    
    read(fid,&mess,100);
    printf("%x\n",mess);
    return 0;
  txPacketType = FPS_ID_COMMANDPACKET; //type of packet - 1 byte
  txInstructionCode = FPS_CMD_SCANFINGER; //instruction code - 1 byte
  txPacketLengthL = txDataBufferLength + 3; //1 byte for command, 2 bytes for checksum
  txPacketLength[0] = txPacketLengthL & 0xFFU; //get lower byte
  txPacketLength[1] = (txPacketLengthL >> 8) & 0xFFU; //get high byte

  txPacketChecksumL = txPacketType + txPacketLength[0] + txPacketLength[1] + txInstructionCode; //sum of packet ID and packet length bytes

  for(int i=0; i<txDataBufferLength; i++) {
    txPacketChecksumL += txDataBuffer[i]; //add rest of the data bytes
  }

  txPacketChecksum[0] = txPacketChecksumL & 0xFFU; //get low byte
  txPacketChecksum[1] = (txPacketChecksumL >> 8) & 0xFFU; //get high byte
   write(fid,&startCode[0],sizeof(startCode[0]));
    write(fid,&startCode[1],sizeof(startCode[1]));
     write(fid,&deviceAddress[0],sizeof(deviceAddress[0]));
     write(fid,&deviceAddress[1],sizeof(deviceAddress[1]));
     write(fid,&deviceAddress[2],sizeof(deviceAddress[2]));
     write(fid,&deviceAddress[3],sizeof(deviceAddress[3]));
     write(fid,&txPacketType,sizeof(txPacketType));
     write(fid,&txPacketLength[0],sizeof(txPacketLength[0]));
     write(fid,&txPacketLength[1],sizeof(txPacketLength[1]));
     write(fid,&txInstructionCode,sizeof(txInstructionCode));
     for(int i=(txDataBufferLength-1); i>=0; i--) {
        write(fid,&txDataBuffer[i],sizeof(txDataBuffer[i])); //send high byte first
      }
     write(fid,&txPacketChecksum[0],sizeof(txPacketChecksum[0]));
     write(fid,&txPacketChecksum[1],sizeof(txPacketChecksum[1]));

    return EXIT_SUCCESS;
}
