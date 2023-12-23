#include "Serial.h"
#include <stdexcept>
#include <string.h>

Serial::Serial(std::string device)
{
    // Open port
    fd = open(device.c_str(), O_RDWR | O_NOCTTY | O_NDELAY);
    if (fd < 0)
    {
        throw std::runtime_error("Failed to open port!");
    }

    // Config
    struct termios config;

    tcgetattr(fd, &config);

    // Set baudrate
    cfsetispeed(&config, B9600);
    cfsetospeed(&config, B9600);

    // 9600 8N1
    config.c_cflag &= ~PARENB;
    config.c_cflag &= ~CSTOPB;
    config.c_cflag &= ~CSIZE;
    config.c_cflag |=  CS8;

    // Disable hardware based flow control
    config.c_cflag &= ~CRTSCTS;

    // Enable receiver
    config.c_cflag |= CREAD | CLOCAL;                               

    // Disable software based flow control
    config.c_iflag &= ~(IXON | IXOFF | IXANY);

    // Termois Non Cannoincal Mode 
    config.c_lflag &= ~(ICANON | ECHO | ECHOE | ISIG); 

    // Minimum number of characters for non cannoincal read
    config.c_cc[VMIN]  = 1;

    // Timeout in deciseconds for read
    config.c_cc[VTIME] = 0; 

    // Save config
    if (tcsetattr(fd, TCSANOW, &config) < 0)                        
    {
        close(fd);
        throw std::runtime_error("Failed to configure port!");
    }

    // Flush RX Buffer
    if (tcflush(fd, TCIFLUSH) < 0)
    {
        close(fd);
        throw std::runtime_error("Failed to flush buffer!");
    }
}

int Serial::Available()
{
    int bytes = 0;
    if (ioctl(fd, TIOCINQ, &bytes) < 0)
    {
        close(fd);
        throw std::runtime_error("Failed to check buffer!");
    }
    return bytes;
}

void Serial::Read(char * buffer, int amountOfBytes)
{
    if (read(fd, buffer, amountOfBytes) < 0)
    {
        close(fd);
        throw std::runtime_error("Failed to read bytes!");
    }
}

void Serial::Read(char * bytePtr)
{
    return Serial::Read(bytePtr, 1);
}

int Serial::Write(std::string message)
{
    int length = message.size();
    if (length > 100)
    {
        throw std::invalid_argument("Message may not be longer than 100 bytes!");
    }

    char msg[101];
    strcpy(msg, message.c_str());

    int bytesWritten = write(fd, msg, length);

    if (bytesWritten < 0)
    {
        close(fd);
        throw std::runtime_error("Failed to write bytes!");
    }

    return bytesWritten;
}
