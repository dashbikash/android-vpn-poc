package me.dashbikash.dashvpnservice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    private final Context context=this;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        SwitchMaterial switchOne= findViewById(R.id.switch_one);
        switchOne.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked){
                Intent vpnIntent= MyXrayVpnService.prepare(context);

                if(vpnIntent!=null){
                    activityResultLauncher.launch(vpnIntent);
                }else {
                    startService(new Intent(context, MyXrayVpnService.class));
                }
            }else {
                stopService(new Intent(context, MyXrayVpnService.class));
            }
        });

    }
    // 1. Register the launcher as a class variable
    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Log.i(Constants.TAG,"Starting Vpn");
                    startService(new Intent(context, MyXrayVpnService.class));
                }
            }
    );

}