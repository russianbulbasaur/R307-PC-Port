package dl.tech.bioams.ui.adminFragments;

import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CONVERTIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_READIMAGE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import dl.tech.bioams.R;
import dl.tech.bioams.R307.FingerprintInterface;
import dl.tech.bioams.api.CustomVolleyError;
import dl.tech.bioams.api.CustomVolleyInterface;
import dl.tech.bioams.api.CustomVolleyRequest;
import dl.tech.bioams.db.DatabaseHelper;
import dl.tech.bioams.models.AMSUser;
import dl.tech.bioams.models.User;

public class Users extends Fragment implements Response.Listener<Bundle>, CustomVolleyInterface {
    private AlertDialog alertDialog;
    private String url;
    private SharedPreferences prefs;
    private User user;
    private RecyclerView userListView;
    private DatabaseHelper helper;
    private SearchView searchView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        helper = new DatabaseHelper(getContext(),"ams.db");
        prefs = getContext().getSharedPreferences("app",Context.MODE_PRIVATE);
        url = prefs.getString("url","");
        if(url.isEmpty()){
            Toast.makeText(getContext(), "Url not set", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            user = loadUserObject();
            importUsers();
        } catch (Exception e){
            Toast.makeText(getContext(),e.toString(),Toast.LENGTH_LONG).show();
        }
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
            cursor.moveToNext();
        }
        cursor.close();
        return users;
    }



    void addUserToDatabase(AMSUser user){
        SQLiteDatabase database = helper.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("user_id",user.userid);
        contentValues.put("name",user.name);
        contentValues.put("fingerprint_id",user.fingerprint);
        database.insert("users",null,contentValues);
        database.close();
    }

    @Override
    public void onError(Bundle bundle) {
        alertDialog.cancel();
        alertDialog.dismiss();
    }

    @Override
    public void onResponse(Bundle response) {
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
            UserAdapter adapter = new UserAdapter(users);
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
}



class UserAdapter extends RecyclerView.Adapter<UserViewHolder>{
    private ArrayList<AMSUser> users;
    UserAdapter(ArrayList<AMSUser> parsedUsers){
        this.users = parsedUsers;
    }



    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.usertile,parent,false);
        return new UserViewHolder(v, parent.getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        int fingerprint_id = users.get(position).fingerprint;
        if(fingerprint_id<0){
            holder.fingerprintButton.setBackgroundDrawable(Drawable.createFromPath("/res/drawable/fingerprintbuttonback.xml"));
        }
        holder.userid = users.get(position).userid;
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
    String userid;
    public UserViewHolder(@NonNull View itemView, Context context) {
        super(itemView);
        this.context = context;
        tv = itemView.findViewById(R.id.userTile);
        fingerprintButton = itemView.findViewById(R.id.fingerprintButton);
        fingerprintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    enrollFingerprint();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    void enrollFingerprint() throws IOException {
        Handler enrollHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                byte sub = msg.getData().getByte("subprocess");
                String instruct = "";
                switch (sub){
                    case 0x01:
                        instruct = "Scanning....";
                        break;
                    case 0x02:
                        instruct = "Converting....";
                        break;
                    case 0x04:
                        instruct = "Searching.....";
                        break;
                    case 0x03:
                        instruct = "Comparing prints.....";
                        break;
                    case 0x05:
                        instruct = "Creating template...";
                        break;
                    case 0x06:
                        instruct = "Storing template....";
                        break;
                }
                instruction.setText(instruct);
            }
        };
        makeDialog();
        FingerprintInterface fingerprintInterface = new FingerprintInterface();
        fingerprintInterface.enroll(userid,context,enrollHandler);
    }

    public void makeDialog(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        LayoutInflater li = LayoutInflater.from(context);
        View view = li.inflate(R.layout.fingerprint_scan,null);
        instruction = view.findViewById(R.id.instruction);
        alertDialog.setView(view);
        alertDialog.create().show();
    }
}