package me.dashbikash.dashvpnservice.security;
public class NativeCrypto {

    // Load the native C library when this class is loaded
//    static {
//        System.loadLibrary("vpn_crypto");
//    }

    // Declare the native method
    // We pass bytes instead of Strings because C handles raw byte arrays better
    public native byte[] encryptIpAddress(byte[] rawIpData, byte[] publicKey);
}