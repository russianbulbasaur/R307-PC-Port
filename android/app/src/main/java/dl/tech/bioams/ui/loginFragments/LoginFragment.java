package dl.tech.bioams.ui.loginFragments;


import static dl.tech.bioams.R307.DataCodes.*;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.LinearLayoutCompat;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dl.tech.bioams.R;
import dl.tech.bioams.R307.Fingerprint;
import dl.tech.bioams.api.CustomVolleyError;
import dl.tech.bioams.api.CustomVolleyInterface;
import dl.tech.bioams.api.CustomVolleyRequest;
import dl.tech.bioams.models.Packet;
import dl.tech.bioams.models.Procedure;
import dl.tech.bioams.models.ProcedureCallback;
import dl.tech.bioams.models.User;

public class LoginFragment extends Fragment implements View.OnClickListener, CustomVolleyInterface {
    AppCompatButton loginButton;
    private TextInputEditText usernameTf;
    private TextInputEditText passwordTf;
    private CustomVolleyError customVolleyError;
    private RequestQueue queue;
    private String url;
    private Set<String> requestSet;
    private User user;
    private LinearLayoutCompat loginCard;
    private LinearLayoutCompat workspaceCard;
    private AppCompatButton workspaceButton;
    private SharedPreferences prefs;
    private TextInputEditText workspaceTextField;

    public static LoginFragment newInstance() {
        return new LoginFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    void initProcess(){
        prefs = getContext().getSharedPreferences("app",Context.MODE_PRIVATE);
        url = prefs.getString("url","");
        if (url.trim().isEmpty()) {
            workspaceCard.setVisibility(View.VISIBLE);
            workspaceButton.setOnClickListener(this);
        } else {
            loginCard.setVisibility(View.VISIBLE);
            loginButton.setOnClickListener(this);
        }
        requestSet = new HashSet<String>();
        queue = Volley.newRequestQueue(getContext());
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration config = getResources().getConfiguration();
        int layout;
        if(config.orientation == Configuration.ORIENTATION_LANDSCAPE){
            layout = R.layout.fragment_login_landscape;
        }else{
            layout = R.layout.fragment_login;
        }
        View view = inflater.inflate(layout, container, false);
        loginButton = view.findViewById(R.id.loginButton);
        loginCard = view.findViewById(R.id.loginCard);
        workspaceCard = view.findViewById(R.id.workspaceCard);
        usernameTf = view.findViewById(R.id.usernametf);
        passwordTf = view.findViewById(R.id.passwordtf);
        workspaceButton = view.findViewById(R.id.workspaceButton);
        workspaceTextField = view.findViewById(R.id.workspaceTextField);
        initProcess();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.workspaceButton:
                String workspaceUrl = workspaceTextField.getEditableText().toString().trim();
                if(workspaceUrl.isEmpty()){
                    workspaceTextField.setError("Required");
                    return;
                }
                pingWorkspace(workspaceUrl);
                break;
            case R.id.loginButton:
                preLogin();
                break;
        }
    }





    public void preLogin(){
        String username = "";
        String password = "";
        Editable usernameText = usernameTf.getText();
        Editable passwordText = passwordTf.getText();
        if(usernameText!=null){
            username = usernameText.toString().trim();
        }
        if(passwordText!=null){
            password = passwordText.toString().trim();
        }
        if(username.isEmpty() || password.isEmpty()){
            if(username.isEmpty()){
                usernameTf.setError("Required");
            }
            if(password.isEmpty()){
                passwordTf.setError("Required");
            }
            return;
        }
        user = new User("","","",username,password);
        login(url+getResources().getString(R.string.loginPath));
    }

    public void pingWorkspace(String workspace){
        String requestUrl = workspace+getResources().getString(R.string.pingPath);
        if(notInQueue(requestUrl)) {
            customVolleyError = new CustomVolleyError(requestUrl);
            customVolleyError.volleyInterface = this;
            CustomVolleyRequest pingRequest = new CustomVolleyRequest(Request.Method.GET, requestUrl, customVolleyError, new Response.Listener<Bundle>() {
                @Override
                public void onResponse(Bundle response) {
                    SharedPreferences.Editor prefsEditor = prefs.edit();
                    prefsEditor.putString("url",workspace);
                    loginCard.setVisibility(View.VISIBLE);
                    workspaceCard.setVisibility(View.INVISIBLE);
                }
            });
            requestSet.add(url);
            queue.add(pingRequest);
        }else{
            Toast.makeText(getContext(), "Request already in queue", Toast.LENGTH_SHORT).show();
        }
    }

    void login(String requestUrl){
        if(notInQueue(requestUrl)) {
            customVolleyError = new CustomVolleyError(requestUrl);
            customVolleyError.volleyInterface = this;
            CustomVolleyRequest loginRequest = new CustomVolleyRequest(Request.Method.POST, requestUrl, customVolleyError, new Response.Listener<Bundle>() {
                @Override
                public void onResponse(Bundle response) {
                    parseLoginResponse(response);
                }
            }){
                @Nullable
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String,String> params = new HashMap<>();
                    params.put("email",user.email.toString());
                    params.put("password",user.password.toString());
                    return params;
                }
            };
            requestSet.add(url);
            queue.add(loginRequest);
        }else{
            Toast.makeText(getContext(), "Request already in queue", Toast.LENGTH_SHORT).show();
        }
    }


    void parseLoginResponse(Bundle response){
        try{
            JSONObject jsonObject = new JSONObject(response.getString("response"));
            if(jsonObject.getString("status").equals("200")) {
                String token = jsonObject.getString("access_token");
                String name = jsonObject.getString("username");
                user = new User(token, name, "", user.email, user.password);
                SharedPreferences.Editor prefsEditor = prefs.edit();
                prefsEditor.putString("user",serializeObject(user));
                prefsEditor.apply();
                getParentFragmentManager().beginTransaction().replace(R.id.container,AdminPanel.newInstance()).commitNow();
            }
        }catch (Exception e){
            Toast.makeText(getContext(), "Parsing error", Toast.LENGTH_SHORT).show();
        }
        requestSet.remove(response.getString("url"));
    }


    boolean notInQueue(String url){
        return !requestSet.contains(url);
    }


    String serializeObject(User user){
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream ois = new ObjectOutputStream(bos);
            ois.writeObject(user);
            ois.close();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        }catch (Exception e){
            return "";
        }
    }

    @Override
    public void onError(Bundle bundle) {
        requestSet.remove(bundle.getString("url"));
        Toast.makeText(getContext(), "Error", Toast.LENGTH_SHORT).show();
    }





}