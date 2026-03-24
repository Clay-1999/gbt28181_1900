package com.example.gbt28181.sip;

/**
 * Static holder for the current local SIP config parameters.
 * Updated by SipStackManager on each successful reload.
 */
public class LocalSipConfigHolder {

    private static volatile String deviceId = "";
    private static volatile String sipIp = "127.0.0.1";
    private static volatile int sipPort = 5060;

    private LocalSipConfigHolder() {}

    public static String getDeviceId() { return deviceId; }
    public static String getSipIp() { return sipIp; }
    public static int getSipPort() { return sipPort; }

    public static void update(String deviceId, String sipIp, int sipPort) {
        LocalSipConfigHolder.deviceId = deviceId != null ? deviceId : "";
        LocalSipConfigHolder.sipIp = sipIp != null ? sipIp : "127.0.0.1";
        LocalSipConfigHolder.sipPort = sipPort;
    }
}
