package dl.tech.bioams.ui.loginFragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import dl.tech.bioams.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RegisterUser#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RegisterUser extends Fragment {

    private boolean isFingerprintSet = false;
    public RegisterUser() {
        // Required empty public constructor
    }


    public static RegisterUser newInstance(String param1, String param2) {
        return new RegisterUser();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_register_user, container, false);
    }
}