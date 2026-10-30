package ca.michael.pip

import android.content.Context

/**
 * Plain (non-secret) settings, names from SPEC section 9. Defaults are set
 * for Pip and listed in README "Settings". Secrets live in SecretStore.
 */
class PipSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pip_settings", Context.MODE_PRIVATE)

    var brainBaseUrl: String
        get() = prefs.getString("brain_base_url", "") ?: ""
        set(v) = prefs.edit().putString("brain_base_url", v).apply()

    var brainModel: String
        get() = prefs.getString("brain_model", "") ?: ""
        set(v) = prefs.edit().putString("brain_model", v).apply()

    var acpHost: String
        get() = prefs.getString("acp_host", "") ?: ""
        set(v) = prefs.edit().putString("acp_host", v).apply()

    var systemAsrOptIn: Boolean
        get() = prefs.getBoolean("system_asr_opt_in", false)
        set(v) = prefs.edit().putBoolean("system_asr_opt_in", v).apply()

    var speechLanguage: String
        get() = prefs.getString("speech_language", "fr-CA") ?: "fr-CA"
        set(v) = prefs.edit().putString("speech_language", v).apply()

    var speakAnswers: Boolean
        get() = prefs.getBoolean("speak_answers", true)
        set(v) = prefs.edit().putBoolean("speak_answers", v).apply()

    /** Block A (SPEC 13): the brain may propose device actions, each approved on S10. */
    var actionsEnabled: Boolean
        get() = prefs.getBoolean("actions_enabled", true)
        set(v) = prefs.edit().putBoolean("actions_enabled", v).apply()

    /** Block B (SPEC 15): the floating bubble, off by default (FR-B.1). */
    var bubbleEnabled: Boolean
        get() = prefs.getBoolean("bubble_enabled", false)
        set(v) = prefs.edit().putBoolean("bubble_enabled", v).apply()

    /** When the current ACP pairing secret was created (key age on S5). 0 = not paired. */
    var acpPairedAtMs: Long
        get() = prefs.getLong("acp_paired_at", 0L)
        set(v) = prefs.edit().putLong("acp_paired_at", v).apply()
}
