#include "R30X_FPS.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include "Serial.h"
#include <chrono>
#include <thread>

using namespace std::this_thread; // sleep_for, sleep_until
using namespace std::chrono; 


R30X_FPS::R30X_FPS (uint32_t password, uint32_t address) {
  //storing 32-bit values as 8-bit values in arrays can make many operations easier later
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

  resetParameters();  //initialize and reset and all parameters
}

//=========================================================================//
//initializes the serial port
//the baudrate received here will override the default one

void R30X_FPS::begin (uint32_t baudrate) {
    fd = open("/dev/ttyUSB0", O_RDWR | O_NOCTTY);
     struct termios options;
    tcgetattr(fd, &options);
    cfsetispeed(&options, B9600); 
    cfsetospeed(&options, B9600);  
    tcsetattr(fd, TCSANOW, &options);
}

//=========================================================================//
//reset some parameters

void R30X_FPS::resetParameters (void) {
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

//=========================================================================//
//send a data packet to the FPS (fingerprint scanner)

uint8_t R30X_FPS::sendPacket (uint8_t type, uint8_t command, uint8_t* data, uint16_t dataLength) {
  int fid = open("/dev/ttyUSB0", O_RDWR | O_NOCTTY);
     struct termios options;
    tcgetattr(fid, &options);
    cfsetispeed(&options, B9600); 
    cfsetospeed(&options, B9600);  
    tcsetattr(fid, TCSANOW, &options);
  if(data != NULL) {  //sometimes there's no additional data except the command
    txDataBuffer = data;
    txDataBufferLength = dataLength;
  }
  else {
    txDataBuffer = NULL;
    txDataBufferLength = 0;
  }

  txPacketType = type; //type of packet - 1 byte
  txInstructionCode = command; //instruction code - 1 byte
  txPacketLengthL = txDataBufferLength + 3; //1 byte for command, 2 bytes for checksum
  txPacketLength[0] = txPacketLengthL & 0xFFU; //get lower byte
  txPacketLength[1] = (txPacketLengthL >> 8) & 0xFFU; //get high byte

  txPacketChecksumL = txPacketType + txPacketLength[0] + txPacketLength[1] + txInstructionCode; //sum of packet ID and packet length bytes

  for(int i=0; i<txDataBufferLength; i++) {
    txPacketChecksumL += txDataBuffer[i]; //add rest of the data bytes
  }

  txPacketChecksum[0] = txPacketChecksumL & 0xFFU; //get low byte
  txPacketChecksum[1] = (txPacketChecksumL >> 8) & 0xFFU; //get high byte
   write(fid,&startCode[1],sizeof(startCode[1]));
    write(fid,&startCode[0],sizeof(startCode[1]));
     write(fid,&deviceAddress[3],sizeof(deviceAddress[3]));
     write(fid,&deviceAddress[2],sizeof(deviceAddress[2]));
     write(fid,&deviceAddress[1],sizeof(deviceAddress[1]));
     write(fid,&deviceAddress[0],sizeof(deviceAddress[0]));
     write(fid,&txPacketType,sizeof(txPacketType));
     write(fid,&txPacketLength[1],sizeof(txPacketLength[1]));
     write(fid,&txPacketLength[0],sizeof(txPacketLength[0]));
     write(fid,&txInstructionCode,sizeof(txInstructionCode));
for(int i=(txDataBufferLength-1); i>=0; i--) {
        write(fid,&txDataBuffer[i],sizeof(txDataBuffer[i])); //send high byte first
      }
     write(fid,&txPacketChecksum[1],sizeof(txPacketChecksum[1]));
     write(fid,&txPacketChecksum[0],sizeof(txPacketChecksum[0]));

  return FPS_RX_OK;
}




uint8_t R30X_FPS::receivePacket (uint32_t timeout) {
  unsigned char* dataBuffer;
  //read(fd,dataBuffer,2);
  printf("%x",dataBuffer);
  return 0;
  if(dataPacketLength < 64) { //data buffer length should be at least 64 bytes
    dataBuffer = new uint8_t[64](); //this contains only the data
  }
  else {
    dataBuffer = new uint8_t[FPS_DEFAULT_RX_DATA_LENGTH]();
  }

  rxDataBuffer = dataBuffer;
  uint8_t serialBuffer[FPS_DEFAULT_SERIAL_BUFFER_LENGTH] = {0}; //serialBuffer will store high byte at the start of the array
  uint16_t serialBufferLength = 0;
  uint8_t byteBuffer = 0;


  if(serialBufferLength == 0) {
    return FPS_RX_TIMEOUT;
  }

  if(serialBufferLength < 10) {
    return FPS_RX_BADPACKET;
  }

  uint16_t token = 0; //a position counter/indicator

  //the following loop checks each segments of the data packet for errors, and retrieve the correct ones
  while(true) {
    switch (token) {
      case 0: //test packet start codes
        if(serialBuffer[token] == startCode[1])
          break;
        else {

          return FPS_RX_BADPACKET;
        }

      case 1:
        if(serialBuffer[token] == startCode[0])
          break;
        else {

          return FPS_RX_BADPACKET;
        }

      case 2: //test device address
        if(serialBuffer[token] == deviceAddress[3])
          break;
        else {

          return FPS_RX_BADPACKET;
        }
      
      case 3:
        if(serialBuffer[token] == deviceAddress[2])
          break;
        else {

          return FPS_RX_BADPACKET;
        }

      case 4:
        if(serialBuffer[token] == deviceAddress[1])
          break;
        else {

          return FPS_RX_BADPACKET;
        }
      
      case 5:
        if(serialBuffer[token] == deviceAddress[0])
          break;
        else {

          return FPS_RX_BADPACKET;
        }

      case 6: //test for valid packet type
        if((serialBuffer[token] == FPS_ID_COMMANDPACKET) || (serialBuffer[token] == FPS_ID_DATAPACKET) || (serialBuffer[token] == FPS_ID_ACKPACKET) || (serialBuffer[token] == FPS_ID_ENDDATAPACKET)) {
          rxPacketType = serialBuffer[token]; //store the packet ID to class variable
          break;
        }
        else {

          return FPS_RX_WRONG_RESPONSE;
        }

      case 7: //read packet data length
        if((serialBuffer[token] > 0) || (serialBuffer[token + 1] > 0)) {
          rxPacketLength[0] = serialBuffer[token + 1];  //lower byte
          rxPacketLength[1] = serialBuffer[token];  //higher byte
          rxPacketLengthL = uint16_t(rxPacketLength[1] << 8) + rxPacketLength[0]; //calculate the full length value
          rxDataBufferLength = rxPacketLengthL - 3; //subtract 2 for checksum and 1 for command
          token++; //because we read one additional bytes here
          break;
        }

        else {

          return FPS_RX_WRONG_RESPONSE;
        }

      case 9: //read confirmation or instruction code
        rxConfirmationCode = serialBuffer[token]; //the first byte of data will be either instruction or confirmation code
        break;

      case 10: //read data
        for(int i=0; i < rxDataBufferLength; i++) {
          rxDataBuffer[(rxDataBufferLength - 1) - i] = serialBuffer[token + i]; //store low values at start of the rxDataBuffer array
        }
        break;
      
      case 11: //read checksum
        if(rxDataBufferLength == 0) { //sometimes there's no data other than the confirmation code
          rxPacketChecksum[0] = serialBuffer[token]; //lower byte
          rxPacketChecksum[1] = serialBuffer[token - 1]; //high byte
          rxPacketChecksumL = uint16_t(rxPacketChecksum[1] << 8) + rxPacketChecksum[0]; //calculate L value

          uint16_t tempSum = 0; //temp checksum 

          tempSum = rxPacketType + rxPacketLength[0] + rxPacketLength[1] + rxConfirmationCode;

          if(rxPacketChecksumL == tempSum) { //check if the calculated checksum matches the received one

            return FPS_RX_OK; //packet read success
          }

          else { //if the checksums do not match

            return FPS_RX_BADPACKET;  //then that's an error
          }
          break;
        }

        //-------------------------------------------------------------------------//

        else if((serialBuffer[token + (rxDataBufferLength-1)] > 0) || ((serialBuffer[token + 1 + (rxDataBufferLength-1)] > 0))) {
          rxPacketChecksum[0] = serialBuffer[token + 1 + (rxDataBufferLength-1)]; //lower byte
          rxPacketChecksum[1] = serialBuffer[token + (rxDataBufferLength-1)]; //high byte
          rxPacketChecksumL = uint16_t(rxPacketChecksum[1] << 8) + rxPacketChecksum[0]; //calculate L value

          uint16_t tempSum = 0; //temp checksum 

          tempSum = rxPacketType + rxPacketLength[0] + rxPacketLength[1] + rxConfirmationCode;

          for(int i=0; i < rxDataBufferLength; i++) {
            tempSum += rxDataBuffer[i]; //calculate data checksum
          }

          if(rxPacketChecksumL == tempSum) { //check if the calculated checksum matches the received one

            return FPS_RX_OK; //packet read success
          }

          else { //if the checksums do not match

            return FPS_RX_BADPACKET;  //then that's an error
          }
          break;
        }

        //-------------------------------------------------------------------------//

        else { //if the checksum received is 0

          return FPS_RX_BADPACKET;  //that too an error
        }
        break;
    
      default:
        break;
    }
    token++; //increment to progressively scan the packet
  }
}



//=========================================================================//
//verify if the password set by user is correct

uint8_t R30X_FPS::verifyPassword (uint32_t inputPassword) {
  uint8_t inputPasswordBytes[4] = {0};  //to store the split password
  inputPasswordBytes[0] = inputPassword & 0xFFU;  //save each bytes
  inputPasswordBytes[1] = (inputPassword >> 8) & 0xFFU;
  inputPasswordBytes[2] = (inputPassword >> 16) & 0xFFU;
  inputPasswordBytes[3] = (inputPassword >> 24) & 0xFFU;

  sendPacket(FPS_ID_COMMANDPACKET, FPS_CMD_VERIFYPASSWORD, inputPasswordBytes, 4); //send the command and data
    if(rxConfirmationCode == FPS_RESP_OK) {
      //save the input password if it is correct
      //this is actually redundant, but can make sure the right password is available to execute further commands
      devicePasswordL = inputPassword;
      devicePassword[0] = inputPasswordBytes[0]; //save the new password as array
      devicePassword[1] = inputPasswordBytes[1];
      devicePassword[2] = inputPasswordBytes[2];
      devicePassword[3] = inputPasswordBytes[3];

      return FPS_RESP_OK; //password is correct
    }
    else {


      return rxConfirmationCode;  //password is not correct and so send confirmation code
    }
}

int main(){ 
  R30X_FPS ob;
  ob.begin(9600);
  ob.sendPacket(FPS_ID_COMMANDPACKET,FPS_CMD_SCANFINGER,NULL,0);
  ob.receivePacket(0);
  return 0;
}


