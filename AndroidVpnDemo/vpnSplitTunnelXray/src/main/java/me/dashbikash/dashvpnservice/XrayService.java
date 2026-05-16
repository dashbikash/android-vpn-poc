package me.dashbikash.dashvpnservice;

import android.util.Log;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;
import libv2ray.ProcessFinder;

public class XrayService {

    private static final String TAG = Constants.TAG;
    private CoreController coreController;

    // FIX 2: Explicitly instantiate the interface instead of using a Lambda
    // to prevent GoMobile JNI binding failures and aggressive Garbage Collection.
    private final ProcessFinder processFinder = new ProcessFinder() {
        @Override
        public long findProcessByConnection(String network, String srcIP, long srcPort, String destIP, long destPort) {
            Log.e(TAG, ">>> INCOMING: " + network + " | IP: " + destIP + ":" + destPort);
            return -1;
        }
    };

    public void startXrayLogger(int tunFd) {
        CoreCallbackHandler callbackHandler = new CoreCallbackHandler() {
            @Override
            public long startup() {
                Log.d(TAG, "Xray core started.");
                return 0;
            }

            @Override
            public long shutdown() {
                Log.d(TAG, "Xray core shutdown.");
                return 0;
            }

            @Override
            public long onEmitStatus(long code, String message) {
                Log.d(TAG, "Xray Status [" + code + "]: " + message);
                return 0;
            }
        };

        coreController = Libv2ray.newCoreController(callbackHandler);
        coreController.registerProcessFinder(processFinder);

        String configContent = "{\n" +
                "  \"log\": {\n" +
                "    \"loglevel\": \"info\"\n" +
                "  },\n" +
                "  \"inbounds\": [\n" +
                "    {\n" +
                "      \"tag\": \"tun-inbound\",\n" +
                "      \"protocol\": \"tun\",\n" +
                "      \"settings\": {\n" +
                "        \"autoRoute\": false,\n" +
                "        \"strictRoute\": false,\n" +
                "        \"stack\": \"gvisor\"\n" + // FIX 1: gVisor user-space stack is required for raw tunFd
                "      },\n" +
                "      \"sniffing\": {\n" +
                "        \"enabled\": true,\n" +
                "        \"destOverride\": [\"http\", \"tls\", \"quic\"],\n" +
                "        \"routeOnly\": true\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"outbounds\": [\n" +
                "    {\n" +
                "      \"tag\": \"direct-outbound\",\n" +
                "      \"protocol\": \"freedom\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"routing\": {\n" +
                "    \"domainStrategy\": \"AsIs\",\n" +
                "    \"rules\": [\n" +
                "      {\n" +
                "        \"type\": \"field\",\n" +
                "        \"port\": \"0-65535\",\n" +
                "        \"network\": \"tcp,udp\",\n" +
                "        \"app\": [\"dummy.trigger.app\"],\n" +
                "        \"outboundTag\": \"direct-outbound\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"type\": \"field\",\n" +
                "        \"inboundTag\": [\"tun-inbound\"],\n" +
                "        \"outboundTag\": \"direct-outbound\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        try {
            coreController.startLoop(configContent, tunFd);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Xray: " + e.getMessage());
        }
    }

    public void stopXrayLogger() {
        if (coreController != null) {
            try {
                coreController.stopLoop();
                coreController.registerProcessFinder(null);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping Xray: " + e.getMessage());
            }
        }
    }
}