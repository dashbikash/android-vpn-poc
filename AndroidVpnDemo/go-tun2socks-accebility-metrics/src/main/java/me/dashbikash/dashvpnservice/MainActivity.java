package me.dashbikash.dashvpnservice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import me.dashbikash.dashvpnservice.security.HashUtil;

public class MainActivity extends AppCompatActivity {

    private final Context context=this;
    private static final int VPN_REQUEST_CODE = 100;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        String devID= null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            devID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        ((TextView)findViewById(R.id.tvDevID)).setText("Device ID: "+ HashUtil.hashAndroidId(devID));
        SwitchMaterial switchOne= findViewById(R.id.switch_one);
        switchOne.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked){
                Intent vpnIntent= MyVpnService.prepare(context);

                if(vpnIntent!=null){
                    activityResultLauncher.launch(vpnIntent);
                }else {
                    startService(new Intent(context, MyVpnService.class));
                }
            }else {
                stopService(new Intent(context, MyVpnService.class));
            }
        });

    }
    // 1. Register the launcher as a class variable
    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Log.i(Constants.TAG,"Starting Vpn");
                    startService(new Intent(context, MyVpnService.class));
                }
            }
    );

}