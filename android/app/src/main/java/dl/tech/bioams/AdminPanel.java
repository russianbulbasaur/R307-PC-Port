package dl.tech.bioams;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import dl.tech.bioams.ui.adminFragments.AdminOptions;
public class AdminPanel extends AppCompatActivity {



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_panel);
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction().add(R.id.container,AdminOptions.newInstance()).commitNow();
    }
}


