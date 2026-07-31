package com.rundeck.app.notifications

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RunDeckNotificationSource(
    val packageName: String,
    val label: String,
    val allowed: Boolean,
)

data class RunDeckNotificationContact(
    val packageName: String,
    val sender: String,
    val allowed: Boolean,
)

data class RunDeckNotificationSettings(
    val forwardingEnabled: Boolean = true,
    val allowAllMessageApps: Boolean = true,
    val allowAllContacts: Boolean = true,
    val sources: List<RunDeckNotificationSource> = emptyList(),
    val contacts: List<RunDeckNotificationContact> = emptyList(),
) {
    val selectedCount: Int = sources.count { it.allowed }
    val selectedContactCount: Int = contacts.count { it.allowed }
}

object RunDeckNotificationPreferences {
    private const val PREFS = "rundeck_notification_prefs"
    private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
    private const val KEY_ALLOW_ALL_MESSAGE_APPS = "allow_all_message_apps"
    private const val KEY_ALLOW_ALL_CONTACTS = "allow_all_contacts"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
    private const val KEY_ALLOWED_CONTACTS = "allowed_contacts"
    private const val KEY_OBSERVED_SOURCES = "observed_sources"
    private const val KEY_OBSERVED_CONTACTS = "observed_contacts"
    private const val ENTRY_SEPARATOR = "\t"

