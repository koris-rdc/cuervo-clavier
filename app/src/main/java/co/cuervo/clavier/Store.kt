package co.cuervo.clavier

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private fun prefs(ctx: Context) = ctx.getSharedPreferences("cuervo_data", Context.MODE_PRIVATE)

/** Dictionnaire français (20 000 mots courants, du plus fréquent au moins fréquent) : suggestions et correction. */
object FrDict {
    @Volatile private var rank: HashMap<String, Int>? = null
    @Volatile private var list: List<String> = emptyList()
    @Volatile private var loading = false
    private const val ALPHA = "abcdefghijklmnopqrstuvwxyzàâçéèêëîïôùûüÿœ"

    fun load(ctx: Context) {
        if (rank != null || loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            try {
                val words = app.assets.open("fr_words.txt").bufferedReader(Charsets.UTF_8).readLines()
                val m = HashMap<String, Int>(words.size * 2)
                words.forEachIndexed { i, w -> m[w] = i }
                list = words
                rank = m
            } catch (e: Exception) {
            } finally {
                loading = false
            }
        }.start()
    }

    fun ready(): Boolean = rank != null

    fun prefix(p: String, n: Int, skip: Collection<String>): List<String> {
        val out = ArrayList<String>()
        for (w in list) {
            if (out.size >= n) break
            if (w.length > p.length && w.startsWith(p) && w !in skip) out.add(w)
        }
        return out
    }

    private fun stripAccents(s: String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(
            when (c) {
                'à', 'â' -> 'a'
                'ç' -> 'c'
                'é', 'è', 'ê', 'ë' -> 'e'
                'î', 'ï' -> 'i'
                'ô' -> 'o'
                'ù', 'û', 'ü' -> 'u'
                'ÿ' -> 'y'
                else -> c
            }
        )
        return sb.toString()
    }

    private fun edits1(w: String): Set<String> {
        val out = HashSet<String>()
        for (i in w.indices) {
            out.add(w.removeRange(i, i + 1))
            if (i + 1 < w.length) out.add(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
            for (c in ALPHA) if (c != w[i]) out.add(w.substring(0, i) + c + w.substring(i + 1))
        }
        for (i in 0..w.length) for (c in ALPHA) out.add(w.substring(0, i) + c + w.substring(i))
        return out
    }

    /** Le mot corrigé, ou null si le mot est correct, inconnu sans correction évidente, ou si on ne sait pas. */
    fun correct(ctx: Context, typed: String): String? {
        val m = rank ?: return null
        val w = typed.lowercase()
        if (w.length < 3 || w.length > 20 || !w.all { it.isLetter() }) return null
        if (WordStore.isSaved(ctx, w)) return null
        val own = m[w]
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        for (c in edits1(w)) {
            val r = m[c] ?: continue
            if (r < bestRank) {
                bestRank = r
                best = c
            }
        }
        val b = best ?: return null
        if (own == null) {
            if (w.length <= 4 && bestRank > 5000) return null
            return b
        }
        // Mot connu : on ne corrige que l'oubli d'accent évident (etre → être, tres → très).
        if (own > 300 && stripAccents(b) == w && b != w && bestRank * 6 < own) return b
        return null
    }
}

/** Historique du presse-papier : les textes copiés ou coupés, le plus récent en premier. */
object ClipStore {
    private const val KEY = "clips"
    private const val MAX = 40

    fun all(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, list: List<String>) {
        val a = JSONArray()
        for (s in list) a.put(s)
        prefs(ctx).edit().putString(KEY, a.toString()).apply()
    }

    fun add(ctx: Context, text: String) {
        val list = ArrayList(all(ctx))
        list.remove(text)
        list.add(0, text)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    fun remove(ctx: Context, text: String) {
        val list = ArrayList(all(ctx))
        list.remove(text)
        save(ctx, list)
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }
}

/**
 * Mots que l'utilisateur tape souvent. Chaque mot terminé (espace, point…) compte une fois ;
 * un mot proposé n'apparaît qu'à partir de 2 utilisations, ou tout de suite s'il a été enregistré d'un appui.
 */
object WordStore {
    private const val KEY = "words"
    private const val MAX = 1500
    private var cache: HashMap<String, Int>? = null

