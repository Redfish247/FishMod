package fishmod.features.storage

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

// Caret, selection and hotkeys for a text field the UI recorder draws itself
class SearchFieldState(private val maxLength: Int = 256) {
    var text = ""
        set(value) {
            field = value
            caret = caret.coerceIn(0, value.length)
            anchor = anchor.coerceIn(0, value.length)
        }
    var caret = 0
        private set
    private var anchor = 0

    val selStart get() = minOf(caret, anchor)
    val selEnd get() = maxOf(caret, anchor)
    val hasSelection get() = caret != anchor

    fun moveTo(pos: Int, extend: Boolean = false) {
        caret = pos.coerceIn(0, text.length)
        if (!extend) anchor = caret
    }

    fun selectAll() {
        anchor = 0
        caret = text.length
    }

    fun selectWordAt(pos: Int) {
        val p = pos.coerceIn(0, text.length)
        var start = p
        var end = p
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++
        anchor = start
        caret = end
    }

    fun insert(s: String) {
        val clean = s.filterNot { it.isISOControl() }
        val room = maxLength - (text.length - (selEnd - selStart))
        if (room <= 0 || clean.isEmpty()) return
        var cut = minOf(room, clean.length)
        if (cut < clean.length && clean[cut - 1].isHighSurrogate()) cut--
        replaceRange(selStart, selEnd, clean.substring(0, cut))
    }

    // Returns true when the key was a field hotkey
    fun keyPressed(key: Int, modifiers: Int): Boolean {
        val shift = modifiers and GLFW.GLFW_MOD_SHIFT != 0
        val alt = modifiers and GLFW.GLFW_MOD_ALT != 0
        val ctrl = !alt && modifiers and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_SUPER) != 0
        val clipboard = Minecraft.getInstance().keyboardHandler
        when (key) {
            GLFW.GLFW_KEY_LEFT -> if (hasSelection && !shift && !ctrl) moveTo(selStart) else moveTo(if (ctrl) prevWord() else prevChar(), shift)
            GLFW.GLFW_KEY_RIGHT -> if (hasSelection && !shift && !ctrl) moveTo(selEnd) else moveTo(if (ctrl) nextWord() else nextChar(), shift)
            GLFW.GLFW_KEY_HOME -> moveTo(0, shift)
            GLFW.GLFW_KEY_END -> moveTo(text.length, shift)
            GLFW.GLFW_KEY_BACKSPACE -> if (!deleteSelection()) replaceRange(if (ctrl) prevWord() else prevChar(), caret, "")
            GLFW.GLFW_KEY_DELETE -> if (!deleteSelection()) replaceRange(caret, if (ctrl) nextWord() else nextChar(), "")
            GLFW.GLFW_KEY_A -> if (ctrl) selectAll() else return false
            GLFW.GLFW_KEY_C -> if (ctrl) { if (hasSelection) clipboard.clipboard = selected() } else return false
            GLFW.GLFW_KEY_X -> if (ctrl) { if (hasSelection) { clipboard.clipboard = selected(); deleteSelection() } } else return false
            GLFW.GLFW_KEY_V -> if (ctrl) insert(clipboard.clipboard) else return false
            else -> return false
        }
        return true
    }

    private fun selected() = text.substring(selStart, selEnd)

    private fun deleteSelection(): Boolean {
        if (!hasSelection) return false
        replaceRange(selStart, selEnd, "")
        return true
    }

    private fun replaceRange(from: Int, to: Int, with: String) {
        text = text.substring(0, from) + with + text.substring(to)
        moveTo(from + with.length)
    }

    private fun prevChar() = if (caret == 0) 0 else text.offsetByCodePoints(caret, -1)
    private fun nextChar() = if (caret == text.length) caret else text.offsetByCodePoints(caret, 1)

    private fun prevWord(): Int {
        var i = caret
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return i
    }

    private fun nextWord(): Int {
        var i = caret
        while (i < text.length && !text[i].isWhitespace()) i++
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }
}
