package dl.tech.bioams.R307;

import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ACKPACKET;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER1;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER2;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_COMMANDPACKET;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_COMPARECHARACTERISTICS;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CONVERTIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CREATETEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_COMMUNICATION;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_FEWFEATUREPOINTS;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_INVALIDIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_MESSYIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_NOTEMPLATEFOUND;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_READIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_OK;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_READIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_SEARCHTEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_STARTCODE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_STORETEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_TEMPLATECOUNT;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_TEMPLATEINDEX;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;


import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import dl.tech.bioams.models.Packet;
import dl.tech.bioams.models.ProcedureCallback;

public class Fingerprint{

    byte[] gAddress = {(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff};
    byte[] gPassword = {0x00,0x00,0x00,0x00};
    int fd = 0;
    Queue<Packet> packetQueue;
    private Context con;
    private Thread subThread;
    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbDevice device;
    private UsbSerialDevice serialPort;
    public void init(Context context) throws IOException {
        // Find all available drivers from attached devices.
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        this.con = context;
        usbManager = (UsbManager) con.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {

            // first, dump the map for diagnostic purposes
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                Log.d("devices : ", String.format("USBDevice.HashMap (vid:pid) (%X:%X)-%b class:%X:%X name:%s",
                        device.getVendorId(), device.getProductId(),
                        UsbSerialDevice.isSupported(device),
                        device.getDeviceClass(), device.getDeviceSubclass(),
                        device.getDeviceName()));
            }

            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

//                if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003)) {
                if (UsbSerialDevice.isSupported(device)) {
                    // There is a device connected to our Android device. Try to open it as a Serial Port.
                    requestUserPermission();
                    break;
                } else {
                    connection = null;
                    device = null;
                }
            }
        } else {
            Log.d("error", "findSerialPortDevice() usbManager returned empty device list.");
        }
        connection = usbManager.openDevice(device);
        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (serialPort != null) {
            if (serialPort.syncOpen()) {
                serialPort.setBaudRate(57600);
                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            }
        }
    }





    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private void requestUserPermission() {
        Log.d("Requeting permission : ", String.format("requestUserPermission(%X:%X)", device.getVendorId(), device.getProductId() ) );
        //PendingIntent mPendingIntent = PendingIntent.getBroadcast(activity, 0, new Intent("com.felhr.usbserial.USB_PERMISSION"), 0);
        usbManager.requestPermission(device, null);
    }




    byte rightShift(int x,int n){
        return (byte)((x >> n) & 0xff);
    }

    synchronized Packet writePacket(byte packetType,byte[] payload,int sig) throws IOException, InterruptedException {
        byte[] packet = new byte[100];
        byte startCodeHigh = FINGERPRINT_STARTCODE[0];
        packet[0] = startCodeHigh;
        byte startCodeLow = FINGERPRINT_STARTCODE[1];
        packet[1] = startCodeLow;
        byte addressOne = gAddress[0];
        packet[2] = addressOne;
        byte addressTwo = gAddress[1];
        packet[3] = addressTwo;
        byte addressThree = gAddress[2];
        packet[4] = addressThree;
        byte addressFour = gAddress[3];
        packet[5] = addressFour;
        packet[6] = packetType;
        int packetLength = payload.length + 2;
        byte packetLengthOne = rightShift(packetLength,8);
        byte packetLengthTwo = rightShift(packetLength,0);
        packet[7] = packetLengthOne;
        packet[8] = packetLengthTwo;
        byte packetChecksum = (byte) (packetType + rightShift(packetLength,8) + rightShift(packetLength,0));
        int i = 9;
        for (byte b : payload) {
            packet[i] = b;
            packetChecksum += b;
            i++;
        }
        byte checksumOne = rightShift(packetChecksum,8);
        byte checksumTwo = rightShift(packetChecksum,0);
        packet[i] = (sig==2)?0:checksumOne;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("checksum 1 : %02X",checksumOne));
        System.out.println(sb.toString());
        packet[i+1] = checksumTwo;
        byte[] finalPacket = new byte[i+2];
        Packet pack = null;
        System.out.print("Writing : ");
        for (int j = 0; j < finalPacket.length; j++) {
            finalPacket[j] = packet[j];
            byte h[] = new byte[1];
            h[0] = finalPacket[j];
            sb = new StringBuilder();
            sb.append(String.format("%02X",finalPacket[j]));
            System.out.print(sb.toString());
            serialPort.syncWrite(h,2);
        }
        System.out.println();
        System.out.print("Reading : ");
        byte[] response = new byte[20];
        int readBytes = serialPort.syncRead(response,2);
        if(readBytes>=12){
            for(byte b:response){
                sb = new StringBuilder();
                sb.append(String.format("%02X",b));
                System.out.print(sb.toString());
            }
            pack = readPacket(response,sig);
            if(pack!=null){
                return pack;
            }
        }
        for(byte b:response){
             sb = new StringBuilder();
            sb.append(String.format("%02X",b));
            System.out.print(sb.toString());
        }
        System.out.println();
        Thread.sleep(200);
        return pack;
    }



    public void convertImage(byte charBufferNumber,ProcedureCallback callback) throws IOException, InterruptedException {
        if(charBufferNumber!=FINGERPRINT_CHARBUFFER1 && charBufferNumber!=FINGERPRINT_CHARBUFFER2){
            return;
        }
        byte[] payload = new byte[2];
        HashMap<String,Object> data = new HashMap<>();
        payload[0] = FINGERPRINT_CONVERTIMAGE;
        payload[1] = charBufferNumber;
        Packet packet = null;
        while(true){
            if(packet==null){
                packet = writePacket(FINGERPRINT_COMMANDPACKET,payload,1);
                continue;
            }
            if (packet.packetType != FINGERPRINT_ACKPACKET) {
                data.put("message","The received packet is no ack packet!");
                data.put("status",0);
                break;
            }
            if (packet.packetPayload[0] == FINGERPRINT_OK) {
                data.put("message","Converted Image");
                data.put("status",1);
                break;
            } else if (packet.packetPayload[0] == FINGERPRINT_ERROR_COMMUNICATION) {
                data.put("message","Communication error");
                data.put("status",0);
                break;
            } else if (packet.packetPayload[0 ]== FINGERPRINT_ERROR_MESSYIMAGE) {
                data.put("message","Messy image");
                data.put("status",0);
                break;
            }

            else if(packet.packetPayload[0] == FINGERPRINT_ERROR_FEWFEATUREPOINTS) {
                data.put("message","Too few feature points");
                data.put("status",0);
                break;
            }

            else if(packet.packetPayload[0] == FINGERPRINT_ERROR_INVALIDIMAGE) {
                data.put("message","Invalid image");
                data.put("status",0);
                break;
            }
            else{
                data.put("message","UK error");
                data.put("status",0);
                break;
            }
        }
        System.out.println("broken");
        callback.subProcedureFinished(data);
    }


    public void searchTemplate(byte charBufferNumber,int positionStart,int count,ProcedureCallback callback) throws IOException, InterruptedException {
        if(charBufferNumber!= FINGERPRINT_CHARBUFFER1 && charBufferNumber!=FINGERPRINT_CHARBUFFER2){
            return;
        }
        int templatesCount = 0;
        if(count>0){
            templatesCount = count;
        }else{
            templatesCount = 1000;
        }
        byte[] payload = new byte[6];
        payload[0] = FINGERPRINT_SEARCHTEMPLATE;
        payload[1] = charBufferNumber;
        payload[2] = rightShift(positionStart,8);
        payload[3] = rightShift(positionStart,0);
        payload[4] = rightShift(templatesCount,8);
        payload[5] = rightShift(templatesCount,0);
        Packet packet = writePacket(FINGERPRINT_COMMANDPACKET, payload,2);
        HashMap<String,Object> data = new HashMap<>();
        while (true){
            if (packet==null){
                packet = writePacket(FINGERPRINT_COMMANDPACKET,payload,2);
                break;
            }
            if (packet.packetType != FINGERPRINT_ACKPACKET ) {
                data.put("message","The received packet is no ack packet!");
                data.put("status",0);
                break;
            }

            //Found template
            if (packet.packetPayload[0] == FINGERPRINT_OK ) {
                int positionNumber = packet.packetPayload[1] << 8;
                positionNumber = positionNumber | packet.packetPayload[2];

                int accuracyScore = packet.packetPayload[3]<< 8;
                accuracyScore = accuracyScore | packet.packetPayload[4];

                data.put("message","Found template");
                data.put("status",1);
                data.put("position",positionNumber);
                data.put("accuracy",accuracyScore);
                callback.subProcedureFinished(data);
                break;
            }

            else if ( packet.packetPayload[0] == FINGERPRINT_ERROR_COMMUNICATION ) {
                data.put("message","Com error");
                data.put("status",0);
                break;
            }

            //Did not find a matching template
            else if ( packet.packetPayload[0] == FINGERPRINT_ERROR_NOTEMPLATEFOUND ) {
                data.put("message","No template");
                data.put("status",2);
                callback.subProcedureFinished(null);
                break;
            } else{
                data.put("message","UK error");
                data.put("status",0);
                break;
            }
        }
        //callback.subProcedureFinished(data);
    }



    public void compareCharacteristics(ProcedureCallback callback) throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        payload[0] = FINGERPRINT_COMPARECHARACTERISTICS;
       // writePacket(FINGERPRINT_COMMANDPACKET,payload);
    }

    public void createTemplate(ProcedureCallback callback) throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        payload[0] = FINGERPRINT_CREATETEMPLATE;
       // writePacket(FINGERPRINT_COMMANDPACKET,payload);
    }


    public void storeTemplate(int positionNumber,byte charBufferNumber,ProcedureCallback callback) throws IOException, InterruptedException {
        if(positionNumber<0x0000 || positionNumber >= 1000){
            System.out.println("The given position is invalid");
            return;
        }
        if(charBufferNumber!= FINGERPRINT_CHARBUFFER1 && charBufferNumber!=FINGERPRINT_CHARBUFFER2){
            System.out.println("The given char buffer is invalid");
            return;
        }
        byte[] payload = new byte[4];
        payload[0] = FINGERPRINT_STORETEMPLATE;
        payload[1] = charBufferNumber;
        payload[2] = rightShift(positionNumber,8);
        payload[3] = rightShift(positionNumber,0);
       // writePacket(FINGERPRINT_COMMANDPACKET,payload);
    }

    public void getTemplateIndex(int page) throws IOException, InterruptedException {
        if(page<0 || page>3){
            System.out.println("The given index page is invalid");
            return;
        }
        byte[] payload = new byte[2];
        payload[0] = FINGERPRINT_TEMPLATEINDEX;
        payload[1] = (byte)page;
      //  writePacket(FINGERPRINT_COMMANDPACKET,payload);
    }

    String byteToString(byte b){
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%02X",b));
        return sb.toString();
    }

    synchronized Packet readPacket(byte[] response,int sig)throws IOException{
        Packet packet = null;
        if(response[0]!=FINGERPRINT_STARTCODE[0] || response[1] != FINGERPRINT_STARTCODE[1]){
            //Toast.makeText(activity, "The packet does not begin with valid header!", Toast.LENGTH_SHORT).show();
            System.out.println("The packet does not begin with valid header!");
            return null;
        }
        short packetLength = (short)((((int)response[7]) << 8) | response[8]);
        if(response.length < 9+packetLength){
           // Toast.makeText(activity, "Invalid length of packet", Toast.LENGTH_SHORT).show();
            System.out.println("Invalid length of packet");
            return null;
        }
        short checksum = (short)(response[6] + response[7] + response[8]);
        byte[] payload = new byte[packetLength];
        for(int j=9;j<(9 + packetLength -2);j++){
            payload[j-9] = response[j];
            checksum += response[j];
        }

        if(checksum != (short)(response[10]+response[11])){
            //Toast.makeText(activity, "Checksum failure.", Toast.LENGTH_SHORT).show();
            System.out.println("Checksum failure");
            return null;
        }
        packet = new Packet();
        packet.packetPayload = payload;
        packet.packetType = response[6];

        return packet;
    }

    void toast(String text){
        //Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }


    public void readImage(ProcedureCallback callback) throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        payload[0] = FINGERPRINT_READIMAGE;
        int flood = 0;
        Packet pack = writePacket(FINGERPRINT_COMMANDPACKET, payload,0);
        while(true){
            flood++;
            System.out.println(flood);
            if(flood>200){
                break;
            }
            if(pack==null){
                pack = writePacket(FINGERPRINT_COMMANDPACKET,payload,0);
                continue;
            }
            if(pack.packetType != FINGERPRINT_ACKPACKET){
                System.out.println("The receieved packet is not ack packet!");
                break;
            }
            if(pack.packetPayload[0] == FINGERPRINT_OK){
                callback.subProcedureFinished(new HashMap<>());
                break;
            }else if(pack.packetPayload[0]==FINGERPRINT_ERROR_COMMUNICATION){
                System.out.println("Communication error");
                break;
            }else if(pack.packetPayload[0] == FINGERPRINT_ERROR_READIMAGE){
                System.out.println("Could not read image");
                break;
            }
            pack = writePacket(FINGERPRINT_COMMANDPACKET,payload,0);
        }
        callback.subProcedureFinished(new HashMap<>());
    }



    public void getTemplateCount(Handler handler){
        byte[] payload = new byte[1];
        payload[0] = FINGERPRINT_TEMPLATECOUNT;
    }




}



interface Dequeue{
    void dequeue();
}


