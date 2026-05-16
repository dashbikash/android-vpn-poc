package me.dashbikash.dashvpnservice;

import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import tun2socks.Tun2socks;

public class MyVpnService extends android.net.VpnService implements Runnable,
        tun2socks.VpnService, tun2socks.PacketFlow, tun2socks.LogService, tun2socks.QuerySpeed {

    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    private BufferedOutputStream mBufferedOut;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mThread != null) mThread.interrupt();
        mThread = new Thread(this, "TrafficMonitorThread");
        mThread.start();
        return START_STICKY;
    }

    @Override
    public void run() {

        try {
            // 1. Configure the VPN Interface (IPv4 + IPv6 capture, adjusted MTU)
            Builder builder = new Builder();
            builder.setSession("TrafficMonitor")
                    .setMtu(1400) // Lowered MTU to prevent packet fragmentation on downloads
                    .addAddress("10.0.0.2", 32)
                    .addAddress("fd00:1:2:3:4:5:6:7", 128) // Dummy IPv6 to capture modern app traffic
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0) // Route all IPv6 into the VPN
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1");

            mInterface = builder.establish();
            FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());

            // Use BufferedOutputStream to handle rapid incoming download packets without dropping them
            FileOutputStream rawOut = new FileOutputStream(mInterface.getFileDescriptor());
            mBufferedOut = new BufferedOutputStream(rawOut, 2* 131072); // 128KB Buffer

            // 2. The "Freedom" Config: Bypasses proxy, forces IPv4 responses
            String directConfig = "{\n" +
                    "  \"outbounds\": [\n" +
                    "    {\n" +
                    "      \"protocol\": \"freedom\",\n" +
                    "      \"settings\": {\n" +
                    "        \"domainStrategy\": \"UseIPv4\"\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            byte[] configBytes = directConfig.getBytes();

            // 3. Asset Directory path (Where Xray looks for geoip/geosite)
            String assetPath = getFilesDir().getAbsolutePath();

            // 4. Start the Xray Engine in a background thread
            new Thread(() -> {
                try {
                    Log.d(Constants.TAG, "Starting Xray Engine...");
                    Tun2socks.startXRay(this, this, this, this, configBytes, assetPath);
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Failed to start Xray Engine", e);
                }
            }).start();

            // Give the engine a brief moment to boot up before sending packets
            Thread.sleep(500);

            byte[] packet = new byte[32767];

            // 5. Packet Loop
            while (!Thread.currentThread().isInterrupted()) {
                int length = in.read(packet);
                if (length > 0) {
                    // Monitor IP (Your original goal!)
                    parseAndLogIP(packet, length);

                    // Forward to Go network stack
                    byte[] dataToProcess = new byte[length];
                    System.arraycopy(packet, 0, dataToProcess, 0, length);
                    Tun2socks.inputPacket(dataToProcess);
                }
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error in VPN Service", e);
        }
    }

    private void parseAndLogIP(byte[] packet, int length) {
        byte version = (byte) ((packet[0] >> 4) & 0x0F);
        if (version == 4 && length >= 20) {
            byte[] destIpBytes = new byte[]{packet[16], packet[17], packet[18], packet[19]};
            try {
                InetAddress destIp = InetAddress.getByAddress(destIpBytes);
                Log.d(Constants.TAG, "Outgoing connection to IP: " + destIp.getHostAddress());
            } catch (UnknownHostException e) {
                // Ignore
            }
        }
    }
    private void parseAndLogIncomingIP(byte[] packet) {
        if (packet == null || packet.length < 20) return;

        byte version = (byte) ((packet[0] >> 4) & 0x0F);
        if (version == 4) {
            // For incoming packets, the remote server is the Source IP (bytes 12 to 15)
            byte[] srcIpBytes = new byte[]{packet[12], packet[13], packet[14], packet[15]};
            try {
                InetAddress srcIp = InetAddress.getByAddress(srcIpBytes);
                Log.d(Constants.TAG, "Incoming packet from IP: " + srcIp.getHostAddress());
            } catch (UnknownHostException e) {
                // Ignore
            }
        }
    }

    // ==========================================================
    // GO INTERFACE IMPLEMENTATIONS
    // ==========================================================

    @Override
    public boolean protect(long fd) {
        // CRITICAL: Tells Android to let Go's sockets bypass the VPN to reach the internet
        boolean isProtected = super.protect((int) fd);
        if (!isProtected) {
            Log.e(Constants.TAG, "CRITICAL: Failed to protect socket! Connection will loop.");
        }
        return isProtected;
    }

    @Override
    public void writePacket(byte[] packet) {
        // Go has processed the internet response and is sending it back to the Android device
        // --- ADD THIS LINE TO LOG INCOMING IPs ---
        parseAndLogIncomingIP(packet);
        // -----------------------------------------
        try {
            if (mBufferedOut != null) {
                mBufferedOut.write(packet);
                mBufferedOut.flush(); // Crucial: Push the data to the TUN interface immediately
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Failed to write back to TUN", e);
        }
    }

    @Override
    public void writeLog(String s) {
        // Optional: Comment this out later if Xray logs get too noisy
        Log.d("Xray-Core", s);
    }

    @Override
    public void updateTraffic(long uploadStats, long downloadStats) {
        // Xray reports speed statistics here
        // Log.d("Xray-Speed", "Up: " + uploadStats + " Down: " + downloadStats);
    }

    @Override
    public void onDestroy() {
        if (mThread != null) mThread.interrupt();
        try {
            if (mInterface != null) mInterface.close();
            if (mBufferedOut != null) mBufferedOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}