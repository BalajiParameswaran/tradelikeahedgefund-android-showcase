package com.tradelikeahedgefund.app.ai

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tlhf.shared.ai.AiModel
import com.tlhf.shared.ai.KeyValueStorage
import java.io.File

/**
 * Android platform pieces for the on-device AI module:
 * encrypted chat storage, model file locations, WiFi check, device check.
 *
 * Chats live in EncryptedSharedPreferences (AES256) — the native equivalent
 * of the web app's "no plaintext chat logs" SecureStore guarantee. If the
 * encrypted store can't be created (old device), we fall back to plain
 * SharedPreferences rather than losing the user's chats.
 */
private class PrefsStorage(private val prefs: SharedPreferences) : KeyValueStorage {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}

object AiPlatform {

    fun storage(context: Context): KeyValueStorage {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "tlhf_ai_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            PrefsStorage(prefs)
        } catch (_: Exception) {
            PrefsStorage(context.getSharedPreferences("tlhf_ai_fallback", Context.MODE_PRIVATE))
        }
    }

    /** General AI prefs (selected model, wifi-only flag, last-used timestamp). */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("tlhf_ai_prefs", Context.MODE_PRIVATE)

    fun modelDir(context: Context): File = File(context.filesDir, "ai").apply { mkdirs() }

    fun modelFile(context: Context, model: AiModel): File =
        File(modelDir(context), model.androidFileName)

    fun partFile(context: Context, model: AiModel): File =
        File(modelDir(context), model.androidFileName + ".part")

    fun isWifi(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork)
        nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    } catch (_: Exception) {
        false
    }

    data class DeviceCheck(val supported: Boolean, val reason: String?)

    /**
     * Same gate as the web app: 64-bit ABI + at least 4 GB RAM.
     * The .task LLM will not load on 32-bit or very small phones.
     */
    fun deviceCheck(context: Context): DeviceCheck {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val gb = mi.totalMem / 1073741824.0
            val abi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
            val is64 = abi.contains("64")
            val ok = is64 && gb >= 4.0
            DeviceCheck(ok, if (ok) null else if (!is64) "needs a 64-bit phone" else "needs at least 4 GB of RAM")
        } catch (e: Exception) {
            DeviceCheck(false, e.message)
        }
    }
}
