package me.dashbikash.dashvpnservice;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import libv2ray.Libv2ray;
import libv2ray.CoreController;
import libv2ray.CoreCallbackHandler;
import libv2ray.ProcessFinder;

public class XrayVpnService extends VpnService {

    private static final String TAG = Constants.TAG;
    private ParcelFileDescriptor vpnInterface;
    private CoreController xrayController;

    // The local port that the library's internal TUN stack forwards to.
    // This must match what AndroidLibXrayLite expects — check the library's
    // source or use the default from v2rayNG (typically 10808 for SOCKS).
    private static final int INBOUND_SOCKS_PORT = 10808;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(this::establishVpnConnection).start();
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
        Log.i(TAG, "VPN Revoked");
        stopSelf();
    }

    private void establishVpnConnection() {
        Builder builder = new Builder();

        builder.setSession(TAG)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4");

        try {
            builder.addAllowedApplication("com.whatsapp");
            builder.addAllowedApplication("com.brave.browser");
            builder.addAllowedApplication("com.google.android.youtube");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Tried to add an uninstalled app.", e);
        }

        try {
            vpnInterface = builder.establish();
            if (vpnInterface != null) {
                Log.i(TAG, "VPN established. Starting Xray Core...");
                startXrayCore(vpnInterface.getFd());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish VPN", e);
        }
    }

    private void startXrayCore(int tunFd) {
        Libv2ray.initCoreEnv(getFilesDir().getAbsolutePath(), "");

        CoreCallbackHandler callback = new CoreCallbackHandler() {
            @Override
            public long shutdown() {
                Log.i(TAG, "Xray Core Shutdown");
                return 0;
            }

            @Override
            public long startup() {
                Log.i(TAG, "Xray Core Started");
                return 0;
            }

            @Override
            public long onEmitStatus(long code, String msg) {
                Log.i(TAG, "Xray Status [" + code + "]: " + msg);
                return 0;
            }
        };

        xrayController = Libv2ray.newCoreController(callback);

        // ProcessFinder is now actually triggered because the routing config
        // below has an "app"-style rule that forces Xray to call this for
        // every connection to determine its source UID.
        xrayController.registerProcessFinder(new ProcessFinder() {
            @Override
            public long findProcessByConnection(
                    String network,
                    String srcIp, long srcPort,
                    String destIp, long destPort) {

                Log.d(TAG, "Traffic | proto=" + network
                        + " src=" + srcIp + ":" + srcPort
                        + " -> dst=" + destIp + ":" + destPort);

                // Returning -1 means "unknown process" — Xray will
                // fall through to the next matching rule (the catch-all
                // direct rule below), so traffic still flows normally.
                return -1;
            }
        });

        // KEY CHANGE: No "tun" protocol in inbounds.
        // The library owns the TUN fd at the Go layer and internally
        // forwards packets to a local transparent/SOCKS listener.
        // Xray's JSON config should describe what to do WITH that traffic.
        //
        // The "dokodemo-door" inbound here acts as the transparent receiver.
        // "followRedirect: true" captures the original destination automatically.
        String xrayConfig = "{\n" +
                "  \"log\": {\n" +
                "    \"loglevel\": \"debug\",\n" +
                // Access log: each line = one connection with src/dst.
                // Use \"none\" to disable file logging and rely on ProcessFinder only.
                "    \"access\": \"" + getFilesDir().getAbsolutePath() + "/access.log\"\n" +
                "  },\n" +
                "  \"inbounds\": [\n" +
                "    {\n" +
                "      \"tag\": \"transparent-in\",\n" +
                "      \"port\": " + INBOUND_SOCKS_PORT + ",\n" +
                "      \"listen\": \"127.0.0.1\",\n" +
                "      \"protocol\": \"dokodemo-door\",\n" +
                "      \"settings\": {\n" +
                "        \"network\": \"tcp,udp\",\n" +
                "        \"followRedirect\": true\n" +
                "      },\n" +
                "      \"sniffing\": {\n" +
                "        \"enabled\": true,\n" +
                "        \"destOverride\": [\"http\", \"tls\", \"quic\"]\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"routing\": {\n" +
                "    \"domainStrategy\": \"AsIs\",\n" +
                "    \"rules\": [\n" +
                "      {\n" +
                // This rule triggers findProcessByConnection for EVERY connection.
                // Xray calls ProcessFinder to get the UID, gets -1 (unknown),
                // this rule doesn't match, and traffic falls to the catch-all below.
                "        \"type\": \"field\",\n" +
                "        \"inboundTag\": [\"transparent-in\"],\n" +
                "        \"app\": [\"__process_lookup_trigger__\"],\n" +
                "        \"outboundTag\": \"direct\"\n" +
                "      },\n" +
                "      {\n" +
                // Catch-all: send everything direct.
                "        \"type\": \"field\",\n" +
                "        \"network\": \"tcp,udp\",\n" +
                "        \"outboundTag\": \"direct\"\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"outbounds\": [\n" +
                "    {\n" +
                "      \"protocol\": \"freedom\",\n" +
                "      \"tag\": \"direct\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        try {
            xrayController.startLoop(xrayConfig, tunFd);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Xray loop", e);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (xrayController != null) {
            try {
                xrayController.stopLoop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping Xray", e);
            }
        }
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
        }
    }
}