package dl.tech.bioams.ui.adminFragments;

import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER1;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER2;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_COMPARECHARACTERISTICS;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CONVERTIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CREATETEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_READIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_SEARCHTEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_STORETEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_TEMPLATECOUNT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import dl.tech.bioams.R;
import dl.tech.bioams.R307.Constants;
import dl.tech.bioams.R307.CustomProber;
import dl.tech.bioams.R307.FingerprintService;
import dl.tech.bioams.R307.SerialListener;
import dl.tech.bioams.R307.SerialSocket;
import dl.tech.bioams.api.CustomVolleyError;
import dl.tech.bioams.api.CustomVolleyInterface;
import dl.tech.bioams.api.CustomVolleyRequest;
import dl.tech.bioams.db.DatabaseHelper;
import dl.tech.bioams.models.AMSUser;
import dl.tech.bioams.models.Procedure;
import dl.tech.bioams.models.User;

public class Users extends Fragment implements Response.Listener<Bundle>, CustomVolleyInterface, ServiceConnection, SerialListener,RegisterFingerprint {
    private String url;
    private SharedPreferences prefs;
    private User user;
    private RecyclerView userListView;
    private int templateCount = 0;
    private DatabaseHelper helper;
    private SearchView searchView;
    private BroadcastReceiver usbPermissionReciever;

    private enum Connected { False, Pending, True }
    private UsbSerialPort usbSerialPort;
    private Connected connected = Connected.False;
    private FingerprintService service;
    private Procedure procedure;
    private TextView instruction;
    private AlertDialog.Builder alertDialogBuilder;
    private AlertDialog alertDialog;

