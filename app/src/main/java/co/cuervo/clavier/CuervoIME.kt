package co.cuervo.clavier

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private val AI_ACTIONS = listOf(
    "Corriger" to "corriger",
    "Reformuler" to "reformuler",
    "Pro" to "pro",
    "Amical" to "amical",
    "Court" to "court",
    "Anglais" to "en",
    "Français" to "fr",
    "Lingala" to "ln",
    "Suite" to "continuer"
)

private val EMOJI_TABS = listOf("⭐", "😀", "👍", "❤️", "🌴")

private val EMOJI_SETS = listOf(
    "",
    "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 🤗 🤔 🤭 😐 😶 🙄 😏 😴 😌 😎 🤓 😕 😟 🙁 😮 😲 😳 🥺 😢 😭 😱 😖 😞 😤 😡 🤬 🥵 🥶 🤯 😷 🤒 🤢 🥳 😈 💀 👻 🤡",
    "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🖐️ 👋 🤚 👏 🙌 👐 🤲 🙏 🤝 💪 ✍️ 🤳 💅 👀 👅 👄 🧠 🙈 🙉 🙊",
    "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ✅ ❌ ⭕ ❗ ❓ ‼️ ⁉️ 💯 🔥 ⭐ 🌟 ✨ ⚡ 💥 💫 🎉 🎊 🎁 🏆 🥇 💰 💎 🔔 📌",
    "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦅 🦋 🐝 🐢 🐍 🐟 🐘 🌳 🌴 🌹 🌻 🌞 🌙 🌈 ☔ 🍎 🍌 🍉 🍍 🥭 🍞 🍗 🍖 🍟 🍔 🍕 🥚 🍚 🍺 🥤 ☕ 🍰 ⚽ 🏀 🎵 🎧 📱 💻 🚗 🏍️ ✈️ 🏠 ⏰"
)

private val QWERTY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val AZERTY_ROWS = listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
private val SYM1_ROWS = listOf("@#€%&-+()/", "*\"':;!?_=", "~<>{}[]")
private val SYM2_ROWS = listOf("£\$¥^°|\\©®™", "§¶•√π÷×¿¡", "«»“”‘’…")

/** Appui long : variantes de la touche (accents, ponctuation). */
private val VARIANTS = mapOf(
    "a" to "àâäæ",
    "e" to "éèêë",
    "i" to "îï",
    "o" to "ôöœ",
    "u" to "ùûü",
    "c" to "ç",
    "y" to "ÿ",
    "n" to "ñ",
    "." to "…!?:;",
    "-" to "–—•",
    "\"" to "«»“”",
    "'" to "‘’",
    "0" to "°",
    "?" to "¿",
    "!" to "¡"
)

class CuervoIME : InputMethodService() {

    private val ui = Handler(Looper.getMainLooper())

    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var chipRow: LinearLayout
    private lateinit var accentStrip: LinearLayout
    private lateinit var status: TextView
    private lateinit var keysBox: LinearLayout
    private lateinit var emojiBox: LinearLayout
    private lateinit var undoChip: TextView
    private lateinit var suggestStrip: LinearLayout
    private lateinit var clipBox: LinearLayout
    private var textField = false        // champ de texte (boutons IA)
    private var fixOn = false            // suggestions et correction automatique actives
    private var lastAuto: Triple<String, String, String>? = null   // mot tapé, mot corrigé, séparateur

    private val letterKeys = ArrayList<Pair<TextView, String>>()
    private var shiftIcon: ImageView? = null

    private var shift = false
    private var caps = false
    private var lastShiftTap = 0L
    private var layer = 0            // 0 lettres, 1 symboles, 2 autres symboles
    private var emojiTab = 0
    private var secure = false       // champ mot de passe : IA désactivée
    private var autoCap = false
    private var enterGlyph = ""      // vide = icône « entrée » par défaut

    private var lastOriginal: String? = null
    private var lastInserted: String? = null

    private val hideStatus = Runnable { status.visibility = View.GONE }
    private val hideAccents = Runnable { closeAccents() }

    // ---------------------------------------------------------------- cycle de vie

