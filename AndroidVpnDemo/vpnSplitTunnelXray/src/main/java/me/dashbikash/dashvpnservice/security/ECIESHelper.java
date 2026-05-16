package me.dashbikash.dashvpnservice.security;

import android.util.Base64;
import android.util.Log;
import java.security.PublicKey;
import javax.crypto.Cipher;

// IMPORTANT: Import the Bouncy Castle provider from the library we added to Gradle
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class ECIESHelper {

    private static final String TAG = "ECIESHelper";

    // Create a single static instance of the provider to save memory
    private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

    public static String encryptIP(String ipAddress, PublicKey publicKey) {
        try {
            // PASS THE PROVIDER OBJECT DIRECTLY.
            // Do NOT use the string "BC" here, as that points to Android's broken internal version.
            Cipher cipher = Cipher.getInstance("ECIES", BC_PROVIDER);

            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encryptedBytes = cipher.doFinal(ipAddress.getBytes());

            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }
}