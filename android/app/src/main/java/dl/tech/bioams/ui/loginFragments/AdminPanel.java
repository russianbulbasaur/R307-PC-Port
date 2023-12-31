package dl.tech.bioams.ui.loginFragments;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;

import dl.tech.bioams.R;
import dl.tech.bioams.UserMode;
import dl.tech.bioams.Users;
import dl.tech.bioams.db.DatabaseHelper;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AdminPanel#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AdminPanel extends Fragment {


    public AdminPanel() {
        // Required empty public constructor
    }

    public static AdminPanel newInstance() {
        return new AdminPanel();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        int layout;
        Configuration config = getResources().getConfiguration();
        if(config.orientation == Configuration.ORIENTATION_LANDSCAPE){
            layout = R.layout.fragment_admin_panel_landscape;
        }else{
            layout = R.layout.fragment_admin_panel;
        }
        // Inflate the layout for this fragment
        View view = inflater.inflate(layout, container, false);
        GridView options = view.findViewById(R.id.options);
        ArrayList<String> arr = new ArrayList<>();
        arr.add("Import Users");
        arr.add("Register User");
        arr.add("Delete User");
        arr.add("User Mode");
        OptionsAdapter adapter = new OptionsAdapter(getContext(),0,arr,getParentFragmentManager(),getActivity());
        options.setAdapter(adapter);
        return view;
    }
}


class OptionsAdapter extends ArrayAdapter<String>{

    private ArrayList<String> options;
    private FragmentManager fm;

    private FragmentActivity parentActivity;

    public OptionsAdapter(@NonNull Context context, int resource,ArrayList<String> options,FragmentManager parsedFM,
                          FragmentActivity parsedActivity) {
        super(context, resource);
        this.fm = parsedFM;
        this.parentActivity = parsedActivity;
        this.options = options;
    }


    void importUsers(){
        fm.beginTransaction().add(R.id.container,new Users()).addToBackStack("users").commit();
    }


    void switchToUser(){
        Intent intent = new Intent(getContext(), UserMode.class);
        getContext().startActivity(intent);
        parentActivity.finish();
    }


    void registerUser(){
        fm.beginTransaction().add(R.id.container,new RegisterUser()).commit();
    }




    void titleClicked(int position){
        switch (position){
            case 0:
                importUsers();
                break;
            case 1:
                registerUser();
                break;
            case 2:
                break;
            case 3:
                switchToUser();
                break;
        }
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        OptionsHolder holder;
        if(convertView==null){
            LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = li.inflate(R.layout.optiontile,parent,false);
            holder = new OptionsHolder();
            holder.listener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    titleClicked(position);
                }
            };
            holder.option = options.get(position);
            holder.optionsText = convertView.findViewById(R.id.optionText);
            convertView.setTag(holder);
        }else{
            holder = (OptionsHolder) convertView.getTag();
        }
        holder.optionsText.setText(holder.option);
        convertView.setOnClickListener(holder.listener);
        return convertView;
    }

    @Override
    public int getCount() {
        return options.size();
    }
}

class OptionsHolder{
    String option;
    TextView optionsText;
    View.OnClickListener listener;
}