    private val BUILTIN: List<String> = (
        "bonjour bonsoir salut merci beaucoup toujours aujourd demain hier maintenant bientôt déjà encore jamais souvent " +
            "parfois ensemble aussi alors donc mais pour avec sans dans chez vers très trop assez plus moins bien mal " +
            "comment pourquoi quand combien quelle quelqu avez avons êtes sommes suis veux voulez voudrais peux pouvez " +
            "pouvoir faire fais faites allez allons envoyer envoie message messages appeler appelle téléphone numéro " +
            "réponse répondre question problème solution travail maison école église famille ami amie frère sœur " +
            "enfants enfant femme mari père mère papa maman monsieur madame directeur professeur élève élèves classe " +
            "examen résultats bulletin paiement argent dollars francs prix payer reçu reçue commande livraison " +
            "disponible important urgent rendez réunion semaine mois année heure minute matin après-midi soir nuit " +
            "lundi mardi mercredi jeudi vendredi samedi dimanche janvier février mars avril juin juillet août " +
            "septembre octobre novembre décembre félicitations bienvenue désolé pardon excusez attention bonne " +
            "bonnes journée soirée nuit courage santé bénédiction dieu seigneur prière amen joyeux anniversaire " +
            "mariage invitation confirmer confirmé confirmation vérifier information informations nouvelle " +
            "nouveau nouvelles ensuite avant pendant depuis jusqu parce quand lorsque pourtant cependant " +
            "malheureusement heureusement certainement probablement exactement vraiment seulement simplement " +
            "directement rapidement immédiatement également autre autres chaque tous toutes tout toute rien " +
            "personne quelque chose choses gens monde pays ville quartier avenue commune kinshasa congo lubumbashi " +
            "goma bukavu application téléphone clavier internet réseau connexion compte mot passe photo vidéo " +
            "audio fichier document page site lien disponible demande accord d'accord compris entendu d'ici " +
            "voici voilà peut-être sûr certain possible impossible facile difficile rapide lent grand petit " +
            "beau belle joli content contente heureux triste fatigué malade occupé libre prêt prête"
        ).split(" ").filter { it.length > 2 && it.all { c -> c.isLetter() } }

    private fun map(ctx: Context): HashMap<String, Int> {
        cache?.let { return it }
        val m = HashMap<String, Int>()
        val raw = prefs(ctx).getString(KEY, null)
        if (raw != null) {
            try {
                val o = JSONObject(raw)
                for (k in o.keys()) m[k] = o.optInt(k, 1)
            } catch (e: Exception) {
            }
        }
        cache = m
        return m
    }

    private fun save(ctx: Context) {
        val m = map(ctx)
        if (m.size > MAX) {
            val keep = m.entries.sortedByDescending { it.value }.take(MAX - 100)
            m.clear()
            for (e in keep) m[e.key] = e.value
        }
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }

    fun learn(ctx: Context, word: String, boost: Boolean = false) {
        val w = word.lowercase()
        if (w.length < 3 || !w.all { it.isLetter() }) return
        val m = map(ctx)
        m[w] = (m[w] ?: 0) + (if (boost) 3 else 1)
        save(ctx)
    }

    fun forget(ctx: Context, word: String) {
        map(ctx).remove(word.lowercase())
        save(ctx)
    }

    fun clear(ctx: Context) {
        map(ctx).clear()
        prefs(ctx).edit().remove(KEY).apply()
    }

    fun isSaved(ctx: Context, word: String): Boolean = (map(ctx)[word.lowercase()] ?: 0) >= 2

    fun count(ctx: Context): Int = map(ctx).size

    /** Jusqu'à [n] mots qui commencent par [prefix] : d'abord les tiens, puis la liste de base. */
    fun suggest(ctx: Context, prefix: String, n: Int): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        map(ctx).entries
            .filter { it.value >= 2 && it.key.startsWith(p) && it.key != p }
            .sortedByDescending { it.value }
            .forEach { if (out.size < n) out.add(it.key) }
        if (FrDict.ready()) {
            if (out.size < n) out.addAll(FrDict.prefix(p, n - out.size, out))
            return out
        }
        for (w in BUILTIN) {
            if (out.size >= n) break
            if (w.startsWith(p) && w != p && w !in out) out.add(w)
        }
        return out
    }
}