    private Thread processThread;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
        if(url.isEmpty()){
            Toast.makeText(getContext(), "Url not set", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            user = loadUserObject();
        } catch (Exception e){
            Toast.makeText(getContext(),e.toString(),Toast.LENGTH_LONG).show();
        }
        getActivity().startService(new Intent(getActivity(), FingerprintService.class));
        importUsers();
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
        helper = new DatabaseHelper(getContext(),"ams.db");
        prefs = getContext().getSharedPreferences("app",Context.MODE_PRIVATE);
        url = prefs.getString("url","");
    }


    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), FingerprintService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), FingerprintService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), FingerprintService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(usbPermissionReciever, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB));
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(usbPermissionReciever);
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((FingerprintService.FingerprintServiceBinder)binder).getService();
        service.attach(this);
        connect(null);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }



    void importUsers() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setMessage("Syncing with AMS");
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setView(new ProgressBar(getContext()));
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        try{
            String requestUrl = url+getResources().getString(R.string.getAllUsers);
            RequestQueue queue = Volley.newRequestQueue(getContext());
            CustomVolleyError error = new CustomVolleyError("");
            error.volleyInterface = this;
            JSONObject order = new JSONObject();
            order.put("column","1");
            order.put("dir","asc");
            JSONArray arr = new JSONArray();
            arr.put(order);
            CustomVolleyRequest request = new CustomVolleyRequest(Request.Method.POST,requestUrl,error,this){
                @Nullable
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String,String> params = new HashMap<>();
                    params.put("order",arr.toString());
                    params.put("requestType","api");
                    params.put("start","0");
                    params.put("length","1000");
                    return params;
                }

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String,String> header = new HashMap<>();
                    header.put("Authorization","Bearer "+user.token);
                    return header;
                }
            };
            queue.add(request);
        }catch (Exception e){
            Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    User loadUserObject() throws IOException, ClassNotFoundException {
        SharedPreferences prefs = getActivity().getSharedPreferences("app",Context.MODE_PRIVATE);
        String serializedUser = prefs.getString("user","");
        System.out.println("here : "+serializedUser);
        ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(serializedUser.getBytes()));
        ObjectInputStream dis = new ObjectInputStream(bis);
        User user = (User) dis.readObject();
        dis.close();
        return user;
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_users,container,false);
        userListView = v.findViewById(R.id.userList);
        searchView = v.findViewById(R.id.search);
        return v;
    }



    @SuppressLint("UseCompatLoadingForDrawables")
    ArrayList<AMSUser> getUsers(){
        ArrayList<AMSUser> users = new ArrayList<>();
        AMSUser user;
        SQLiteDatabase reader = helper.getReadableDatabase();
        Cursor cursor = reader.query("users",new String[]{"user_id","name","fingerprint_id"},"",new String[]{},"","","");
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            user = new AMSUser(cursor.getString(0),cursor.getString(1),cursor.getInt(2));
            users.add(user);
            System.out.println(user.fingerprint);
            cursor.moveToNext();
        }
        cursor.close();
        return users;
    }


    void updateUserInDatabase(AMSUser user){
        SQLiteDatabase database = helper.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("fingerprint_id",user.fingerprint);
        Toast.makeText(getContext(), "Registered "+user.name, Toast.LENGTH_SHORT).show();
        database.update("users",contentValues,"user_id=?",new String[]{user.userid});
        database.close();
    }

    void addUserToDatabase(AMSUser user){
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query("users",new String[]{"name","user_id"},"user_id=?",new String[]{user.userid},null,null,null);
        if(cursor.getCount()==0) {
            database = helper.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put("user_id", user.userid);
            contentValues.put("name", user.name);
            contentValues.put("fingerprint_id", user.fingerprint);
            database.insert("users", null, contentValues);
            database.close();
        }
        cursor.close();
    }

    @Override
    public void onError(Bundle bundle) {
        alertDialog.cancel();
        alertDialog.dismiss();
    }

    @Override
    public void onResponse(Bundle response) {
        System.out.println(response.getString("response"));
        try {
            JSONArray arr = new JSONArray(response.getString("response"));
            AMSUser amsUser;
            JSONObject jsonObject;
            for(int i=0;i<arr.length();i++){
                jsonObject = arr.getJSONObject(i);
                amsUser = new AMSUser(jsonObject.getString("user_id"),jsonObject.getString("name"),-1);
                addUserToDatabase(amsUser);
            }
            ArrayList<AMSUser> users = getUsers();
            UserAdapter adapter = new UserAdapter(users,this);
            userListView.setAdapter(adapter);
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    for(int i=0;i<users.size();i++){
                        AMSUser user = users.get(i);
                        if(!user.name.trim().toLowerCase().startsWith(newText.toLowerCase())){
                            users.remove(i);
                            adapter.notifyItemRemoved(i);
                        }
                    }
                    return false;
                }
            });
            alertDialog.cancel();
            alertDialog.dismiss();
        } catch (JSONException e) {
            Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }
    }


    void dismissDialog(){
        if(alertDialog!=null) {
            alertDialog.dismiss();
            alertDialog.cancel();
        }
    }

    void makeDialog(View view,boolean cancelable){
        alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setCancelable(cancelable);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void enroll(AMSUser user)  {
        procedure = new Procedure();
        procedure.handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                Bundle data = msg.getData();
                switch (data.getByte("nextSubprocess")){
                    case FINGERPRINT_READIMAGE:
                        dismissDialog();
                        if(data.keySet().contains("found") && data.getInt("found")==1){
                            Toast.makeText(getContext(), "Fingerprint already registered.", Toast.LENGTH_SHORT).show();
                            processThread.interrupt();
                        }else{
                            makeDialog(getLayoutInflater().inflate(R.layout.place_finger,null),false);
                        }
                        break;
                    case FINGERPRINT_CONVERTIMAGE:
                        dismissDialog();
                        makeDialog(getLayoutInflater().inflate(R.layout.registering,null),false);
                        break;
                    case FINGERPRINT_CREATETEMPLATE:
                        if(data.getInt("match")==0){
                            dismissDialog();
                            Toast.makeText(getContext(), "The trials did not match. Please retry.", Toast.LENGTH_SHORT).show();
                            processThread.interrupt();
                        }
                        break;
                    case FINGERPRINT_STORETEMPLATE:
                        if(!data.getBoolean("status")){
                            Toast.makeText(getContext(), "Fingerprint mismatch", Toast.LENGTH_SHORT).show();
                            processThread.interrupt();
                        }
                        break;
                    case (byte)0xff:
                        dismissDialog();
                        if(!data.keySet().contains("interrupted")) {
                            user.fingerprint = templateCount;
                            updateUserInDatabase(user);
                        }
                        break;
                }
            }
        };
        procedure.subProcedures = new byte[]{
                FINGERPRINT_TEMPLATECOUNT,
                FINGERPRINT_READIMAGE,
                FINGERPRINT_CONVERTIMAGE,
                FINGERPRINT_SEARCHTEMPLATE,
                FINGERPRINT_READIMAGE,
                FINGERPRINT_CONVERTIMAGE,
                FINGERPRINT_COMPARECHARACTERISTICS,
                FINGERPRINT_CREATETEMPLATE,
                FINGERPRINT_STORETEMPLATE
        };
        procedure.currentSubProcedure = procedure.subProcedures[0];
        procedure.name = "enroll";
        process();
    }


    public void process(){
        processThread = new Thread(new Runnable() {

            @Override
            public void run() {
                Bundle bundle = null;
                try {
                    for(byte i=0;i<procedure.subProcedures.length;i++) {
                        service.i = 0;
                        switch (procedure.subProcedures[i]) {
                            case FINGERPRINT_TEMPLATECOUNT:
                                bundle = service.getTemplateCount();
                                templateCount = bundle.getShort("count");
                                updateSubprocedure(bundle,procedure.subProcedures[i+1]);
                                break;
                            case FINGERPRINT_READIMAGE:
                                status("Scanning....");
                                Thread.sleep(2500);
                                bundle = service.readImage();
                                updateSubprocedure(bundle,procedure.subProcedures[i+1]);
                                break;
                            case FINGERPRINT_CONVERTIMAGE:
                                status("Converting...");
                                bundle = service.convertImage((procedure.subProcedures[i+1]==FINGERPRINT_COMPARECHARACTERISTICS)?FINGERPRINT_CHARBUFFER2:FINGERPRINT_CHARBUFFER1);
                                updateSubprocedure(bundle,procedure.subProcedures[i+1]);
                                break;
                            case FINGERPRINT_SEARCHTEMPLATE:
                                status("Searching....");
                                bundle = service.searchTemplate(FINGERPRINT_CHARBUFFER1, 0, -1, 1000);
                                updateSubprocedure(bundle,procedure.subProcedures[i+1]);
                                break;
                            case FINGERPRINT_COMPARECHARACTERISTICS:
                                status("Comparing......");
                                bundle = service.compareCharacteristics();
                                updateSubprocedure(bundle,procedure.subProcedures[i+1]);
                                break;
                            case FINGERPRINT_CREATETEMPLATE:
                                status("Creating template....");
                                bundle = service.createTemplate();
                                updateSubprocedure(bundle,procedure.subProcedures[i+1]);
                                break;
                            case FINGERPRINT_STORETEMPLATE:
                                status("Storing.....");
                                bundle = service.storeTemplate(templateCount, FINGERPRINT_CHARBUFFER1);
                                updateSubprocedure(bundle,(byte)0xff);
                                break;
                        }
                    }
                }catch (Exception e){
                    bundle = new Bundle();
                    bundle.putInt("interrupted",1);
                    updateSubprocedure(bundle,(byte)0xff);
                }
            }
        });
        processThread.start();
    }



    private void updateSubprocedure(Bundle bundle,byte nextSubprocess) {
        Message message = new Message();
        if(bundle!=null) {
            bundle.putByte("nextSubprocess", nextSubprocess);
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
        usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
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
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), flags);
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
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
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


    @Override
    public void register(AMSUser user) {
        enroll(user);
    }
}



