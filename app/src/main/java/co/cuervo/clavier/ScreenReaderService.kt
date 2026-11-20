package co.cuervo.clavier

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lecture d'écran À LA DEMANDE : le service ne fait rien tant que l'utilisateur ne touche pas
 * le bouton « Répondre » du clavier. Il ignore les champs de mot de passe et le champ de saisie.
 */
class ScreenReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // volontairement vide : aucune surveillance en continu
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val out = ArrayList<String>()
        collect(root, out, 0)
        val lines = ArrayList<String>()
        for (l in out) if (lines.isEmpty() || lines.last() != l) lines.add(l)
        return lines.joinToString("\n")
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (n == null || depth > 40) return
        if (n.isPassword) return
        if (n.isVisibleToUser && !n.isEditable) {
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.length < 1500) out.add(t)
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    companion object {
        @Volatile
        var instance: ScreenReaderService? = null
    }
}
