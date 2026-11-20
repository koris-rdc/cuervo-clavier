package co.cuervo.clavier

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Écran d'accueil : installation guidée, essai, disposition, IA, émojis. Design « Encre & soleil ». */
class SettingsActivity : Activity() {

    private val reqImage = 41

    private lateinit var heroChip: TextView
    private lateinit var stepsBox: LinearLayout
    private lateinit var iaChip: TextView
    private lateinit var iaResult: TextView
    private lateinit var urlField: EditText
    private lateinit var keyField: EditText
    private lateinit var emojiBox: LinearLayout
    private lateinit var segQwerty: TextView
    private lateinit var segFixOn: TextView
    private lateinit var segFixOff: TextView
    private lateinit var segAzerty: TextView

    private val animated = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EmojiStore.ensureInstalled(this)
        window.statusBarColor = Ui.INK
        window.navigationBarColor = Ui.PAPER

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        col.addView(buildHero())

        // Installation guidée
        val steps = card()
        steps.addView(cardTitle("Installation", "Trois étapes, une seule fois."))
        stepsBox = LinearLayout(this)
        stepsBox.orientation = LinearLayout.VERTICAL
        steps.addView(stepsBox)
        col.addView(steps)

        // Essai
        val tryCard = card()
        tryCard.addView(cardTitle("Essayer le clavier", "Écris ici pour tester les touches, les accents (appui long) et les émojis."))
        val test = EditText(this)
        test.hint = "Écris ici…"
        test.minLines = 3
        test.gravity = Gravity.TOP
        test.textSize = 15f
        test.setTextColor(Ui.INK_TEXT)
        test.setHintTextColor(Ui.GREY_TEXT)
        test.setPadding(dp(14), dp(12), dp(14), dp(12))
        test.background = Ui.outline(Ui.PAPER, Ui.LINE, dp(14), dp(1))
        test.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        tryCard.addView(test, topMargin(12))
        col.addView(tryCard)

        // Disposition
        val layoutCard = card()
        layoutCard.addView(cardTitle("Disposition des touches", "QWERTY comme sur ton clavier habituel, ou AZERTY."))
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = Ui.round(Ui.PAPER, dp(14))
        seg.setPadding(dp(4), dp(4), dp(4), dp(4))
        segQwerty = segment("QWERTY") { chooseLayout("qwerty") }
        segAzerty = segment("AZERTY") { chooseLayout("azerty") }
        seg.addView(segQwerty, LinearLayout.LayoutParams(0, dp(42), 1f))
        seg.addView(segAzerty, LinearLayout.LayoutParams(0, dp(42), 1f))
        layoutCard.addView(seg, topMargin(12))
        col.addView(layoutCard)
        refreshSegments()

        // Correction des mots
        val fixCard = card()
        fixCard.addView(cardTitle("Correction des mots", "Corrige les fautes de frappe en français quand tu tapes un espace. Un retour arrière juste après remet ton mot."))
        val fixSeg = LinearLayout(this)
        fixSeg.orientation = LinearLayout.HORIZONTAL
        fixSeg.background = Ui.round(Ui.PAPER, dp(14))
        fixSeg.setPadding(dp(4), dp(4), dp(4), dp(4))
        segFixOn = segment("Activée") { chooseFix(true) }
        segFixOff = segment("Désactivée") { chooseFix(false) }
        fixSeg.addView(segFixOn, LinearLayout.LayoutParams(0, dp(42), 1f))
        fixSeg.addView(segFixOff, LinearLayout.LayoutParams(0, dp(42), 1f))
        fixCard.addView(fixSeg, topMargin(12))
        col.addView(fixCard)
        refreshFix()

        // IA
        val ia = card()
        ia.addView(cardTitle("Intelligence artificielle", "Corriger, reformuler, traduire, répondre. La clé reste sur ton serveur."))
        iaChip = pillLabel("")
        ia.addView(iaChip, wrapMargin(10))
        urlField = field("https://xxxx.supabase.co/functions/v1/ai-keyboard", false)
        urlField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        urlField.setText(Prefs.url(this))
        keyField = field("Mot de passe secret (CUERVO_TOKEN)", true)
        keyField.setText(Prefs.key(this))
        ia.addView(urlField, topMargin(12))
        ia.addView(keyField, topMargin(8))
        ia.addView(primaryButton("Enregistrer et tester") { saveAndTest() }, topMargin(12))
        iaResult = TextView(this)
        iaResult.textSize = 13f
        iaResult.setTextColor(Ui.GREY_TEXT)
        iaResult.visibility = View.GONE
        ia.addView(iaResult, topMargin(10))
        ia.addView(note("L'IA est désactivée dans les champs de mot de passe et ne reçoit du texte que lorsque tu touches un de ses boutons."), topMargin(10))
        col.addView(ia)
        refreshIaChip()

