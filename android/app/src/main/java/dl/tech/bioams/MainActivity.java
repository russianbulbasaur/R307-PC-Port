package dl.tech.bioams;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("app",Context.MODE_PRIVATE);
        int userMode = prefs.getInt("usermode",0);
        Toast.makeText(this, String.valueOf(userMode), Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this,Login.class);
        if(userMode==1){
            intent = new Intent(this,UserMode.class);
        }
        startActivity(intent);
        finish();
    }
}