    override fun onEvaluateFullscreenMode(): Boolean = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    override fun onCreate() {
        super.onCreate()
        FrDict.load(this)
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    /** Enregistre dans l'historique ce qui vient d'être copié ou coupé (sauf contenus signalés sensibles). */
    private fun captureClip() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val desc = clip.description
            if (desc.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
            if (!desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return
            val t = clip.getItemAt(0).coerceToText(this)?.toString()?.trim() ?: return
            if (t.isEmpty() || t.length > 2000) return
            ClipStore.add(this, t)
            if (::clipBox.isInitialized && clipBox.visibility == View.VISIBLE) renderClip()
        } catch (e: Exception) {
        }
    }

    override fun onCreateInputView(): View {
        EmojiStore.ensureInstalled(this)

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.INK)
        root.setPadding(dp(4), dp(2), dp(4), dp(8))

        // Barre d'outils (réglages, émojis, actions IA) + bandeau d'accents qui la recouvre à la demande
        val top = FrameLayout(this)
        toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.addView(iconButton(R.drawable.ic_sliders) { openSettings() })
        toolbar.addView(iconButton(R.drawable.ic_emoji) { openEmoji() })
        toolbar.addView(iconButton(R.drawable.ic_clip) { openClip() })
        val sep = View(this)
        sep.setBackgroundColor(Ui.OUTLINE)
        toolbar.addView(sep, LinearLayout.LayoutParams(dp(1), dp(20)))

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.gravity = Gravity.CENTER_VERTICAL
        scroll.addView(chipRow)
        toolbar.addView(scroll, LinearLayout.LayoutParams(0, MATCH, 1f))
        buildChips()

        accentStrip = LinearLayout(this)
        accentStrip.orientation = LinearLayout.HORIZONTAL
        accentStrip.gravity = Gravity.CENTER
        accentStrip.visibility = View.GONE
        accentStrip.setBackgroundColor(Ui.INK)

        suggestStrip = LinearLayout(this)
        suggestStrip.orientation = LinearLayout.HORIZONTAL
        suggestStrip.gravity = Gravity.CENTER_VERTICAL
        suggestStrip.visibility = View.GONE
        suggestStrip.setBackgroundColor(Ui.INK)

        top.addView(toolbar, FrameLayout.LayoutParams(MATCH, MATCH))
        top.addView(suggestStrip, FrameLayout.LayoutParams(MATCH, MATCH))
        top.addView(accentStrip, FrameLayout.LayoutParams(MATCH, MATCH))

        status = TextView(this)
        status.setTextColor(Ui.TEXT)
        status.textSize = 12.5f
        status.typeface = Ui.MEDIUM
        status.gravity = Gravity.CENTER
        status.setPadding(dp(12), dp(6), dp(12), dp(6))
        status.background = Ui.round(Ui.KEY, dp(12))
        status.visibility = View.GONE

        keysBox = LinearLayout(this)
        keysBox.orientation = LinearLayout.VERTICAL

        emojiBox = LinearLayout(this)
        emojiBox.orientation = LinearLayout.VERTICAL
        emojiBox.visibility = View.GONE

        clipBox = LinearLayout(this)
        clipBox.orientation = LinearLayout.VERTICAL
        clipBox.visibility = View.GONE

        root.addView(top, LinearLayout.LayoutParams(MATCH, dp(44)))
        val slp = LinearLayout.LayoutParams(MATCH, WRAP)
        slp.setMargins(dp(4), 0, dp(4), dp(4))
        root.addView(status, slp)
        root.addView(keysBox, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(emojiBox, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(clipBox, LinearLayout.LayoutParams(MATCH, WRAP))

        renderKeys()
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::root.isInitialized) return

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        secure = (cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        val isText = cls == InputType.TYPE_CLASS_TEXT
        layer = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) 1 else 0
        autoCap = (info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        val empty = currentInputConnection?.getTextBeforeCursor(1, 0).isNullOrEmpty()
        shift = autoCap && empty
        caps = false

        enterGlyph = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_GO -> "→"
            EditorInfo.IME_ACTION_NEXT -> "→"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> ""
        }

        textField = isText && !secure
        fixOn = textField && variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
                variation != InputType.TYPE_TEXT_VARIATION_URI &&
                variation != InputType.TYPE_TEXT_VARIATION_FILTER &&
                variation != InputType.TYPE_TEXT_VARIATION_PERSON_NAME &&
                (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        lastAuto = null
        chipRow.visibility = if (textField) View.VISIBLE else View.INVISIBLE
        closeAccents()
        status.visibility = View.GONE
        keysBox.visibility = View.VISIBLE
        emojiBox.visibility = View.GONE
        clipBox.visibility = View.GONE
        renderKeys()
        captureClip()
        updateSuggestions()
    }

    // ---------------------------------------------------------------- clavier

    private fun renderKeys() {
        keysBox.removeAllViews()
        letterKeys.clear()
        shiftIcon = null
        val azerty = Prefs.layout(this) == "azerty"

        // Rangée de chiffres (toujours visible)
        val nums = ArrayList<View>()
        for (c in "1234567890") nums.add(charKey(c.toString(), 1f, false, 17f, Ui.TEXT_DIM))
        keysBox.addView(row(40, nums))

        val rows: List<String> = when {
            layer == 1 -> SYM1_ROWS
            layer == 2 -> SYM2_ROWS
            azerty -> AZERTY_ROWS
            else -> QWERTY_ROWS
        }
        val letters = layer == 0

        val r1 = ArrayList<View>()
        for (c in rows[0]) r1.add(charKey(c.toString(), 1f, letters))
        keysBox.addView(row(48, r1))

        val r2 = ArrayList<View>()
        val pad = rows[1].length < 10
        if (pad) r2.add(spacer(0.5f))
        for (c in rows[1]) r2.add(charKey(c.toString(), 1f, letters))
        if (pad) r2.add(spacer(0.5f))
        keysBox.addView(row(48, r2))

        val wide = if (rows[2].length >= 7) 1.5f else 2f
        val r3 = ArrayList<View>()
        if (layer == 0) {
            val sk = iconKey(R.drawable.ic_shift, wide, Ui.KEY_ALT)
            attachTouch(sk, 0L, null) { onShiftTap() }
            shiftIcon = sk
            r3.add(sk)
        } else if (layer == 1) {
            val k = keyText("=\\<", wide, Ui.KEY_ALT, 15f)
            attachTouch(k, 0L, null) { layer = 2; renderKeys() }
            r3.add(k)
        } else {
            val k = keyText("!#1", wide, Ui.KEY_ALT, 15f)
            attachTouch(k, 0L, null) { layer = 1; renderKeys() }
            r3.add(k)
        }
        for (c in rows[2]) r3.add(charKey(c.toString(), 1f, letters))
        r3.add(backspaceKey(wide))
        keysBox.addView(row(48, r3))

        val bottom = ArrayList<View>()
        val sw = keyText(if (layer == 0) "!#1" else "ABC", 1.5f, Ui.KEY_ALT, 15f)
        attachTouch(sw, 0L, null) {
            layer = if (layer == 0) 1 else 0
            renderKeys()
        }
        bottom.add(sw)
        bottom.add(charKey(",", 1f, false))
        val space = keyText("Français", 5f, Ui.KEY, 13f, Ui.MUTED)
        attachTouch(space, 450L, {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }) { commit(" ") }
        bottom.add(space)
        bottom.add(charKey(".", 1f, false))

        val enter: View = if (enterGlyph.isEmpty()) {
            iconKey(R.drawable.ic_enter, 1.6f, Ui.GREEN, Color.WHITE, Ui.GREEN_LIGHT, Ui.GREEN_DEEP)
        } else {
            keyText(enterGlyph, 1.6f, Ui.GREEN, 20f, Color.WHITE, Ui.GREEN_LIGHT, Ui.GREEN_DEEP)
        }
        attachTouch(enter, 0L, null) { pressEnter() }
        bottom.add(enter)
        keysBox.addView(row(48, bottom))

        refreshShift()
    }

    private fun charKey(c: String, weight: Float, isLetter: Boolean, size: Float = 21f, color: Int = Ui.TEXT): TextView {
        val t = keyText(c, weight, Ui.KEY, size, color)
        if (isLetter) letterKeys.add(Pair(t, c))
        val variants = VARIANTS[c]
        var onLong: (() -> Unit)? = null
        if (variants != null) onLong = { showVariants(variants) }
        attachTouch(t, 380L, onLong) {
            val out = if (isLetter && (shift || caps)) c.uppercase() else c
            commit(out)
            if (isLetter && shift && !caps) {
                shift = false
                refreshShift()
            }
        }
        return t
    }

    private fun refreshShift() {
        val up = shift || caps
        for ((t, c) in letterKeys) t.text = if (up) c.uppercase() else c
        val icon = shiftIcon
        if (icon != null) {
            icon.setImageResource(if (caps) R.drawable.ic_shift_fill else R.drawable.ic_shift)
            icon.setColorFilter(if (up) Ui.SUN else Ui.TEXT)
        }
    }

    private fun onShiftTap() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTap < 400) {
            caps = true
            shift = true
        } else if (caps) {
            caps = false
            shift = false
        } else {
            shift = !shift
        }
        lastShiftTap = now
        refreshShift()
    }

