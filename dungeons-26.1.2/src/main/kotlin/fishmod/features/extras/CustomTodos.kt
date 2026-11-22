package fishmod.features.extras

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.features.FishHudEditor
import fishmod.utils.FishUtil
import fishmod.utils.config.FishConfig
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import fishmod.features.extras.ExtrasSettings as S

// Custom todo list HUD, edited with /fmtodo add|done|clear
object CustomTodos {
    private const val SEP = "\n"

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("fmtodo")
                .executes { list(); 1 }
                .then(ClientCommands.literal("add").then(ClientCommands.argument("text", StringArgumentType.greedyString())
                    .executes { set(items() + StringArgumentType.getString(it, "text").replace(SEP, " ").trim()); list(); 1 }))
                .then(ClientCommands.literal("done").then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                    .executes { val i = IntegerArgumentType.getInteger(it, "n") - 1; items().let { l -> if (i < l.size) set(l.filterIndexed { j, _ -> j != i }) }; list(); 1 }))
                .then(ClientCommands.literal("clear").executes { set(emptyList()); list(); 1 }))
        }
        FishHudEditor.register("Todos", { S.todoHudX }, { S.todoHudX = it }, { S.todoHudY }, { S.todoHudY = it }, 120, 40,
            { S.todoHudScale }, { S.todoHudScale = it })
        fishmod.features.HudPreview.register("Todos") {
            val rows = items().ifEmpty { listOf("Example todo", "Another todo") }.map { "- $it" }
            val f = Minecraft.getInstance().font
            fishmod.features.HudExample(maxOf(f.width("Todo"), rows.maxOf { f.width(it) }), fishmod.features.HudPreview.textHeight(rows.size + 1)) { ctx ->
                ctx.text(f, Component.literal("Todo"), 0, 0, S.todoHudTitleColor, true)
                rows.forEachIndexed { i, s -> ctx.text(f, Component.literal(s), 0, (i + 1) * 10, S.todoHudColor, true) }
            }
        }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "todo_hud")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("CustomTodos.1", "todo hud render failed", t) }
        }
    }

    private fun items(): List<String> = S.todoItems.split(SEP).filter { it.isNotBlank() }

    private fun set(l: List<String>) {
        S.todoItems = l.filter { it.isNotBlank() }.joinToString(SEP)
        runCatching { FishConfig.manager.save() }.onFailure { FishDiag.fail("CustomTodos.2", "saving todos failed", it) }
    }

    private fun list() {
        if (!S.todoHud) FishUtil.addChatMessage(Component.literal("§7Custom Todos HUD is off in the Extras tab."))
        val l = items()
        if (l.isEmpty()) { FishUtil.addChatMessage(Component.literal("§7No todos. §e/fmtodo add <text>")); return }
        l.forEachIndexed { i, s -> FishUtil.addChatMessage(Component.literal("§e${i + 1}. §f$s")) }
    }

    private fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        if (!S.todoHud) return
        val l = items()
        if (l.isEmpty()) return
        val mc = Minecraft.getInstance()
        val sc = S.todoHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.todoHudX.toFloat(), S.todoHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(mc.font, Component.literal("Todo"), 0, 0, S.todoHudTitleColor, true)
            l.forEachIndexed { i, s -> ctx.text(mc.font, Component.literal("- $s"), 0, (i + 1) * 10, S.todoHudColor, true) }
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
