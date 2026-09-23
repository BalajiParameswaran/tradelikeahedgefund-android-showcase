package com.tradelikeahedgefund.app;

import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * SecureStore: key-value storage backed by Android Keystore
 * (EncryptedSharedPreferences, AES256_GCM). Nothing is stored in plaintext.
 */
@CapacitorPlugin(name = "SecureStore")
public class SecureStorePlugin extends Plugin {
    private SharedPreferences prefs;

    private synchronized SharedPreferences getPrefs() throws Exception {
        if (prefs == null) {
            MasterKey masterKey = new MasterKey.Builder(getContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    getContext(),
                    "tlhf_secure",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        }
        return prefs;
    }

    @PluginMethod
    public void set(PluginCall call) {
        try {
            String key = call.getString("key");
            String value = call.getString("value");
            if (key == null || value == null) { call.reject("key and value required"); return; }
            getPrefs().edit().putString(key, value).apply();
            call.resolve();
        } catch (Exception e) {
            call.reject("secure store unavailable: " + e.getMessage());
        }
    }

    @PluginMethod
    public void get(PluginCall call) {
        try {
            String key = call.getString("key");
            if (key == null) { call.reject("key required"); return; }
            JSObject ret = new JSObject();
            ret.put("value", getPrefs().getString(key, null));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("secure store unavailable: " + e.getMessage());
        }
    }

    @PluginMethod
    public void remove(PluginCall call) {
        try {
            String key = call.getString("key");
            if (key == null) { call.reject("key required"); return; }
            getPrefs().edit().remove(key).apply();
            call.resolve();
        } catch (Exception e) {
            call.reject("secure store unavailable: " + e.getMessage());
        }
    }
}
