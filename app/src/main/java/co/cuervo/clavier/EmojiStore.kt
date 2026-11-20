package co.cuervo.clavier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

data class Sticker(val id: String, val alias: String)

/** Émojis personnalisés : 12 intégrés (assets) + ceux ajoutés par l'utilisateur. */
object EmojiStore {

    val builtin = listOf(
        Sticker("corbeau", "🐦"),
        Sticker("coeur_couronne", "❤️"),
        Sticker("feu", "🔥"),
        Sticker("ok", "✅"),
        Sticker("etoile", "⭐"),
        Sticker("rire", "😂"),
        Sticker("cool", "😎"),
        Sticker("amour", "😍"),
        Sticker("triste", "😢"),
        Sticker("piece", "💰"),
        Sticker("eclair", "⚡"),
        Sticker("diamant", "💎")
    )

    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "emoji")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Copie les émojis intégrés dans le stockage interne (nécessaire pour les partager via FileProvider). */
    fun ensureInstalled(ctx: Context) {
        val d = dir(ctx)
        for (s in builtin) {
            val f = File(d, s.id + ".png")
            if (f.exists()) continue
            try {
                ctx.assets.open("emoji/" + s.id + ".png").use { input ->
                    FileOutputStream(f).use { out -> input.copyTo(out) }
                }
            } catch (e: Exception) {
                // ignoré : l'émoji sera simplement absent
            }
        }
    }

    fun builtinFile(ctx: Context, s: Sticker): File = File(dir(ctx), s.id + ".png")

    fun userFiles(ctx: Context): List<File> {
        val all = dir(ctx).listFiles() ?: return emptyList()
        return all.filter { it.name.startsWith("mes_") && it.name.endsWith(".png") }
            .sortedBy { it.lastModified() }
    }

    /** Ajoute une image de la galerie comme émoji (réduite à 256 px max). */
    fun addFromUri(ctx: Context, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false

            val max = 256f
            val scale = minOf(max / bmp.width, max / bmp.height, 1f)
            val w = maxOf(1, (bmp.width * scale).toInt())
            val h = maxOf(1, (bmp.height * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)

            val f = File(dir(ctx), "mes_" + System.currentTimeMillis() + ".png")
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun delete(file: File) {
        file.delete()
    }
}
