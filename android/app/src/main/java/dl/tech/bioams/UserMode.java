package dl.tech.bioams;

import android.annotation.SuppressLint;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;

import dl.tech.bioams.databinding.ActivityUserModeBinding;
import dl.tech.bioams.FR.FacialRecognition;
import dl.tech.bioams.models.User;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class UserMode extends AppCompatActivity implements View.OnClickListener{
    private View mContentView;
    private ActivityUserModeBinding binding;

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
        setupClickListeners();
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

//        Intent intent = new Intent(this, FacialRecognition.class);
//        startActivity(intent);
    }

    void checkout(){

    }

    void adminMode(){
        AlertDialog.Builder ab = new AlertDialog.Builder(this);
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
        ab.setView(view);
        AlertDialog ad = ab.create();
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
}