class UserAdapter extends RecyclerView.Adapter<UserViewHolder>{
    private final ArrayList<AMSUser> users;
    private final RegisterFingerprint registerCallback;
    UserAdapter(ArrayList<AMSUser> parsedUsers,RegisterFingerprint register){
        this.users = parsedUsers;
        this.registerCallback = register;
    }



    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.usertile,parent,false);
        return new UserViewHolder(v, parent.getContext(),registerCallback);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        int fingerprint_id = users.get(position).fingerprint;
        if(fingerprint_id<0){
            holder.fingerprintButton.setBackgroundDrawable(Drawable.createFromPath("/res/drawable/fingerprintbuttonback.xml"));
        }
        holder.user = users.get(position);
        holder.tv.setText(users.get(position).name);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }
}

class UserViewHolder extends RecyclerView.ViewHolder{
    TextView tv;
    AppCompatImageButton fingerprintButton;
    Context context;
    private TextView instruction;
    private RegisterFingerprint call;
    AMSUser user;
    public UserViewHolder(@NonNull View itemView, Context context,RegisterFingerprint callback) {
        super(itemView);
        this.context = context;
        this.call = callback;
        tv = itemView.findViewById(R.id.userTile);
        fingerprintButton = itemView.findViewById(R.id.fingerprintButton);
        fingerprintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (user.fingerprint!=-1){
                    Toast.makeText(context, user.name+" already enrolled at "+user.fingerprint, Toast.LENGTH_SHORT).show();
                    return;
                }
                callback.register(user);
            }
        });
    }
}


