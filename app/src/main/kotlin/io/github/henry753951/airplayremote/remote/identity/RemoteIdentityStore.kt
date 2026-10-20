package io.github.henry753951.airplayremote.remote.identity

import io.github.henry753951.airplayremote.remote.input.InjectionMode

import android.content.Context
import io.github.henry753951.airplayremote.InstallationContract
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

object RemoteIdentityStore {

    @Synchronized
    fun getPairingPin(context: Context): Int {
        val prefs = getPrefs(context)
        val existing = prefs.getInt("pairing_pin", 0)
        if (existing in 1000..9999) return existing
        val pin = java.security.SecureRandom().nextInt(9000) + 1000
        check(prefs.edit().putInt("pairing_pin", pin).commit())
        return pin
    }

    @Synchronized
    fun getPrivateKey(context: Context): ByteArray {
        val prefs = getPrefs(context)
        prefs.getString("signing_seed", null)?.let {
            return android.util.Base64.decode(it, android.util.Base64.NO_WRAP).also { key -> check(key.size == 32) }
        }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        check(prefs.edit().putString("signing_seed", android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)).commit())
        return key
    }

    const val PREF_MENU_BUTTON_BINDING = "menu_button_binding"
    const val PREF_INJECTION_MODE = "injection_mode"

    fun getPrefs(context: Context): SharedPreferences {
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !context.isDeviceProtectedStorage) {
            try {
                context.createDeviceProtectedStorageContext()
            } catch (_: Exception) {
                context
            }
        } else {
            context
        }
        return storageContext.getSharedPreferences(InstallationContract.REMOTE_PREFERENCES, Context.MODE_PRIVATE)
    }

    fun getMenuButtonBinding(context: Context): String {
        return getPrefs(context).getString(PREF_MENU_BUTTON_BINDING, "none") ?: "none"
    }

    fun setMenuButtonBinding(context: Context, buttonName: String) {
        getPrefs(context).edit().putString(PREF_MENU_BUTTON_BINDING, buttonName.lowercase()).apply()
    }

    fun getInjectionMode(context: Context): String {
        return getPrefs(context).getString(PREF_INJECTION_MODE, "auto") ?: "auto"
    }

    fun setInjectionMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(PREF_INJECTION_MODE, mode.lowercase()).apply()
    }

    fun getDeviceName(context: Context): String {
        return io.github.henry753951.airplayremote.Prefs.SERVER_NAME.read(
            context.getSharedPreferences(io.github.henry753951.airplayremote.Prefs.NAME, Context.MODE_PRIVATE))
    }

    fun getDeviceId(context: Context): String {
        val prefs = getPrefs(context)
        val cached = prefs.getString("device_id", null)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        val bytes = ByteArray(6).also { java.security.SecureRandom().nextBytes(it) }
        bytes[0] = ((bytes[0].toInt() or 2) and 254).toByte()
        val deviceId = bytes.joinToString(":") { "%02X".format(it) }

        prefs.edit().putString("device_id", deviceId).apply()
        return deviceId
    }

    fun getServerIdentifier(context: Context, deviceId: String): String {
        val prefs = getPrefs(context)
        val cached = prefs.getString("server_uuid", null)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        val uuid = UUID.nameUUIDFromBytes("${InstallationContract.REMOTE_UUID_NAMESPACE}$deviceId".toByteArray()).toString().uppercase()
        prefs.edit().putString("server_uuid", uuid).apply()
        return uuid
    }

}
