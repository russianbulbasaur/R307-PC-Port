#ifndef SERIAL_H
#define SERIAL_H

#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <string>
#include <sys/ioctl.h>
#include <termios.h>
#include <unistd.h>

class Serial
{
    public:
        int fd;
    public:
        Serial(std::string device);

        ~Serial()
        {
            close(fd);
        };     

        int Available();
        void Read(char * buffer, int amountOfBytes);
        void Read(char * bytePtr);
        int Write(std::string message);
};

#endif
