package co.cuervo.clavier

import android.content.Context

/** Réglages : adresse de la fonction IA, mot de passe secret, disposition des touches. */
object Prefs {
    private const val FILE = "cuervo"
    private const val KEY_URL = "ai_url"
    private const val KEY_KEY = "ai_key"
    private const val KEY_LAYOUT = "layout"
    private const val KEY_AUTOFIX = "autofix"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun url(ctx: Context): String = p(ctx).getString(KEY_URL, "") ?: ""

    fun key(ctx: Context): String = p(ctx).getString(KEY_KEY, "") ?: ""

    fun save(ctx: Context, url: String, key: String) {
        p(ctx).edit().putString(KEY_URL, url.trim()).putString(KEY_KEY, key.trim()).apply()
    }

    /** "qwerty" (par défaut, comme le clavier Samsung) ou "azerty". */
    fun layout(ctx: Context): String = p(ctx).getString(KEY_LAYOUT, "qwerty") ?: "qwerty"

    fun setLayout(ctx: Context, value: String) {
        p(ctx).edit().putString(KEY_LAYOUT, value).apply()
    }

    /** Correction automatique des mots français (activée par défaut). */
    fun autoCorrect(ctx: Context): Boolean = p(ctx).getBoolean(KEY_AUTOFIX, true)

    fun setAutoCorrect(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean(KEY_AUTOFIX, on).apply()
    }
}
