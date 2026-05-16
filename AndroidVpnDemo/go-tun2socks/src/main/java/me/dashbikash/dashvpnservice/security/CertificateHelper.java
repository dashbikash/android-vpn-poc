package me.dashbikash.dashvpnservice.security;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;

import android.content.Context;
import android.util.Base64;
import android.util.Log;


public class CertificateHelper {

    private static final String TAG = "CertificateHelper";

    /**
     * Reads an X.509 Certificate from an InputStream and extracts the Public Key.
     */

    public static PublicKey getPublicKeyFromCertificate(InputStream certStream) {
        try {
            // X.509 is the standard format for public key certificates
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(certStream);

            // Extract and return the public key
            return certificate.getPublicKey();

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse certificate", e);
            return null;
        }
    }

    // NEW METHOD: Requires you to pass the Context
    public static PublicKey loadKeyFromAssets(Context context, String fileName) {
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder keyBuilder = new StringBuilder();
            String line;

            // 1. Read the file line-by-line
            while ((line = reader.readLine()) != null) {
                // Skip the PEM header and footer lines
                if (line.contains("-----BEGIN") || line.contains("-----END")) {
                    continue;
                }
                // Append only the Base64 characters
                keyBuilder.append(line.trim());
            }

            // 2. Decode the Base64 string into raw bytes
            String publicKeyPEM = keyBuilder.toString();
            byte[] decodedKey = Base64.decode(publicKeyPEM, Base64.DEFAULT);

            // 3. Convert the raw bytes into a Java Elliptic Curve (EC) PublicKey object
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");

            return keyFactory.generatePublic(spec);

        } catch (Exception e) {
            Log.e(TAG, "Failed to load and parse public key from assets: " + fileName, e);
            return null;
        }
    }
}
