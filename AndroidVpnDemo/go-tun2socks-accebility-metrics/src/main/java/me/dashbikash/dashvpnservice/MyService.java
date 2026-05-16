package me.dashbikash.dashvpnservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class MyService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Run background task in a new thread
        new Thread(() -> {
            Log.d("MyService", "Background task running...");
            // Your logic here (e.g., periodic logging, data sync)
        }).start();

        return START_STICKY; // Restarts service if system kills it
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // For simp/ le background services, binding is not required
    }
}
