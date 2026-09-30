package fishmod.features.diana

import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import java.util.Optional

// Sphinx quiz: marks the right answer and answers it on any left click while chat is open
object SphinxSolver {

    private val QUESTIONS: Map<String, String> = mapOf(
        "Which of these is NOT a pet?" to "Slime",
        "What type of mob is exclusive to the Fishing Festival?" to "Shark",
        "Where is Trevor the Trapper found?" to "Mushroom Desert",
        "Who helps you apply Rod Parts?" to "Roddy",
        "Which type of Gemstone has the lowest Breaking Power?" to "Ruby",
        "Which item rarity comes after Mythic?" to "Divine",
        "How do you obtain the Dark Purple Dye?" to "Dark Auction",
        "Who runs the Chocolate Factory?" to "Hoppity",
        "How many floors are there in The Catacombs?" to "7",
        "What is the first type of slayer Maddox offers?" to "Zombie",
        "What item do you use to kill Pests?" to "Vacuum",
        "Who owns the Gold Essence Shop?" to "Marigold",
        "Which of these is NOT a type of Gemstone?" to "Prismite",
        "What does Junker Joel collect?" to "Junk",
        "Where is the Titanoboa found?" to "Backwater Bayou",
    ).mapKeys { it.key.lowercase() }

    private val ALL_ANSWERS = QUESTIONS.values.map { it.lowercase() }.toSet()
    private val ANSWER_LINE = Regex("^\\s*([ABC])\\) (.+)$")
    private const val TIMEOUT_MS = 60_000L
    private const val BUFFER_MS = 750L

    private class Pending(val letter: String, val text: String, val original: Component)
    private class Solved(val index: Int, val click: ClickEvent?, val at: Long)

    private var question: String? = null
    private var questionAt = 0L
    private val buffer = ArrayList<Pending>()
    private var bufferAt = 0L
    private var solved: Solved? = null

    private fun enabled() = DianaSettings.dianaSphinxSolver && Diana.inHub()

    fun init() {
        Events.ON_GAME_MESSAGE.register { msg -> onMessage(msg) }
        Events.ON_WORLD_CHANGE.register { reset(); false }
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is ChatScreen) return@AfterInit
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                !(click.button() == 0 && answer())
            })
        })
    }

    private fun reset() {
        flush()
        question = null
        solved = null
    }

    // true hides the packet line (only while buffering answer lines)
    private fun onMessage(msg: Component): Boolean {
        if (!enabled()) return false
        val plain = ChatFormatting.stripFormatting(msg.string)?.trim() ?: return false
        val lower = plain.lowercase()
        QUESTIONS.keys.firstOrNull { lower == it || lower.endsWith(it) }?.let {
            flush()
            question = it
            questionAt = System.currentTimeMillis()
            solved = null
            Minecraft.getInstance().gui.chat.addClientSystemMessage(
                Component.literal("§6[FishMod] §bClick anywhere while chat is open to answer."))
            return false
        }
        val m = ANSWER_LINE.find(plain) ?: return false
        val letter = m.groupValues[1]
        if (buffer.any { it.letter == letter }) flush()
        if (buffer.isEmpty()) bufferAt = System.currentTimeMillis()
        buffer.add(Pending(letter, m.groupValues[2].trim(), msg))
        if (buffer.size >= 3) flush()
        return true
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        if (buffer.isNotEmpty() && now - bufferAt > BUFFER_MS) flush()
        if (question != null && now - questionAt > TIMEOUT_MS) question = null
        solved?.let { if (now - it.at > TIMEOUT_MS) solved = null }
    }

    // Re-emit buffered lines: restyled if a correct option is known, otherwise untouched
    private fun flush() {
        if (buffer.isEmpty()) return
        val lines = ArrayList(buffer)
        buffer.clear()
        val expected = question?.let { QUESTIONS[it]?.lowercase() }
        val correct = lines.indexOfFirst {
            val t = it.text.lowercase()
            if (expected != null) t == expected else t in ALL_ANSWERS
        }
        val chat = Minecraft.getInstance().gui.chat
        if (correct < 0) {
            lines.forEach { chat.addClientSystemMessage(it.original) }
            return
        }
        lines.forEachIndexed { i, p -> chat.addClientSystemMessage(restyle(p, i == correct)) }
        val c = lines[correct]
        solved = Solved("ABC".indexOf(c.letter), findClick(c.original), System.currentTimeMillis())
        question = null
    }

    private fun restyle(p: Pending, correct: Boolean): Component {
        val click = findClick(p.original)
        val hover = findHover(p.original)
        val answer = if (correct) Component.literal(p.text).withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
            else Component.literal(p.text).withStyle(ChatFormatting.RED)
        return Component.empty()
            .withStyle { it.withClickEvent(click).withHoverEvent(hover) }
            .append(Component.literal("   ${p.letter}) ").withStyle(ChatFormatting.GRAY))
            .append(answer)
    }

    private fun findClick(c: Component): ClickEvent? =
        c.visit({ style: Style, _: String -> Optional.ofNullable(style.clickEvent) }, Style.EMPTY).orElse(null)

    private fun findHover(c: Component): HoverEvent? =
        c.visit({ style: Style, _: String -> Optional.ofNullable(style.hoverEvent) }, Style.EMPTY).orElse(null)

    // One user click = one answer
    private fun answer(): Boolean {
        val s = solved ?: return false
        if (!enabled() || System.currentTimeMillis() - s.at > TIMEOUT_MS) { solved = null; return false }
        val conn = Minecraft.getInstance().connection ?: return false
        solved = null
        val cmd = (s.click as? ClickEvent.RunCommand)?.command()?.removePrefix("/")
        conn.sendCommand(cmd ?: "sphinxanswer ${s.index}")
        return true
    }
}
