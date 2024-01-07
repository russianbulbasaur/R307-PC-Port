package dl.tech.bioams;

import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER1;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CONVERTIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_READIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_SEARCHTEMPLATE;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayDeque;
import java.util.Base64;

import dl.tech.bioams.R307.Constants;
import dl.tech.bioams.R307.CustomProber;
import dl.tech.bioams.R307.FingerprintService;
import dl.tech.bioams.R307.SerialListener;
import dl.tech.bioams.R307.SerialSocket;
import dl.tech.bioams.databinding.ActivityUserModeBinding;
import dl.tech.bioams.db.DatabaseHelper;
import dl.tech.bioams.models.Procedure;
import dl.tech.bioams.models.User;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class UserMode extends AppCompatActivity implements View.OnClickListener, ServiceConnection, SerialListener {
    private View mContentView;
    private ActivityUserModeBinding binding;

    private DatabaseHelper helper;

    private BroadcastReceiver usbPermissionReciever;

    private enum Connected { False, Pending, True }
    private UsbSerialPort usbSerialPort;
    private Connected connected = Connected.False;
    private FingerprintService service;
    private Procedure procedure;
    private AlertDialog.Builder alertDialogBuilder;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mContentView = binding.fullscreenContent;
        SharedPreferences prefs = getSharedPreferences("app",Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putInt("usermode",1);
        prefsEditor.apply();
        startService(new Intent(this, FingerprintService.class));
        bindService(new Intent(this, FingerprintService.class), this, Context.BIND_AUTO_CREATE);
        init();
        setupClickListeners();
    }

    void init(){
        usbPermissionReciever = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(Constants.INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
        helper = new DatabaseHelper(this,"ams.db");
    }

    void setupClickListeners(){
        MaterialCardView checkInCard = findViewById(R.id.checkInCard);
        MaterialCardView checkoutCard = findViewById(R.id.checkoutCard);
        MaterialCardView adminMode = findViewById(R.id.adminMode);
        checkoutCard.setOnClickListener(this);
        checkInCard.setOnClickListener(this);
        adminMode.setOnClickListener(this);
    }

    void checkin(){
        log("in checkin");
        search();
    }

    void checkout(){

    }

    void adminMode(){
        alertDialogBuilder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.adminmodedialog,null,false);
        TextInputEditText passwordTF = view.findViewById(R.id.passwordtf);
        view.findViewById(R.id.confirm_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String password = passwordTF.getText().toString().trim();
                if(password.isEmpty()){
                    passwordTF.setError("Required");
                    return;
                }
                try{
                    User user = getAdminUser();
                    if(user.password.trim().equals(password)){
                        Toast.makeText(UserMode.this, "Success", Toast.LENGTH_SHORT).show();
                        SharedPreferences prefs = getSharedPreferences("app",Context.MODE_PRIVATE);
                        SharedPreferences.Editor prefsEditor = prefs.edit();
                        prefsEditor.putInt("usermode",0);
                        prefsEditor.apply();
                        return;
                    }
                    passwordTF.setError("Password does not match");
                }catch (Exception e){
                    Toast.makeText(UserMode.this, "Serialization error", Toast.LENGTH_SHORT).show();
                }
            }
        });
        alertDialogBuilder.setView(view);
        AlertDialog ad = alertDialogBuilder.create();
        ad.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        ad.show();
    }

    User getAdminUser() throws IOException, ClassNotFoundException {
        SharedPreferences prefs = getSharedPreferences("app",Context.MODE_PRIVATE);
        ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(prefs.getString("user","")));
        ObjectInputStream ois = new ObjectInputStream(bis);
        User user = (User) ois.readObject();
        ois.close();
        return user;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.checkInCard:
                checkin();
                break;
            case R.id.checkoutCard:
                checkout();
                break;
            case R.id.adminMode:
                adminMode();
                break;
        }
    }


    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        stopService(new Intent(this, FingerprintService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            startService(new Intent(this, FingerprintService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(usbPermissionReciever, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB));
    }

    @Override
    public void onPause() {
        unregisterReceiver(usbPermissionReciever);
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Toast.makeText(this, "connected", Toast.LENGTH_SHORT).show();
        service = ((FingerprintService.FingerprintServiceBinder)binder).getService();
        service.attach(this);
        connect(null);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }


    void searchUserInDatabase(int position){
        SQLiteDatabase sqLiteDatabase = helper.getReadableDatabase();
        Cursor cursor = sqLiteDatabase.query("users",new String[]{"name","user_id"},"fingerprint_id=?",new String[]{String.valueOf(position)},null,null,null);
        cursor.moveToFirst();
        alertDialogBuilder = new AlertDialog.Builder(this);
        if(cursor.getCount()==0){
            alertDialogBuilder.setMessage("No fingerprint found");
            alertDialogBuilder.setCancelable(false);
            alertDialogBuilder.setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                    dialogInterface.dismiss();
                   search();
                }
            });
            alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                    dialogInterface.dismiss();
                }
            });
        }
        alertDialogBuilder.setMessage("Hey,"+cursor.getString(0));
        alertDialogBuilder.create().show();
    }


    void dismissDialog(){
        alertDialog.dismiss();
        alertDialog.cancel();
    }

    void makeDialog(View view,boolean cancelable){
        alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setCancelable(cancelable);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void search()  {
        makeDialog(getLayoutInflater().inflate(R.layout.place_finger,null),false);
        procedure = new Procedure();
        procedure.handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                Bundle data = msg.getData();
                switch (data.getByte("nextSubprocess")){
                    case FINGERPRINT_READIMAGE:
                        makeDialog(getLayoutInflater().inflate(R.layout.place_finger,null),false);
                        break;
                    case FINGERPRINT_CONVERTIMAGE:
                        dismissDialog();
                        makeDialog(getLayoutInflater().inflate(R.layout.searching,null),false);
                        break;
                    case (byte)0xff:
                        dismissDialog();
                        if(data.getInt("found")==0){
                            Toast.makeText(getApplicationContext(), "User not found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        searchUserInDatabase(data.getInt("position"));
                        break;
                }
            }
        };
        procedure.currentSubProcedure = 0;
        procedure.subProcedures = new byte[]{
                FINGERPRINT_READIMAGE,
                FINGERPRINT_CONVERTIMAGE,
                FINGERPRINT_SEARCHTEMPLATE
        };
        procedure.name = "search";
        process();
    }


    public void process(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = null;
                updateSubprocedure(bundle,(byte)0);
                for (int i = 0; i < procedure.subProcedures.length; i++) {
                    try {
                        switch (i) {
                            case 0:
                                status("Scanning....");
                                bundle = service.readImage();
                                updateSubprocedure(bundle, procedure.subProcedures[1]);
                                break;
                            case 1:
                                status("Converting...");
                                bundle = service.convertImage(FINGERPRINT_CHARBUFFER1);
                                updateSubprocedure(bundle, procedure.subProcedures[2]);
                                break;
                            case 2:
                                status("Searching....");
                                bundle = service.searchTemplate(FINGERPRINT_CHARBUFFER1, 0, -1, 1000);
                                System.out.println(bundle.getInt("position"));
                                updateSubprocedure(bundle,(byte)0xff);
                                break;
                        }
                        procedure.currentSubProcedure += 1;
                    } catch (Exception e) {
                        System.out.println(e.toString());
                    }
                }
            }
        }).start();
    }



    private void updateSubprocedure(Bundle bundle,byte nextSubProcedure) {
        Message message = new Message();
        if(bundle!=null) {
            bundle.putByte("nextSubprocess", nextSubProcedure);
            message.setData(bundle);
            procedure.handler.sendMessage(message);
        }
    }


    void log(String s){
        System.out.println(s);
    }




    private void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        UsbManager usbManager;
        usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        usbSerialPort = driver.getPorts().get(0);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            try {
                usbSerialPort.setParameters(57600, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (UnsupportedOperationException e) {
                status("Setting serial parameters failed: " + e.getMessage());
            }
            SerialSocket socket = new SerialSocket(this.getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        if(service!=null)
            service.disconnect();
        usbSerialPort = null;
    }


    private void receive(ArrayDeque<byte[]> datas) {
        for (byte[] data : datas) {
            for(byte b:data){
                service.response[service.i] = b;
                service.i++;
            }
        }
    }

    void status(String str) {
        System.out.println(str);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}