        // Émojis
        val em = card()
        em.addView(cardTitle("Mes émojis", "12 émojis Cuervo inclus. Ajoute les tiens ; touche l'un des tiens pour le supprimer."))
        emojiBox = LinearLayout(this)
        emojiBox.orientation = LinearLayout.VERTICAL
        em.addView(emojiBox, topMargin(10))
        em.addView(ghostButton("Ajouter un émoji (galerie)") {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            i.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(Intent.createChooser(i, "Choisir une image"), reqImage)
        }, topMargin(10))
        em.addView(note("Certaines applications (WhatsApp, Telegram…) acceptent les émojis image envoyés depuis un clavier ; les autres reçoivent l'émoji standard équivalent."), topMargin(10))
        col.addView(em)

        val foot = TextView(this)
        foot.text = "cuervo.co · Je transforme vos idées en expériences digitales"
        foot.textSize = 12f
        foot.setTextColor(Ui.GREY_TEXT)
        foot.gravity = Gravity.CENTER
        foot.setPadding(dp(24), dp(28), dp(24), dp(36))
        col.addView(foot)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.PAPER)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        // Entrée en cascade
        var delay = 0L
        val ease = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        for (v in animated) {
            v.alpha = 0f
            v.translationY = dp(16).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(380).setInterpolator(ease).start()
            delay += 60L
        }
    }

    override fun onResume() {
        super.onResume()
        renderSteps()
        refreshEmojis()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqImage && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                val ok = EmojiStore.addFromUri(this, uri)
                toast(if (ok) "Émoji ajouté" else "Image illisible")
                refreshEmojis()
            }
        }
    }

    // ---------------------------------------------------------------- sections

    private fun buildHero(): View {
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(dp(22), dp(22), dp(22), dp(30))
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.INK, Color.parseColor("#0F3D26")))
        val r = dp(30).toFloat()
        g.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        hero.background = g

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        val bmp = BitmapFactory.decodeFile(EmojiStore.builtinFile(this, EmojiStore.builtin[0]).absolutePath)
        if (bmp != null) logo.setImageBitmap(bmp)
        logo.setPadding(dp(8), dp(8), dp(8), dp(8))
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setColor(Ui.KEY)
        ring.setStroke(dp(2), Ui.SUN)
        logo.background = ring
        top.addView(logo, LinearLayout.LayoutParams(dp(60), dp(60)))
        val brand = TextView(this)
        brand.text = "CUERVO.CO"
        brand.textSize = 12f
        brand.letterSpacing = 0.18f
        brand.typeface = Ui.BOLD
        brand.setTextColor(Ui.SUN)
        val blp = LinearLayout.LayoutParams(WRAP, WRAP)
        blp.leftMargin = dp(14)
        top.addView(brand, blp)
        hero.addView(top)

        val title = TextView(this)
        title.text = "Cuervo Clavier"
        title.textSize = 32f
        title.typeface = Ui.BOLD
        title.setTextColor(Color.WHITE)
        hero.addView(title, topMargin(18))

        val sub = TextView(this)
        sub.text = "Écris plus vite. Réponds mieux."
        sub.textSize = 16f
        sub.setTextColor(Ui.TEXT_DIM)
        hero.addView(sub, topMargin(4))

        heroChip = TextView(this)
        heroChip.textSize = 13f
        heroChip.typeface = Ui.MEDIUM
        heroChip.setPadding(dp(14), dp(7), dp(14), dp(7))
        hero.addView(heroChip, wrapMargin(18))

        animated.add(hero)
        return hero
    }

    private fun renderSteps() {
        stepsBox.removeAllViews()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val isDefault = current.startsWith("$packageName/")
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val readerOn = services.contains("$packageName/")

        stepsBox.addView(stepRow(1, "Activer le clavier", "Autorise Cuervo Clavier dans la liste des claviers.", enabled, "Activer") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        stepsBox.addView(stepRow(2, "Choisir comme clavier", "Sélectionne-le dans la fenêtre. Il reste ton clavier tant que tu n'en changes pas.", isDefault, "Choisir") {
            imm.showInputMethodPicker()
        })
        stepsBox.addView(stepRow(3, "Lecture d'écran (facultatif)", "Pour que Répondre lise la conversation. Lecture uniquement quand tu touches le bouton.", readerOn, "Activer") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        val help = TextView(this)
        help.text = "Android bloque l'activation ? Ouvre les infos de l'application, touche ⋮ puis « Autoriser les paramètres restreints »."
        help.textSize = 12.5f
        help.setTextColor(Ui.GREEN_DEEP)
        help.paintFlags = help.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        help.setOnClickListener {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        }
        stepsBox.addView(help, topMargin(14))

        if (enabled && isDefault) {
            heroChip.text = "Prêt à l'emploi"
            heroChip.setTextColor(Ui.INK)
            heroChip.background = Ui.round(Ui.GREEN_LIGHT, dp(18))
        } else {
            heroChip.text = "Installation à terminer"
            heroChip.setTextColor(Ui.INK)
            heroChip.background = Ui.round(Ui.SUN, dp(18))
        }
    }

    private fun stepRow(n: Int, title: String, sub: String, done: Boolean, action: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = topMargin(14)

        val badge = TextView(this)
        badge.gravity = Gravity.CENTER
        badge.textSize = 14f
        badge.typeface = Ui.BOLD
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        if (done) {
            badge.text = "✓"
            badge.setTextColor(Color.WHITE)
            oval.setColor(Ui.GREEN)
        } else {
            badge.text = n.toString()
            badge.setTextColor(Ui.GREY_TEXT)
            oval.setColor(Color.TRANSPARENT)
            oval.setStroke(dp(1), Ui.LINE)
        }
        badge.background = oval
        row.addView(badge, LinearLayout.LayoutParams(dp(32), dp(32)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = title
        t.textSize = 15f
        t.typeface = Ui.BOLD
        t.setTextColor(Ui.INK_TEXT)
        val s = TextView(this)
        s.text = sub
        s.textSize = 12.5f
        s.setTextColor(Ui.GREY_TEXT)
        texts.addView(t)
        texts.addView(s)
        val tlp = LinearLayout.LayoutParams(0, WRAP, 1f)
        tlp.leftMargin = dp(12)
        tlp.rightMargin = dp(8)
        row.addView(texts, tlp)

        if (!done) {
            val b = TextView(this)
            b.text = action
            b.textSize = 13f
            b.typeface = Ui.MEDIUM
            b.setTextColor(Color.WHITE)
            b.gravity = Gravity.CENTER
            b.setPadding(dp(16), 0, dp(16), 0)
            b.background = Ui.round(Ui.GREEN, dp(18))
            b.setOnClickListener { onClick() }
            pressable(b)
            row.addView(b, LinearLayout.LayoutParams(WRAP, dp(36)))
        }
        return row
    }

    private fun chooseFix(on: Boolean) {
        Prefs.setAutoCorrect(this, on)
        refreshFix()
        toast(if (on) "Correction automatique activée" else "Correction automatique désactivée")
    }

    private fun refreshFix() {
        val on = Prefs.autoCorrect(this)
        styleSegment(segFixOn, on)
        styleSegment(segFixOff, !on)
    }

    private fun chooseLayout(value: String) {
        Prefs.setLayout(this, value)
        refreshSegments()
        toast(if (value == "azerty") "Disposition AZERTY" else "Disposition QWERTY")
    }

    private fun refreshSegments() {
        val azerty = Prefs.layout(this) == "azerty"
        styleSegment(segQwerty, !azerty)
        styleSegment(segAzerty, azerty)
    }

    private fun styleSegment(v: TextView, selected: Boolean) {
        if (selected) {
            v.setTextColor(Color.WHITE)
            v.background = Ui.round(Ui.GREEN, dp(11))
        } else {
            v.setTextColor(Ui.GREY_TEXT)
            v.background = Ui.round(Color.TRANSPARENT, dp(11))
        }
    }

    private fun segment(label: String, onClick: () -> Unit): TextView {
        val v = TextView(this)
        v.text = label
        v.textSize = 14f
        v.typeface = Ui.BOLD
        v.gravity = Gravity.CENTER
        v.setOnClickListener { onClick() }
        return v
    }

    private fun refreshIaChip() {
        if (Prefs.url(this).isBlank()) {
            iaChip.text = "À configurer"
            iaChip.setTextColor(Color.parseColor("#7A5B00"))
            iaChip.background = Ui.round(Ui.SOFT_SUN, dp(14))
        } else {
            iaChip.text = "Adresse enregistrée"
            iaChip.setTextColor(Ui.GREEN_DEEP)
            iaChip.background = Ui.round(Ui.SOFT_GREEN, dp(14))
        }
    }

    private fun saveAndTest() {
        Prefs.save(this, urlField.text.toString(), keyField.text.toString())
        refreshIaChip()
        iaResult.visibility = View.VISIBLE
        iaResult.setTextColor(Ui.GREY_TEXT)
        iaResult.text = "Test en cours…"
        val ctx: Context = this
        Thread {
            val r = AiClient.call(ctx, "corriger", "bonjour sa va bien, je suis content de te voir")
            runOnUiThread {
                val v = r.getOrNull()
                if (v != null) {
                    iaChip.text = "Connectée"
                    iaResult.setTextColor(Ui.GREEN_DEEP)
                    iaResult.text = "L'IA répond : " + v
                } else {
                    iaResult.setTextColor(Ui.RED)
                    iaResult.text = "Échec : " + (r.exceptionOrNull()?.message ?: "erreur inconnue")
                }
            }
        }.start()
    }

    private fun refreshEmojis() {
        emojiBox.removeAllViews()
        val cells = ArrayList<View>()
        for (s in EmojiStore.builtin) {
            val f = EmojiStore.builtinFile(this, s)
            if (f.exists()) cells.add(thumb(f, false))
        }
        for (f in EmojiStore.userFiles(this)) cells.add(thumb(f, true))
        for (chunk in cells.chunked(5)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (c in chunk) {
                val lp = LinearLayout.LayoutParams(0, dp(58), 1f)
                lp.setMargins(dp(3), dp(3), dp(3), dp(3))
                r.addView(c, lp)
            }
            for (i in chunk.size until 5) {
                val lp = LinearLayout.LayoutParams(0, dp(58), 1f)
                lp.setMargins(dp(3), dp(3), dp(3), dp(3))
                r.addView(View(this), lp)
            }
            emojiBox.addView(r)
        }
    }

    private fun thumb(f: File, deletable: Boolean): View {
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.setPadding(dp(8), dp(8), dp(8), dp(8))
        img.background = Ui.round(Ui.PAPER, dp(14))
        val bmp = BitmapFactory.decodeFile(f.absolutePath)
        if (bmp != null) img.setImageBitmap(bmp)
        if (deletable) {
            img.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Supprimer cet émoji ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        EmojiStore.delete(f)
                        refreshEmojis()
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
        return img
    }

    // ---------------------------------------------------------------- fabrique de vues

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun topMargin(dpValue: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(dpValue)
        return lp
    }

    private fun wrapMargin(dpValue: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = dp(dpValue)
        return lp
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(20), dp(20), dp(20), dp(20))
        c.background = Ui.outline(Ui.CARD, Ui.LINE, dp(22), dp(1))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(dp(16), dp(16), dp(16), 0)
        c.layoutParams = lp
        animated.add(c)
        return c
    }

    private fun cardTitle(title: String, sub: String): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = title
        t.textSize = 19f
        t.typeface = Ui.BOLD
        t.setTextColor(Ui.INK_TEXT)
        val s = TextView(this)
        s.text = sub
        s.textSize = 13.5f
        s.setTextColor(Ui.GREY_TEXT)
        s.setPadding(0, dp(3), 0, 0)
        box.addView(t)
        box.addView(s)
        return box
    }

    private fun note(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12.5f
        t.setTextColor(Ui.GREY_TEXT)
        return t
    }

    private fun pillLabel(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12.5f
        t.typeface = Ui.MEDIUM
        t.setPadding(dp(12), dp(5), dp(12), dp(5))
        t.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        return t
    }

    private fun field(hint: String, secret: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.textSize = 14f
        e.setTextColor(Ui.INK_TEXT)
        e.setHintTextColor(Color.parseColor("#9AA0AE"))
        e.setSingleLine(true)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.background = Ui.outline(Ui.PAPER, Ui.LINE, dp(14), dp(1))
        if (secret) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        return e
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 15f
        b.typeface = Ui.BOLD
        b.setTextColor(Color.WHITE)
        b.gravity = Gravity.CENTER
        b.background = Ui.round(Ui.GREEN, dp(16))
        b.minimumHeight = dp(50)
        b.setOnClickListener { onClick() }
        pressable(b)
        return b
    }

    private fun ghostButton(label: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 15f
        b.typeface = Ui.BOLD
        b.setTextColor(Ui.GREEN_DEEP)
        b.gravity = Gravity.CENTER
        b.background = Ui.outline(Color.TRANSPARENT, Ui.GREEN, dp(16), dp(1))
        b.minimumHeight = dp(50)
        b.setOnClickListener { onClick() }
        pressable(b)
        return b
    }

    /** Micro-interaction : léger rétrécissement au toucher, retour en ressort. */
    private fun pressable(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                        .setInterpolator(OvershootInterpolator()).start()
            }
            false
        }
    }
}
