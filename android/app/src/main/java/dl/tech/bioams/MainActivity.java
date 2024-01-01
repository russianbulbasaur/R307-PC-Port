package dl.tech.bioams;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("app",Context.MODE_PRIVATE);
        int userMode = prefs.getInt("usermode",0);
        String user = prefs.getString("user","");
        String url = prefs.getString("url","");
        Intent intent = new Intent(this,Login.class);
        if(userMode==1){
            intent = new Intent(this,UserMode.class);
        }else if(!user.trim().isEmpty() && !url.trim().isEmpty())
        {
            intent  = new Intent(this, AdminPanel.class);
        }
        startActivity(intent);
        finish();
    }
}