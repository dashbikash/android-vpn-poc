package me.dashbikash.dashvpnservice;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KafkaBridgeService {
    // Define the specific Kafka Bridge Media Type
    private static final MediaType KAFKA_JSON = MediaType.get("application/vnd.kafka.json.v2+json");
    private static final String KAFKA_PUBLISH_URL="https://192.168.29.40:8443/topics/ip-metrics";
    public static boolean produceMessage(String jsonPayload) throws Exception {
        OkHttpClient client = getUnsafeOkHttpClient();

        // 2. Create the RequestBody
        byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
        RequestBody body = RequestBody.create(KAFKA_JSON, payloadBytes);

        // 3. Build the POST request
        Request request = new Request.Builder()
                .url(KAFKA_PUBLISH_URL)
                .post(body)
                .build();

        // 4. Execute the call
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.i(Constants.TAG, "Message published! Response: " + response.body().string());
                return true;
            } else {
                Log.e(Constants.TAG, "Failed to publish: " + response.code() + " " + response.message());
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, Objects.requireNonNull(e.getMessage()));
        }
        return false;
    }
    // Helper method to trust self-signed certificates
    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // 1. Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // 2. Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // 3. Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);

            // 4. Force hostname verification to always return true
            builder.hostnameVerifier((hostname, session) -> true);

            // Optional: Add timeouts since SSL handshakes can sometimes hang
            builder.connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS);
            builder.writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS);
            builder.readTimeout(15, java.util.concurrent.TimeUnit.SECONDS);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
