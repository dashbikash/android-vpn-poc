package me.dashbikash.dashvpnservice;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;

public class AppBasedSplitTunnelService extends VpnService {

    private static final String TAG = Constants.TAG;
    private ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Run the connection logic on a separate thread to avoid blocking the main UI thread
        new Thread(() -> {
            establishVpnConnection();
        }).start();

        // START_STICKY tells the OS to recreate the service if it gets killed for memory
        return START_STICKY;
    }
    private void establishVpnConnection() {
        Builder builder = new Builder();

        // 1. Configure basic VPN parameters
        builder.setSession(Constants.TAG)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) // Route all traffic to the VPN initially
                .addDnsServer("208.67.222.222")
                .addDnsServer("208.67.220.220");

        // 2. Apply Split Tunneling Rules
        try {
            // STRATEGY A: Inclusive (Allow-list)
            // ONLY these apps will go through the VPN. Everything else goes through the normal ISP.
            builder.addAllowedApplication("com.whatsapp");
            builder.addAllowedApplication("com.brave.browser");
            builder.addAllowedApplication("com.google.android.youtube");

            /*
            // STRATEGY B: Exclusive (Block-list)
            // ALL apps go through the VPN, EXCEPT the ones listed here.
            builder.addDisallowedApplication("com.netflix.mediaclient");
            builder.addDisallowedApplication("com.spotify.music");
            */

        } catch (PackageManager.NameNotFoundException e) {
            // This exception is thrown if you try to add a package name that isn't installed on the device.
            Log.e(TAG, "Tried to add an app to split tunneling that is not installed.", e);
        }

        // 3. Establish the VPN interface
        try {
            vpnInterface = builder.establish();
            if (vpnInterface != null) {
                Log.i(TAG, "VPN established successfully with split tunneling.");
                // Start reading packets in a separate thread
                new Thread(this::capturePackets).start();
                // Proceed to handle read/write on the vpnInterface's FileDescriptor
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "Failed to establish VPN", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up the interface when the service is destroyed
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
        }
    }
    private void capturePackets() {
        // The FileDescriptor acts as the "tunnel"
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());

        // Allocate a buffer for the maximum typical IP packet size
        byte[] packet = new byte[32767];

        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Read raw IP packets from the tun interface.
                // This is a blocking call; it waits until a packet arrives.
                int length = in.read(packet);

                if (length > 0) {
                    // Parse the raw bytes to extract IP information
                    extractIpAddresses(packet, length);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading from VPN interface", e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    private void extractIpAddresses(byte[] packet, int length) {
        // An IPv4 header is at least 20 bytes long
        if (length >= 20) {
            // The first byte contains the version (first 4 bits) and header length
            int version = (packet[0] >> 4) & 0x0F;

            if (version == 4) { // It's an IPv4 packet
                // Extract Source IP (Offset 12)
                String sourceIp = getIpString(packet, 12);

                // Extract Destination IP (Offset 16)
                String destIp = getIpString(packet, 16);

                // Log the captured IPs
                Log.d(TAG, "Captured Packet | Src: " + sourceIp + " -> Dest: " + destIp);

            } else if (version == 6) { // It's an IPv6 packet
                // IPv6 header parsing is different (Source is at offset 8, Dest at offset 24)
                Log.d(TAG, "Captured IPv6 packet (parsing not implemented here)");
            }
        }
    }

    // Helper method to convert 4 bytes into a readable IP string (e.g., "192.168.1.1")
    private String getIpString(byte[] packet, int offset) {
        return (packet[offset] & 0xFF) + "." +
                (packet[offset + 1] & 0xFF) + "." +
                (packet[offset + 2] & 0xFF) + "." +
                (packet[offset + 3] & 0xFF);
    }
}