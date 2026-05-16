package me.dashbikash.dashvpnservice.security;

import java.security.MessageDigest;

public class HashUtil {

    public static String hashAndroidId(String androidId) {
        try {
            // Use SHA-256 for a one-way secure hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(androidId.getBytes("UTF-8"));

            // Convert bytes to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            throw new RuntimeException("Could not hash ID", e);
        }
    }
}
