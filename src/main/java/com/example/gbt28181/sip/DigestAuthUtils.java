package com.example.gbt28181.sip;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestAuthUtils {

    private DigestAuthUtils() {}

    public static String calcHa1(String username, String realm, String password) {
        return md5(username + ":" + realm + ":" + password);
    }

    public static String calcHa2(String method, String uri) {
        return md5(method + ":" + uri);
    }

    public static String calcResponse(String ha1, String nonce, String ha2) {
        return md5(ha1 + ":" + nonce + ":" + ha2);
    }

    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