    private fun showVariants(variants: String) {
        accentStrip.removeAllViews()
        val up = shift || caps
        for (ch in variants) {
            val s = if (up) ch.toString().uppercase() else ch.toString()
            val v = TextView(this)
            v.text = s
            v.setTextColor(Ui.TEXT)
            v.textSize = 22f
            v.typeface = Ui.MEDIUM
            v.gravity = Gravity.CENTER
            v.background = Ui.keyBg(this, Ui.KEY_ALT)
            v.setOnClickListener {
                haptic(it)
                commit(s)
                if (shift && !caps) {
                    shift = false
                    refreshShift()
                }
                closeAccents()
            }
            val lp = LinearLayout.LayoutParams(dp(46), MATCH)
            lp.setMargins(dp(3), dp(4), dp(3), dp(4))
            accentStrip.addView(v, lp)
        }
        toolbar.visibility = View.INVISIBLE
        accentStrip.visibility = View.VISIBLE
        ui.removeCallbacks(hideAccents)
        ui.postDelayed(hideAccents, 4000)
    }

    private fun closeAccents() {
        if (!::accentStrip.isInitialized) return
        ui.removeCallbacks(hideAccents)
        accentStrip.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
    }

    private fun commit(text: String) {
        val ic = currentInputConnection ?: return
        lastAuto = null
        if (fixOn && text.isNotEmpty() && !text[0].isLetter()) {
            val fixed = autoCorrect()
            if (fixed == null) learnCurrentWord() else WordStore.learn(this, fixed.second)
            ic.commitText(text, 1)
            if (fixed != null) lastAuto = Triple(fixed.first, fixed.second, text)
        } else {
            ic.commitText(text, 1)
        }
        if (autoCap && text == " ") {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before == ". " || before == "? " || before == "! ") {
                shift = true
                refreshShift()
            }
        }
        updateSuggestions()
    }

    private fun deleteBack() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        val la = lastAuto
        lastAuto = null
        if (la != null && sel.isNullOrEmpty()) {
            // Premier retour arrière après une correction : on rend le mot tel que tu l'avais tapé.
            val tail = la.second + la.third
            if (ic.getTextBeforeCursor(tail.length, 0)?.toString() == tail) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(tail.length, 0)
                ic.commitText(la.first, 1)
                ic.endBatchEdit()
                WordStore.learn(this, la.first, true)
                showStatus("« ${la.first} » gardé tel quel.", 2000)
                updateSuggestions()
                return
            }
        }
        if (!sel.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateSuggestions()
    }

    // ---------------------------------------------------------------- suggestions de mots

    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString() ?: return ""
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        return before.substring(i)
    }

    /** Corrige le mot qui précède le curseur. Renvoie (mot tapé, mot corrigé) ou null si rien n'a changé. */
    private fun autoCorrect(): Pair<String, String>? {
        if (!Prefs.autoCorrect(this)) return null
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: return null
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        val typed = before.substring(i)
        if (typed.length < 3) return null
        if (i > 0 && before[i - 1] in "0123456789@/#_.:") return null
        if (typed[0].isUpperCase()) {
            val sentenceStart = before.substring(0, i).trimEnd(' ', '\t').let { it.isEmpty() || it.last() in ".!?\n" }
            val normalCap = typed.length > 1 && typed.substring(1).all { it.isLowerCase() }
            if (!sentenceStart || !normalCap) return null
        } else if (typed.any { it.isUpperCase() }) {
            return null
        }
        val fixed = FrDict.correct(this, typed) ?: return null
        val out = if (typed[0].isUpperCase()) fixed.replaceFirstChar { it.uppercase() } else fixed
        if (out == typed) return null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.commitText(out, 1)
        ic.endBatchEdit()
        return Pair(typed, out)
    }

    private fun learnCurrentWord() {
        val w = currentWord()
        if (w.length >= 3) WordStore.learn(this, w)
    }

    private fun matchCase(typed: String, word: String): String =
        if (typed.isNotEmpty() && typed[0].isUpperCase()) word.replaceFirstChar { it.uppercase() } else word

    private fun updateSuggestions() {
        if (!::suggestStrip.isInitialized) return
        val word = if (fixOn && layer == 0) currentWord() else ""
        if (word.isEmpty()) {
            suggestStrip.visibility = View.GONE
            return
        }
        val cands = WordStore.suggest(this, word, 2).map { matchCase(word, it) }
        if (cands.isEmpty() && word.length < 3) {
            suggestStrip.visibility = View.GONE
            return
        }
        suggestStrip.removeAllViews()
        val saved = WordStore.isSaved(this, word)
        val literal = suggestCell(if (saved) word else "＋ $word", true) { saveTypedWord(word) }
        val order = ArrayList<View>()
        if (cands.isNotEmpty()) order.add(suggestCell(cands[0], false) { applySuggestion(word, cands[0]) }.also { cell ->
            cell.setOnLongClickListener { forgetSuggestion(cands[0]); true }
        })
        order.add(literal)
        if (cands.size > 1) order.add(suggestCell(cands[1], false) { applySuggestion(word, cands[1]) }.also { cell ->
            cell.setOnLongClickListener { forgetSuggestion(cands[1]); true }
        })
        for (v in order) {
            val lp = LinearLayout.LayoutParams(0, MATCH, 1f)
            lp.setMargins(dp(3), dp(4), dp(3), dp(4))
            suggestStrip.addView(v, lp)
        }
        suggestStrip.visibility = View.VISIBLE
    }

    private fun suggestCell(label: String, literal: Boolean, onTap: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.setTextColor(if (literal) Ui.SUN else Ui.TEXT)
        t.textSize = 15f
        t.typeface = Ui.MEDIUM
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.background = Ui.keyBg(this, if (literal) Ui.KEY else Ui.KEY_ALT)
        t.setOnClickListener {
            haptic(it)
            onTap()
        }
        return t
    }

    private fun applySuggestion(typed: String, sugg: String) {
        val ic = currentInputConnection ?: return
        lastAuto = null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$sugg ", 1)
        ic.endBatchEdit()
        WordStore.learn(this, sugg)
        if (shift && !caps) {
            shift = false
            refreshShift()
        }
        updateSuggestions()
    }

    private fun saveTypedWord(word: String) {
        WordStore.learn(this, word, true)
        showStatus("« ${word.lowercase()} » enregistré dans tes mots.")
        updateSuggestions()
    }

    private fun forgetSuggestion(word: String) {
        WordStore.forget(this, word)
        showStatus("« ${word.lowercase()} » retiré de tes mots.")
        updateSuggestions()
    }

    private fun backspaceKey(weight: Float): ImageView {
        val v = iconKey(R.drawable.ic_backspace, weight, Ui.KEY_ALT)
        val repeat = object : Runnable {
            override fun run() {
                deleteBack()
                ui.postDelayed(this, 55)
            }
        }
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    haptic(view)
                    deleteBack()
                    ui.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    ui.removeCallbacks(repeat)
                }
            }
            true
        }
        return v
    }

    private fun pressEnter() {
        lastAuto = null
        if (fixOn) {
            val fixed = autoCorrect()
            if (fixed == null) learnCurrentWord() else WordStore.learn(this, fixed.second)
        }
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ---------------------------------------------------------------- IA

    private fun buildChips() {
        chipRow.removeAllViews()
        undoChip = pill("Annuler", 2, 0) { undo() }
        undoChip.visibility = View.GONE
        chipRow.addView(undoChip)
        chipRow.addView(pill("Répondre", 1, R.drawable.ic_reply) { runAi("repondre") })
        for ((label, action) in AI_ACTIONS) {
            chipRow.addView(pill(label, 0, 0) { runAi(action) })
        }
    }

    private fun runAi(action: String) {
        if (secure) return
        if (action == "repondre") {
            runReply()
            return
        }
        val ic = currentInputConnection ?: return
        if (Prefs.url(this).isBlank()) {
            showStatus("Configure l'IA dans l'application « Cuervo Clavier ».", 5000)
            return
        }
        val selected = ic.getSelectedText(0)?.toString()
        val useSel = !selected.isNullOrBlank()
        val src: String = if (!selected.isNullOrBlank()) selected else (ic.getTextBeforeCursor(1500, 0)?.toString() ?: "")
        if (src.isBlank()) {
            showStatus("Écris d'abord du texte (ou sélectionne-le).")
            return
        }
        showStatus("L'IA travaille…", 30000)
        val ctx: Context = this
        Thread {
            val r = AiClient.call(ctx, action, src)
            ui.post {
                val value = r.getOrNull()
                if (value != null) {
                    applyResult(src, useSel, value, action == "continuer")
                } else {
                    showStatus("⚠ " + (r.exceptionOrNull()?.message ?: "Erreur IA"), 5000)
                }
            }
        }.start()
    }

    /** Lit la conversation affichée (ou le presse-papiers) et propose une réponse. */
    private fun runReply() {
        val ic = currentInputConnection ?: return
        if (Prefs.url(this).isBlank()) {
            showStatus("Configure l'IA dans l'application « Cuervo Clavier ».", 5000)
            return
        }
        var screen = ScreenReaderService.instance?.readScreen() ?: ""
        if (screen.isBlank()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            screen = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        }
        if (screen.isBlank()) {
            showStatus("Active la lecture d'écran dans l'application, ou copie le message puis réessaie.", 6000)
            return
        }
        val src = screen.takeLast(2500)
        showStatus("L'IA lit la conversation…", 30000)
        val ctx: Context = this
        Thread {
            val r = AiClient.call(ctx, "repondre", src)
            ui.post {
                val value = r.getOrNull()
                if (value == null) {
                    showStatus("⚠ " + (r.exceptionOrNull()?.message ?: "Erreur IA"), 5000)
                } else {
                    val clean = value.trim()
                    ic.commitText(clean, 1)
                    lastOriginal = ""
                    lastInserted = clean
                    undoChip.visibility = View.VISIBLE
                    showStatus("Réponse proposée : modifie-la puis envoie. Touche Répondre pour une autre.", 4000)
                }
            }
        }.start()
    }

    private fun applyResult(src: String, useSel: Boolean, result: String, append: Boolean) {
        val clean = result.trim()
        val ic = currentInputConnection
        if (ic == null) {
            copyToClipboard(clean)
            showStatus("Résultat copié (champ fermé).")
            return
        }

        if (append) {
            val lead = if (src.endsWith(" ") || src.endsWith("\n")) "" else " "
            val inserted = if (useSel) src + lead + clean else lead + clean
            ic.commitText(inserted, 1)
            lastOriginal = if (useSel) src else ""
            lastInserted = inserted
        } else {
            val unchanged = if (useSel) {
                ic.getSelectedText(0)?.toString() == src
            } else {
                ic.getTextBeforeCursor(src.length, 0)?.toString() == src
            }
            if (!unchanged) {
                copyToClipboard(clean)
                showStatus("Le texte a changé : résultat copié dans le presse-papiers.", 5000)
                return
            }
            if (!useSel) ic.deleteSurroundingText(src.length, 0)
            ic.commitText(clean, 1)
            lastOriginal = src
            lastInserted = clean
        }
        undoChip.visibility = View.VISIBLE
        showStatus("Fait", 1500)
    }

    private fun undo() {
        val ic = currentInputConnection ?: return
        val inserted = lastInserted ?: return
        val before = ic.getTextBeforeCursor(inserted.length, 0)?.toString()
        if (before == inserted) {
            ic.deleteSurroundingText(inserted.length, 0)
            ic.commitText(lastOriginal ?: "", 1)
        } else {
            showStatus("Impossible d'annuler : le texte a changé.")
        }
        lastInserted = null
        lastOriginal = null
        undoChip.visibility = View.GONE
    }

    private fun showStatus(msg: String, ms: Long = 3500) {
        status.text = msg
        status.visibility = View.VISIBLE
        ui.removeCallbacks(hideStatus)
        ui.postDelayed(hideStatus, ms)
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Cuervo", text))
    }

    private fun openSettings() {
        val i = Intent(this, SettingsActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ---------------------------------------------------------------- presse-papier

    private fun openClip() {
        closeAccents()
        keysBox.visibility = View.GONE
        emojiBox.visibility = View.GONE
        clipBox.visibility = View.VISIBLE
        renderClip()
    }

    private fun closeClip() {
        clipBox.visibility = View.GONE
        keysBox.visibility = View.VISIBLE
    }

    private fun renderClip() {
        clipBox.removeAllViews()

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        val back = keyText("ABC", 1.5f, Ui.KEY_ALT, 15f)
        back.setOnClickListener {
            haptic(it)
            closeClip()
        }
        head.addView(back)
        val title = TextView(this)
        title.text = "Presse-papier"
        title.setTextColor(Ui.TEXT)
        title.textSize = 16f
        title.typeface = Ui.BOLD
        title.gravity = Gravity.CENTER
        head.addView(title, LinearLayout.LayoutParams(0, MATCH, 3f))
        val clear = keyText("Tout effacer", 2f, Ui.KEY, 13f, Ui.MUTED)
        clear.setOnClickListener {
            haptic(it)
            ClipStore.clear(this)
            renderClip()
        }
        head.addView(clear)
        clipBox.addView(head, LinearLayout.LayoutParams(MATCH, dp(48)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val clips = ClipStore.all(this)
        if (clips.isEmpty()) {
            val e = TextView(this)
            e.text = "Rien pour l'instant.\nCopie ou coupe un texte : il apparaîtra ici."
            e.setTextColor(Ui.MUTED)
            e.textSize = 14f
            e.gravity = Gravity.CENTER
            e.setPadding(dp(16), dp(40), dp(16), dp(40))
            col.addView(e, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        for (text in clips) col.addView(clipRow(text))

        val scroll = ScrollView(this)
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        clipBox.addView(scroll, LinearLayout.LayoutParams(MATCH, dp(210 - 0)))
    }

    private fun clipRow(text: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL

        val t = TextView(this)
        t.text = text
        t.setTextColor(Ui.TEXT)
        t.textSize = 14f
        t.maxLines = 3
        t.ellipsize = android.text.TextUtils.TruncateAt.END
        t.setPadding(dp(14), dp(10), dp(14), dp(10))
        t.background = Ui.keyBg(this, Ui.KEY)
        t.setOnClickListener {
            haptic(it)
            currentInputConnection?.commitText(text, 1)
            closeClip()
            updateSuggestions()
        }
        r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))

        val x = TextView(this)
        x.text = "✕"
        x.setTextColor(Ui.MUTED)
        x.textSize = 16f
        x.gravity = Gravity.CENTER
        x.setOnClickListener {
            haptic(it)
            ClipStore.remove(this, text)
            renderClip()
        }
        r.addView(x, LinearLayout.LayoutParams(dp(44), dp(44)))

        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(dp(4), dp(3), dp(2), dp(3))
        r.layoutParams = lp
        return r
    }

    // ---------------------------------------------------------------- émojis

    private fun openEmoji() {
        closeAccents()
        clipBox.visibility = View.GONE
        keysBox.visibility = View.GONE
        emojiBox.visibility = View.VISIBLE
        renderEmoji()
    }

    private fun closeEmoji() {
        emojiBox.visibility = View.GONE
        keysBox.visibility = View.VISIBLE
    }

    private fun renderEmoji() {
        emojiBox.removeAllViews()

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        val back = keyText("ABC", 1.5f, Ui.KEY_ALT, 15f)
        back.setOnClickListener {
            haptic(it)
            closeEmoji()
        }
        tabs.addView(back)
        EMOJI_TABS.forEachIndexed { i, label ->
            val face = if (i == emojiTab) Ui.GREEN_DEEP else Ui.KEY
            val t = keyText(label, 1f, face, 19f)
            t.setOnClickListener {
                haptic(it)
                emojiTab = i
                renderEmoji()
            }
            tabs.addView(t)
        }
        tabs.addView(backspaceKey(1.5f))
        emojiBox.addView(tabs, LinearLayout.LayoutParams(MATCH, dp(48)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        if (emojiTab == 0) fillStickers(col) else fillEmojis(col, EMOJI_SETS[emojiTab])

        val scroll = ScrollView(this)
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        emojiBox.addView(scroll, LinearLayout.LayoutParams(MATCH, dp(210)))
    }

    private fun fillEmojis(col: LinearLayout, set: String) {
        val items = set.split(" ").filter { it.isNotBlank() }
        for (chunk in items.chunked(8)) {
            val cells = chunk.map { e ->
                val t = TextView(this)
                t.text = e
                t.textSize = 26f
                t.gravity = Gravity.CENTER
                t.setOnClickListener {
                    haptic(it)
                    commit(e)
                }
                t as View
            }
            col.addView(gridRow(cells, 8, 50, 0))
        }
    }

    private fun fillStickers(col: LinearLayout) {
        val cells = ArrayList<View>()
        for (s in EmojiStore.builtin) {
            val f = EmojiStore.builtinFile(this, s)
            if (f.exists()) cells.add(stickerCell(f, s.alias))
        }
        for (f in EmojiStore.userFiles(this)) cells.add(stickerCell(f, null))

        val plus = TextView(this)
        plus.text = "+"
        plus.textSize = 30f
        plus.setTextColor(Ui.MUTED)
        plus.gravity = Gravity.CENTER
        plus.background = Ui.outline(Color.TRANSPARENT, Ui.OUTLINE, dp(14), dp(1))
        plus.setOnClickListener {
            haptic(it)
            openSettings()
        }
        cells.add(plus)

        for (chunk in cells.chunked(4)) col.addView(gridRow(chunk, 4, 74, 4))
    }

    private fun stickerCell(file: File, alias: String?): View {
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.setPadding(dp(8), dp(8), dp(8), dp(8))
        img.background = Ui.round(Ui.KEY, dp(14))
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        if (bmp != null) img.setImageBitmap(bmp)
        img.setOnClickListener {
            haptic(it)
            sendSticker(file, alias)
        }
        return img
    }

    private fun gridRow(cells: List<View>, perRow: Int, heightDp: Int, gapDp: Int): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (c in cells) {
            val lp = LinearLayout.LayoutParams(0, dp(heightDp), 1f)
            lp.setMargins(dp(gapDp), dp(gapDp), dp(gapDp), dp(gapDp))
            r.addView(c, lp)
        }
        for (i in cells.size until perRow) {
            val lp = LinearLayout.LayoutParams(0, dp(heightDp), 1f)
            lp.setMargins(dp(gapDp), dp(gapDp), dp(gapDp), dp(gapDp))
            r.addView(View(this), lp)
        }
        return r
    }

    private fun supportsImages(info: EditorInfo): Boolean {
        val types = EditorInfoCompat.getContentMimeTypes(info)
        return types.any { it.equals("image/png", true) || it.equals("image/*", true) }
    }

    private fun sendSticker(file: File, alias: String?) {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        var ok = false
        if (info != null && supportsImages(info)) {
            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val desc = ClipDescription(file.nameWithoutExtension, arrayOf("image/png"))
                ok = InputConnectionCompat.commitContent(
                    ic, info, InputContentInfoCompat(uri, desc, null),
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
                )
            } catch (e: Exception) {
                ok = false
            }
        }
        if (!ok) {
            if (alias != null) {
                ic.commitText(alias, 1)
                toast("Cette application n'accepte pas les images : émoji standard envoyé.")
            } else {
                toast("Cette application n'accepte pas les émojis image.")
            }
        }
    }

    // ---------------------------------------------------------------- fabrique de vues

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun haptic(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun keyLp(weight: Float): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, MATCH, weight)
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
        return lp
    }

    private fun spacer(weight: Float): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(0, MATCH, weight)
        return v
    }

    private fun keyText(
        label: String, weight: Float, face: Int, size: Float = 21f, color: Int = Ui.TEXT,
        pressed: Int = Ui.KEY_PRESSED, edge: Int = Ui.KEY_EDGE
    ): TextView {
        val t = TextView(this)
        t.text = label
        t.setTextColor(color)
        t.textSize = size
        t.typeface = Ui.MEDIUM
        t.gravity = Gravity.CENTER
        t.includeFontPadding = false
        t.background = Ui.keyBg(this, face, pressed, edge)
        t.layoutParams = keyLp(weight)
        return t
    }

    private fun iconKey(
        res: Int, weight: Float, face: Int, tint: Int = Ui.TEXT,
        pressed: Int = Ui.KEY_PRESSED, edge: Int = Ui.KEY_EDGE
    ): ImageView {
        val v = ImageView(this)
        v.setImageResource(res)
        v.setColorFilter(tint)
        v.scaleType = ImageView.ScaleType.CENTER_INSIDE
        v.setPadding(dp(13), dp(13), dp(13), dp(13))
        v.background = Ui.keyBg(this, face, pressed, edge)
        v.layoutParams = keyLp(weight)
        return v
    }

    private fun row(heightDp: Int, views: List<View>): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (v in views) r.addView(v)
        r.layoutParams = LinearLayout.LayoutParams(MATCH, dp(heightDp))
        return r
    }

    /** Toucher : action au relâchement ; appui long optionnel. Les touches ne sont jamais recréées pendant la frappe. */
    private fun attachTouch(v: View, longMs: Long, onLong: (() -> Unit)?, onTap: () -> Unit) {
        var longFired = false
        val longRun = Runnable {
            longFired = true
            onLong?.invoke()
        }
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longFired = false
                    view.isPressed = true
                    haptic(view)
                    if (onLong != null) ui.postDelayed(longRun, longMs)
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(longRun)
                    view.isPressed = false
                    if (!longFired) onTap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(longRun)
                    view.isPressed = false
                }
            }
            true
        }
    }

    private fun iconButton(res: Int, onClick: () -> Unit): ImageView {
        val v = ImageView(this)
        v.setImageResource(res)
        v.setColorFilter(Ui.TEXT_DIM)
        v.scaleType = ImageView.ScaleType.CENTER_INSIDE
        v.setPadding(dp(11), dp(11), dp(11), dp(11))
        v.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        v.setOnClickListener {
            haptic(it)
            onClick()
        }
        return v
    }

    /** style : 0 contour, 1 vert plein, 2 jaune plein. */
    private fun pill(label: String, style: Int, iconRes: Int, onClick: () -> Unit): TextView {
        val v = TextView(this)
        v.text = label
        v.textSize = 13.5f
        v.typeface = Ui.MEDIUM
        v.gravity = Gravity.CENTER
        v.setPadding(dp(14), 0, dp(14), 0)
        when (style) {
            1 -> {
                v.setTextColor(Color.WHITE)
                v.background = Ui.round(Ui.GREEN, dp(16))
            }
            2 -> {
                v.setTextColor(Ui.INK)
                v.background = Ui.round(Ui.SUN, dp(16))
            }
            else -> {
                v.setTextColor(Ui.TEXT)
                v.background = Ui.outline(Ui.KEY, Ui.OUTLINE, dp(16), dp(1))
            }
        }
        if (iconRes != 0) {
            val d = getDrawable(iconRes)
            if (d != null) {
                d.setBounds(0, 0, dp(16), dp(16))
                v.setCompoundDrawables(d, null, null, null)
                v.compoundDrawablePadding = dp(6)
            }
        }
        val lp = LinearLayout.LayoutParams(WRAP, dp(32))
        lp.setMargins(dp(4), 0, dp(4), 0)
        v.layoutParams = lp
        v.setOnClickListener {
            haptic(it)
            onClick()
        }
        return v
    }
}
