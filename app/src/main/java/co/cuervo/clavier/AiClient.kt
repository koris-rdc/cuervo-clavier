package co.cuervo.clavier

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Appelle la fonction Supabase « ai-keyboard ».
 * La clé de l'IA reste côté serveur : le clavier n'envoie que le texte demandé par l'utilisateur.
 * À appeler hors du thread principal.
 */
object AiClient {

    /** Une connexion réseau coupée par Android (« connection abort ») est retentée une fois. */
    fun call(ctx: Context, action: String, text: String): Result<String> {
        val first = once(ctx, action, text)
        val err = first.exceptionOrNull()
        return if (err is java.net.SocketException || err is java.net.UnknownHostException || err is java.io.EOFException) {
            Thread.sleep(600)
            once(ctx, action, text)
        } else first
    }

    private fun once(ctx: Context, action: String, text: String): Result<String> = runCatching {
        val url = Prefs.url(ctx)
        if (!url.startsWith("https://")) throw IOException("Adresse de l'IA absente ou invalide")

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 40_000
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Connection", "close")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val key = Prefs.key(ctx)
            if (key.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("apikey", key)
            }

            val body = JSONObject().put("action", action).put("text", text).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("Erreur IA ($code)")

            val result = JSONObject(resp).optString("result", "")
            if (result.isBlank()) throw IOException("Réponse vide")
            result
        } finally {
            conn.disconnect()
        }
    }
}
