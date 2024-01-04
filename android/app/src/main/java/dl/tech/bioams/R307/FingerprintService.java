package dl.tech.bioams.R307;

import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ACKPACKET;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER1;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER2;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_COMMANDPACKET;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_COMPARECHARACTERISTICS;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CONVERTIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CREATETEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_COMMUNICATION;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_NOTEMPLATEFOUND;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_NOTMATCHING;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_ERROR_READIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_GETSYSTEMPARAMETERS;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_OK;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_READIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_SEARCHTEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_STARTCODE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_STORETEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_TEMPLATECOUNT;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_TEMPLATEINDEX;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;


import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import dl.tech.bioams.R;
import dl.tech.bioams.models.Packet;
import dl.tech.bioams.models.User;
import dl.tech.bioams.ui.adminFragments.Users;

public class FingerprintService extends Service implements SerialListener {

    private Handler mainHandler;
    private IBinder binder;
    private ArrayDeque<QueueItem> queue1, queue2;
    private final QueueItem lastRead;
    byte[] gAddress = {(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff};

    private SerialSocket socket;
    public byte[] response = new byte[40];
    public int i = 0;
    private SerialListener listener;
    private boolean connected;
    public class FingerprintServiceBinder extends Binder {
        public FingerprintService getService() { return FingerprintService.this; }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private static class QueueItem {
        QueueType type;
        ArrayDeque<byte[]> datas;
        Exception e;

        QueueItem(QueueType type) { this.type=type; if(type==QueueType.Read) init(); }
        QueueItem(QueueType type, Exception e) { this.type=type; this.e=e; }
        QueueItem(QueueType type, ArrayDeque<byte[]> datas) { this.type=type; this.datas=datas; }

        void init() { datas = new ArrayDeque<>(); }
        void add(byte[] data) { datas.add(data); }
    }

    public FingerprintService() {
       mainHandler = new Handler(Looper.getMainLooper());
        binder = new FingerprintServiceBinder();
        queue1 = new ArrayDeque<>();
        queue2 = new ArrayDeque<>();
        lastRead = new QueueItem(QueueType.Read);
    }






    @Override
    public void onDestroy() {
        disconnect();
        cancelNotification();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        connected = false;
        cancelNotification();
        if(socket != null) {
            socket.disconnect();
            socket = null;
        }
    }


    public void write(byte[] data) throws IOException {
        if(!connected)
            throw new IOException("not connected");
        socket.write(data);
    }


    private void cancelNotification() {
        stopForeground(true);
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }
        Intent disconnectIntent = new Intent()
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, flags);
        PendingIntent restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent,  flags);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(socket != null ? "Connected to "+socket.getName() : "Background Service")
                .setContentIntent(restartPendingIntent)
                .setOngoing(true);
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        Notification notification = builder.build();
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }


    public void attach(SerialListener listener) {
        if(Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        cancelNotification();
        synchronized (this) {
            this.listener = listener;
        }
        for(QueueItem item : queue1) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.datas); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        for(QueueItem item : queue2) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.datas); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        queue1.clear();
        queue2.clear();
    }

    public void detach() {
        if(connected)
            createNotification();
        listener = null;
    }




    public void onSerialConnect() {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnect();
                        } else {
                            queue1.add(new QueueItem(QueueType.Connect));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Connect));
                }
            }
        }
    }


    public void onSerialConnectError(Exception e) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnectError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.ConnectError, e));
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.ConnectError, e));
                    disconnect();
                }
            }
        }
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) { throw new UnsupportedOperationException(); }

    /**
     * reduce number of UI updates by merging data chunks.
     * Data can arrive at hundred chunks per second, but the UI can only
     * perform a dozen updates if receiveText already contains much text.
     *
     * On new data inform UI thread once (1).
     * While not consumed (2), add more data (3).
     */
    public void onSerialRead(byte[] data) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    boolean first;
                    synchronized (lastRead) {
                        first = lastRead.datas.isEmpty(); // (1)
                        lastRead.add(data); // (3)
                    }
                    if(first) {
                        mainHandler.post(() -> {
                            ArrayDeque<byte[]> datas;
                            synchronized (lastRead) {
                                datas = lastRead.datas;
                                lastRead.init(); // (2)
                            }
                            if (listener != null) {
                                listener.onSerialRead(datas);
                            } else {
                                queue1.add(new QueueItem(QueueType.Read, datas));
                            }
                        });
                    }
                } else {
                    if(queue2.isEmpty() || queue2.getLast().type != QueueType.Read)
                        queue2.add(new QueueItem(QueueType.Read));
                    queue2.getLast().add(data);
                }
            }
        }
    }

    public void onSerialIoError(Exception e) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                   mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onSerialIoError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.IoError, e));
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.IoError, e));
                    disconnect();
                }
            }
        }
    }


    byte rightShift(int x,int n){
        return (byte)((x >> n) & 0xff);
    }

    synchronized byte[] createPacket(byte packetType,byte[] payload){
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
        short packetLength = (short)(payload.length + 2);
        byte packetLengthOne = rightShift(packetLength,8);
        byte packetLengthTwo = rightShift(packetLength,0);
        packet[7] = packetLengthOne;
        packet[8] = packetLengthTwo;
        short packetChecksum = 0;
        short dummyAdder = (short)packetType;
        dummyAdder &= 0x00ff;
        packetChecksum += dummyAdder;
        dummyAdder = (short) rightShift(packetLength,8);
        dummyAdder &= 0x00ff;
        packetChecksum += dummyAdder;
        dummyAdder = (short) rightShift(packetLength,0);
        dummyAdder &= 0x00ff;
        packetChecksum += dummyAdder;
        int i = 9;
        for (byte b : payload) {
            packet[i] = b;
            dummyAdder = (short) b;
            dummyAdder &= 0x00ff;
            packetChecksum += dummyAdder;
            i++;
        }
        byte checksumOne = rightShift(packetChecksum,8);
        byte checksumTwo = rightShift(packetChecksum,0);
        packet[i] = checksumOne;
        packet[i+1] = checksumTwo;
        byte[] finalPacket = new byte[i+2];
        System.arraycopy(packet, 0, finalPacket, 0, finalPacket.length);
        return finalPacket;
    }



    public Bundle convertImage(byte charBufferNumber) throws IOException, InterruptedException {
        byte[] payload = new byte[2];
        Bundle bundle = new Bundle();
        payload[0] = FINGERPRINT_CONVERTIMAGE;
        payload[1] = charBufferNumber;
        while(true) {
            i = 0;
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    System.out.println("Converted image");
                    bundle.putBoolean("status",true);
                    return bundle;
                }
            }
        }
    }


    int bitAtPosition(int n,int p){
        int twoP = 1 << p;
        int result = n & twoP;
        return (result>0)?1:0;
    }


    boolean[] getTemplateIndex(byte page) throws IOException, InterruptedException {
        Bundle bundle = new Bundle();
        if(page<0 || page>3){
            System.out.println("The given index page is invalid");
            return new boolean[1];
        }
        byte[] payload = new byte[2];
        payload[0] = FINGERPRINT_TEMPLATEINDEX;
        payload[1] = page;
        while(true) {
            i = 0;
            System.out.println("sent : "+TextUtil.toHexString(createPacket(FINGERPRINT_COMMANDPACKET, payload)));
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println("Response : "+TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    ArrayList<Boolean> templateIndex = new ArrayList<Boolean>();
                    for(int i=1;i<packet.packetPayload.length;i++){
                        for(int p=0;p<8;p++){
                            boolean positionIsUsed = (bitAtPosition(packet.packetPayload[i],p)==1);
                            templateIndex.add(positionIsUsed);
                        }
                    }
                    boolean[] tempArray = new boolean[templateIndex.size()];
                    int i=0;
                    for(Boolean b:templateIndex){
                        tempArray[i] = b;
                        i++;
                    }
                    return tempArray;
                }
            }
        }
    }


    public Bundle searchTemplate(byte charBufferNumber,int positionStart,int count,int storageCap) throws IOException, InterruptedException {
        Bundle bundle = new Bundle();
        int templatesCount = 0;
        if(count>0){
            templatesCount = count;
        }else{
            templatesCount = storageCap;
        }
        byte[] payload = new byte[6];
        payload[0] = FINGERPRINT_SEARCHTEMPLATE;
        payload[1] = charBufferNumber;
        payload[2] = rightShift(positionStart,8);
        payload[3] = rightShift(positionStart,0);
        payload[4] = rightShift(templatesCount,8);
        payload[5] = rightShift(templatesCount,0);
        while(true) {
            i = 0;
            System.out.println("Test sent : "+TextUtil.toHexString(createPacket(FINGERPRINT_COMMANDPACKET, payload)));
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    short positionNumber = packet.packetPayload[1];
                    positionNumber <<= 8;
                    positionNumber |= packet.packetPayload[2];
                    bundle.putShort("position",positionNumber);
                    short accuracy = packet.packetPayload[3];
                    accuracy <<= 8;
                    accuracy |= packet.packetPayload[4];
                    bundle.putShort("accuracy",accuracy);
                    System.out.println("Found template at "+positionNumber);
                    bundle.putBoolean("status",true);
                    bundle.putInt("found",1);
                    return bundle;
                }
                else if(packet.packetPayload[0] == FINGERPRINT_ERROR_NOTEMPLATEFOUND){
                    System.out.println("Not found");
                    bundle.putBoolean("status",true);
                    bundle.putInt("found",0);
                    return bundle;
                }
            }
        }
    }



    public Bundle compareCharacteristics() throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        Bundle bundle = new Bundle();
        payload[0] = FINGERPRINT_COMPARECHARACTERISTICS;
        while(true) {
            i = 0;
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    short accuracy = packet.packetPayload[1];
                    accuracy <<= 8;
                    accuracy |= packet.packetPayload[2];
                    System.out.println("Read fingerprint successfully\n");
                    bundle.putShort("accuracy",accuracy);
                    bundle.putInt("match",1);
                    bundle.putBoolean("status",true);
                    return bundle;
                }else if(packet.packetPayload[0]==FINGERPRINT_ERROR_NOTMATCHING){
                    bundle.putInt("match",0);
                    return bundle;
                }
            }
        }
    }

    public Bundle createTemplate() throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        Bundle bundle = new Bundle();
        payload[0] = FINGERPRINT_CREATETEMPLATE;
        while(true) {
            i = 0;
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    System.out.println("Template created\n");
                    bundle.putBoolean("status",true);
                    return bundle;
                }
            }
        }
    }


    //this function can be used for other parameters too
    public Bundle getStorageCapacity() throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        Bundle bundle = new Bundle();
        payload[0] = FINGERPRINT_GETSYSTEMPARAMETERS;
        while(true) {
            i = 0;
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    System.out.println("Read fingerprint successfully\n");
                    bundle.putBoolean("status",true);
                    return bundle;
                }
            }
        }
    }


    public Bundle storeTemplate(int positionNumber,byte charBufferNumber) throws IOException, InterruptedException {
        byte[] payload = new byte[4];
        Bundle bundle = new Bundle();
        payload[0] = FINGERPRINT_STORETEMPLATE;
        payload[1] = charBufferNumber;
        payload[2] = rightShift(positionNumber,8);
        payload[3] = rightShift(positionNumber,0);
        if(positionNumber<0x0000 || positionNumber >=1000){
            System.out.println("Invalid position number");
            return bundle;
        }
        while(true) {
            i = 0;
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    System.out.println("Stored successfully\n");
                    bundle.putBoolean("status",true);
                    return bundle;
                }
            }
        }
    }



    synchronized Packet readPacket(byte[] response) {
        Packet packet = null;
        if(i<12){
            return null;
        }
        if(response[0]!=FINGERPRINT_STARTCODE[0] || response[1] != FINGERPRINT_STARTCODE[1]){
            System.out.println("The packet does not begin with valid header!");
            return null;
        }
        short dummyAddr = response[7];
        dummyAddr <<= 8;
        dummyAddr |= response[8];
        short packetLength = dummyAddr;
        if(i<packetLength+9){
            return null;
        }
        byte packetType = response[6];
        dummyAddr = response[7];
        dummyAddr += response[8];
        short checksum = (short)(dummyAddr + packetType);
        System.out.println(checksum);
        byte[] payload = new byte[packetLength];
        for(int j=9;j<(9 + packetLength -2);j++){
            payload[j-9] = response[j];
            checksum += response[j];
            System.out.println(checksum);
        }
        System.out.println("Calculated  : "+checksum);
        short receivedChecksum = response[i-2];
        receivedChecksum <<= 8;
        receivedChecksum |= response[i-1];
        System.out.println("Received : "+receivedChecksum);
        if(checksum != receivedChecksum){
            System.out.println("Checksum failure");
            return null;
        }
        packet = new Packet();
        packet.packetPayload = payload;
        packet.packetType = response[6];
        return packet;
    }



    public Bundle readImage() throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        Bundle bundle = new Bundle();
        payload[0] = FINGERPRINT_READIMAGE;
        while(true) {
            i = 0;
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    System.out.println("Read fingerprint successfully\n");
                    bundle.putBoolean("status",true);
                    return bundle;
                }
            }
        }
    }



    public Bundle getTemplateCount() throws IOException, InterruptedException {
        byte[] payload = new byte[1];
        Bundle bundle = new Bundle();
        payload[0] = FINGERPRINT_TEMPLATECOUNT;
        while(true) {
            i = 0;
            write(createPacket(FINGERPRINT_COMMANDPACKET, payload));
            Thread.sleep(2000);
            System.out.println(TextUtil.toHexString(response));
            Packet packet = readPacket(response);
            if(packet!=null) {
                if(packet.packetType!=FINGERPRINT_ACKPACKET){
                    continue;
                }
                if(packet.packetPayload[0] == FINGERPRINT_OK){
                    short templateCount = packet.packetPayload[1];
                    templateCount <<= 8;
                    templateCount |= packet.packetPayload[2];
                    System.out.println("Templates : "+templateCount);
                    bundle.putShort("count",templateCount);
                    return bundle;
                }
            }
        }
    }

}