    private val knownMessagePackages = listOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.whatsapp",
        "org.thoughtcrime.securesms",
        "org.telegram.messenger",
        "com.facebook.orca",
    )

    private val _settings = MutableStateFlow(RunDeckNotificationSettings())
    val settings: StateFlow<RunDeckNotificationSettings> = _settings.asStateFlow()

    fun initialize(context: Context) {
        discoverKnownSources(context.applicationContext)
        refresh(context.applicationContext)
    }

    fun isPackageAllowed(context: Context, packageName: String, label: String): Boolean {
        val appContext = context.applicationContext
        rememberSource(appContext, packageName, label)
        val settings = currentSettings(appContext)
        if (!settings.forwardingEnabled) return false
        if (settings.allowAllMessageApps) return true
        return settings.sources.any { it.packageName == packageName && it.allowed }
    }

    fun isContactAllowed(context: Context, packageName: String, sender: String): Boolean {
        val appContext = context.applicationContext
        rememberContact(appContext, packageName, sender)
        val settings = currentSettings(appContext)
        if (settings.allowAllContacts) return true
        return settings.contacts.any { it.packageName == packageName && it.sender == sender && it.allowed }
    }

    fun setForwardingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORWARDING_ENABLED, enabled).apply()
        refresh(context.applicationContext)
    }

    fun setAllowAllMessageApps(context: Context, allowAll: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_ALL_MESSAGE_APPS, allowAll).apply()
        refresh(context.applicationContext)
    }

    fun setAllowAllContacts(context: Context, allowAll: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_ALL_CONTACTS, allowAll).apply()
        refresh(context.applicationContext)
    }

    fun setSourceAllowed(context: Context, packageName: String, allowed: Boolean) {
        val prefs = prefs(context)
        val allowedPackages = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet()).orEmpty().toMutableSet()
        if (allowed) allowedPackages.add(packageName) else allowedPackages.remove(packageName)
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, allowedPackages).apply()
        refresh(context.applicationContext)
    }

    fun setContactAllowed(context: Context, packageName: String, sender: String, allowed: Boolean) {
        val key = contactKey(packageName, sender) ?: return
        val prefs = prefs(context)
        val allowedContacts = prefs.getStringSet(KEY_ALLOWED_CONTACTS, emptySet()).orEmpty().toMutableSet()
        if (allowed) allowedContacts.add(key) else allowedContacts.remove(key)
        prefs.edit().putStringSet(KEY_ALLOWED_CONTACTS, allowedContacts).apply()
        refresh(context.applicationContext)
    }

    fun rememberSource(context: Context, packageName: String, label: String) {
        val cleanPackage = packageName.trim()
        val cleanLabel = label.sanitizePreferenceLabel()
        if (cleanPackage.isBlank() || cleanLabel.isBlank()) return

        val prefs = prefs(context)
        val sources = readObservedSources(prefs).toMutableMap()
        if (sources[cleanPackage] == cleanLabel) return
        sources[cleanPackage] = cleanLabel
        prefs.edit().putStringSet(KEY_OBSERVED_SOURCES, sources.toEntries()).apply()
        refresh(context.applicationContext)
    }

    fun rememberContact(context: Context, packageName: String, sender: String) {
        val key = contactKey(packageName, sender) ?: return
        val prefs = prefs(context)
        val contacts = readObservedContacts(prefs).toMutableMap()
        if (contacts[key] == sender.sanitizePreferenceLabel()) return
        contacts[key] = sender.sanitizePreferenceLabel()
        prefs.edit().putStringSet(KEY_OBSERVED_CONTACTS, contacts.toEntries()).apply()
        refresh(context.applicationContext)
    }

    private fun currentSettings(context: Context): RunDeckNotificationSettings {
        refresh(context)
        return _settings.value
    }

    private fun refresh(context: Context) {
        val prefs = prefs(context)
        val allowedPackages = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet()).orEmpty()
        val observedSources = readObservedSources(prefs)
        val observedContacts = readObservedContacts(prefs)
        val allowedContacts = prefs.getStringSet(KEY_ALLOWED_CONTACTS, emptySet()).orEmpty()
        _settings.value = RunDeckNotificationSettings(
            forwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, true),
            allowAllMessageApps = prefs.getBoolean(KEY_ALLOW_ALL_MESSAGE_APPS, true),
            allowAllContacts = prefs.getBoolean(KEY_ALLOW_ALL_CONTACTS, true),
            sources = observedSources
                .map { (packageName, label) ->
                    RunDeckNotificationSource(
                        packageName = packageName,
                        label = label,
                        allowed = packageName in allowedPackages,
                    )
                }
                .sortedWith(compareBy<RunDeckNotificationSource> { !it.allowed }.thenBy { it.label.lowercase() }),
            contacts = observedContacts.map { (key, sender) ->
                RunDeckNotificationContact(
                    packageName = key.substringBefore(ENTRY_SEPARATOR),
                    sender = sender,
                    allowed = key in allowedContacts,
                )
            }.sortedWith(compareBy<RunDeckNotificationContact> { !it.allowed }.thenBy { it.sender.lowercase() }),
        )
    }

    private fun discoverKnownSources(context: Context) {
        knownMessagePackages.forEach { packageName ->
            val label = appLabel(context, packageName) ?: return@forEach
            rememberSource(context, packageName, label)
        }
    }

    private fun appLabel(context: Context, packageName: String): String? = runCatching {
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    private fun readObservedSources(prefs: SharedPreferences): Map<String, String> =
        prefs.getStringSet(KEY_OBSERVED_SOURCES, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(ENTRY_SEPARATOR, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .toMap()

    private fun readObservedContacts(prefs: SharedPreferences): Map<String, String> =
        prefs.getStringSet(KEY_OBSERVED_CONTACTS, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(ENTRY_SEPARATOR, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else null
            }
            .toMap()

    private fun Map<String, String>.toEntries(): Set<String> =
        entries.map { (packageName, label) -> "$packageName$ENTRY_SEPARATOR${label.sanitizePreferenceLabel()}" }.toSet()

    private fun contactKey(packageName: String, sender: String): String? {
        val cleanPackage = packageName.trim()
        val cleanSender = sender.sanitizePreferenceLabel()
        if (cleanPackage.isBlank() || cleanSender.isBlank()) return null
        return "$cleanPackage$ENTRY_SEPARATOR$cleanSender"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun String.sanitizePreferenceLabel(): String = asSequence()
        .map { if (it.code in 32..126) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(48)
}
