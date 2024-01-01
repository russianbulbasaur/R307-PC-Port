package dl.tech.bioams.ui.adminFragments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
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
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import dl.tech.bioams.R;
import dl.tech.bioams.R307.FingerprintInterface;
import dl.tech.bioams.api.CustomVolleyError;
import dl.tech.bioams.api.CustomVolleyInterface;
import dl.tech.bioams.api.CustomVolleyRequest;
import dl.tech.bioams.db.DatabaseHelper;
import dl.tech.bioams.models.User;

public class Users extends Fragment implements Response.Listener<Bundle>, CustomVolleyInterface {
    private AlertDialog alertDialog;
    private String url;
    private SharedPreferences prefs;
    private User user;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        alertDialogBuilder.setView(new ProgressBar(getContext()));
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        try{
            String requestUrl = url+getResources().getString(R.string.getAllUsers);
            RequestQueue queue = Volley.newRequestQueue(getContext());
            CustomVolleyError error = new CustomVolleyError("");
            error.volleyInterface = this;
            JSONObject order = new JSONObject();
            order.put("column",1);
            order.put("dir","asc");
            JSONArray arr = new JSONArray();
            arr.put(order);
            CustomVolleyRequest request = new CustomVolleyRequest(Request.Method.POST,requestUrl,error,this){
                @Nullable
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String,String> params = new HashMap<>();
                    params.put("order",order.toString());
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
        return null;
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_users,container,false);
        RecyclerView rv = v.findViewById(R.id.userList);
        UserAdapter adapter = new UserAdapter(getUsers());
        rv.setAdapter(adapter);
        return v;
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    ArrayList<Map<String,String>> getUsers(){
        ArrayList<Map<String,String>> users = new ArrayList<>();
        Map<String,String> user;
        DatabaseHelper helper = new DatabaseHelper(getContext(),"ams.db");
        SQLiteDatabase reader = helper.getReadableDatabase();
        Cursor cursor = reader.query("users",new String[]{"user_id","name","fingerprint_id"},"",new String[]{},"","","");
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            user = new HashMap<>();
            user.put("id",cursor.getString(0));
            user.put("name",cursor.getString(1));
            user.put("fingerprint_id",cursor.getString(2));
            users.add(user);
            cursor.moveToNext();
        }
        return users;
    }

    @Override
    public void onError(Bundle bundle) {
        alertDialog.cancel();
        alertDialog.dismiss();
        Toast.makeText(getContext(), bundle.getString("response"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResponse(Bundle response) {
        alertDialog.cancel();
        alertDialog.dismiss();
    }
}



class UserAdapter extends RecyclerView.Adapter<UserViewHolder>{
    private ArrayList<Map<String,String>> users;
    UserAdapter(ArrayList<Map<String,String>> parsedUsers){
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
        String fingerprint_id = users.get(position).get("fingerprint_id");
        if(fingerprint_id==null || fingerprint_id.isEmpty()){
            holder.fingerprintButton.setBackgroundDrawable(Drawable.createFromPath("/res/drawable/fingerprintbuttonback.xml"));
        }
        holder.tv.setText(users.get(position).get("name"));
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
        makeDialog();
        FingerprintInterface fingerprintInterface = new FingerprintInterface();
        fingerprintInterface.enroll("",context,new Handler());
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