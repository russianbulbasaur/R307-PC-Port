package dl.tech.bioams.R307;



public class DataCodes {
    static final byte[] FINGERPRINT_STARTCODE = {(byte)((int)0xEF),(byte)0x01};

//Packet identification

    static final byte FINGERPRINT_COMMANDPACKET = 0x01 & 0xff;

    public static final byte FINGERPRINT_ACKPACKET = 0x07;
    public static final byte FINGERPRINT_DATAPACKET = 0x02;
    public static final byte FINGERPRINT_ENDDATAPACKET = 0x08;

//Instruction codes

    public static final byte FINGERPRINT_VERIFYPASSWORD = 0x13;
    public  static final byte FINGERPRINT_SETPASSWORD = 0x12;
    public  static final byte FINGERPRINT_SETADDRESS = 0x15;
    public  static final byte FINGERPRINT_SETSYSTEMPARAMETER = 0x0E;
    public  static final byte FINGERPRINT_GETSYSTEMPARAMETERS = 0x0F;
    public  static final byte FINGERPRINT_TEMPLATEINDEX = 0x1F;
    public  static final byte FINGERPRINT_TEMPLATECOUNT = 0x1D;

    public static final byte FINGERPRINT_READIMAGE = 0x01;

    // Note: The documentation mean upload to host computer.
    public  static final byte FINGERPRINT_DOWNLOADIMAGE = 0x0A;

    public  static final byte FINGERPRINT_CONVERTIMAGE = 0x02;

    public  static final byte FINGERPRINT_CREATETEMPLATE = 0x05;
    public  static final byte FINGERPRINT_STORETEMPLATE = 0x06;
    public static final byte FINGERPRINT_SEARCHTEMPLATE = 0x04;
    public  static final byte FINGERPRINT_LOADTEMPLATE = 0x07;
    public  static final byte FINGERPRINT_DELETETEMPLATE = 0x0C;

    public  static final byte FINGERPRINT_CLEARDATABASE = 0x0D;
    public  static final byte FINGERPRINT_GENERATERANDOMNUMBER = 0x14;
    public  static final byte FINGERPRINT_COMPARECHARACTERISTICS = 0x03;

    //Note: The documentation mean download from host computer.
    public  static final byte FINGERPRINT_UPLOADCHARACTERISTICS = 0x09;
    // Note: The documentation mean upload to host computer.
    public  static final byte FINGERPRINT_DOWNLOADCHARACTERISTICS = 0x08;

//Parameters of setSystemParameter()


    public  static final int FINGERPRINT_SETSYSTEMPARAMETER_BAUDRATE = 4;
    public  static final int FINGERPRINT_SETSYSTEMPARAMETER_SECURITY_LEVEL = 5;
    public static final int FINGERPRINT_SETSYSTEMPARAMETER_PACKAGE_SIZE = 6;

//Packet reply confirmations

    public static final byte FINGERPRINT_OK = 0x00;
    public static final byte FINGERPRINT_ERROR_COMMUNICATION = 0x01;

    static final byte FINGERPRINT_ERROR_WRONGPASSWORD = 0x13;

    static final byte FINGERPRINT_ERROR_INVALIDREGISTER = 0x1A;

    public static final byte FINGERPRINT_ERROR_NOFINGER = 0x02;
    public static final byte FINGERPRINT_ERROR_READIMAGE = 0x03;

    static final byte FINGERPRINT_ERROR_MESSYIMAGE = 0x06;
    static final byte FINGERPRINT_ERROR_FEWFEATUREPOINTS = 0x07;
    static final byte FINGERPRINT_ERROR_INVALIDIMAGE = 0x15;

    static final byte FINGERPRINT_ERROR_CHARACTERISTICSMISMATCH = 0x0A;

    static final byte FINGERPRINT_ERROR_INVALIDPOSITION = 0x0B;
    static final byte FINGERPRINT_ERROR_FLASH = 0x18;

    static final byte FINGERPRINT_ERROR_NOTEMPLATEFOUND = 0x09;

    static final byte FINGERPRINT_ERROR_LOADTEMPLATE = 0x0C;

    static final byte FINGERPRINT_ERROR_DELETETEMPLATE = 0x10;

    static final byte FINGERPRINT_ERROR_CLEARDATABASE = 0x11;

    static final byte FINGERPRINT_ERROR_NOTMATCHING = 0x08;

    static final byte FINGERPRINT_ERROR_DOWNLOADIMAGE = 0x0F;
    static final byte FINGERPRINT_ERROR_DOWNLOADbyteACTERISTICS = 0x0D;

//Unknown error codes

    static final byte FINGERPRINT_ADDRCODE = 0x20;
    static final byte FINGERPRINT_PASSVERIFY = 0x21;

    static final byte FINGERPRINT_PACKETRESPONSEFAIL = 0x0E;

    static final byte FINGERPRINT_ERROR_TIMEOUT = (byte)(0xFF & 0xff);
    static final byte FINGERPRINT_ERROR_BADPACKET = (byte)(0xFE & 0xff);

//byte buffers


    public static final byte FINGERPRINT_CHARBUFFER1 = 0x01;
//byte buffer 1


    public static final byte FINGERPRINT_CHARBUFFER2 = 0x02;
//byte buffer 2

}
