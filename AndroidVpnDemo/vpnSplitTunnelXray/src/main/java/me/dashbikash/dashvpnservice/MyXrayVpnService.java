package me.dashbikash.dashvpnservice;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class MyXrayVpnService extends VpnService {

    private static final String TAG = Constants.TAG;
    private ParcelFileDescriptor vpnInterface;
    private XrayService xrayLogger;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupVpn();
        return START_STICKY;
    }

    private void setupVpn() {
        try {
            // 1. Configure the VPN interface
            Builder builder = new Builder();
            builder.setSession("XrayLoggerVPN")
                    // FIX 1: Use a /26 subnet to allow proper gateway routing
                    .addAddress("172.19.0.1", 26)
                    .addRoute("0.0.0.0", 0)
                    .addAddress("fc00::2", 64)
                    .addRoute("::", 0)
                    .addDnsServer("8.8.8.8")
                    .setMtu(1500);

            // CRITICAL: Exclude your own app so Xray's output goes to the real internet
            //builder.addDisallowedApplication(getPackageName());
            builder.addAllowedApplication("com.android.chrome");

            // 2. Establish the VPN and get the File Descriptor
            vpnInterface = builder.establish();
            if (vpnInterface != null) {
                int tunFd = vpnInterface.getFd();
                Log.i(TAG, "VPN established. TUN FD: " + tunFd);

                // 3. Start Xray and pass the tunFd
                xrayLogger = new XrayService();

                // We run this in a new thread so it doesn't block the Service's main thread
                new Thread(() -> xrayLogger.startXrayLogger(tunFd)).start();
            } else {
                Log.e(TAG, "Failed to establish VPN interface.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up VPN: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up when the VPN stops
        if (xrayLogger != null) {
            xrayLogger.stopXrayLogger();
        }
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing VPN interface", e);
        }
    }
}