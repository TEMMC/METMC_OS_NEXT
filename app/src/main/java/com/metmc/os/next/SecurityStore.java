package com.metmc.os.next;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class SecurityStore {
    static final String NONE = "none";
    static final String PIN = "pin";
    static final String PASSWORD = "password";
    static final String PATTERN = "pattern";

    private static final String PREFS = "metmc_security";
    private static final String MODE = "mode";
    private static final String HASH = "hash";
    private static final String SALT = "salt";
    private static final String LOCKED = "locked";
    private static final String AUTO_LOCK = "auto_lock";
    private static final String DEVICE_AUTH = "device_auth";

    private SecurityStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String mode(Context c) {
        return prefs(c).getString(MODE, NONE);
    }

    static boolean enabled(Context c) {
        return !NONE.equals(mode(c)) && prefs(c).contains(HASH);
    }

    static boolean autoLock(Context c) {
        return prefs(c).getBoolean(AUTO_LOCK, false);
    }

    static void setAutoLock(Context c, boolean value) {
        prefs(c).edit().putBoolean(AUTO_LOCK, value).apply();
    }

    static boolean deviceAuth(Context c) {
        return prefs(c).getBoolean(DEVICE_AUTH, true);
    }

    static void setDeviceAuth(Context c, boolean value) {
        prefs(c).edit().putBoolean(DEVICE_AUTH, value).apply();
    }

    static void setCredential(Context c, String mode, String credential) {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        String hash = hash(encodedSalt, credential);
        prefs(c).edit()
                .putString(MODE, mode)
                .putString(SALT, encodedSalt)
                .putString(HASH, hash)
                .putBoolean(LOCKED, false)
                .apply();
    }

    static void clearCredential(Context c) {
        prefs(c).edit()
                .remove(MODE).remove(SALT).remove(HASH)
                .putBoolean(LOCKED, false)
                .apply();
    }

    static boolean verify(Context c, String credential) {
        String salt = prefs(c).getString(SALT, "");
        String expected = prefs(c).getString(HASH, "");
        if (salt.isEmpty() || expected.isEmpty() || credential == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                hash(salt, credential).getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String salt, String credential) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] value = md.digest((salt + ":" + credential).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(value.length * 2);
            for (byte b : value) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static boolean locked(Context c) {
        return enabled(c) && prefs(c).getBoolean(LOCKED, false);
    }

    static void lock(Context c) {
        if (enabled(c)) prefs(c).edit().putBoolean(LOCKED, true).apply();
    }

    static void unlock(Context c) {
        prefs(c).edit().putBoolean(LOCKED, false).apply();
    }
}
