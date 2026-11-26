package fishmod.features.chat

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents

object ChatEmotes {

    private val TABLE: Map<String, String> = mapOf(
        "<3" to "❤",
        "o/" to "( ﾟ◡ﾟ)/",
        ":wave:" to "( ﾟ◡ﾟ)/",
        ":star:" to "✮",
        ":yes:" to "✔",
        ":no:" to "✖",
        ":java:" to "☕",
        ":arrow:" to "➜",
        ":pvp:" to "⚔",
        ":peace:" to "✌",
        ":snow:" to "☃",
        ":heart:" to "❤",
        ":sun:" to "☀",
        ":shrug:" to "¯\_(ツ)_/¯",
        ":tableflip:" to "(╯°□°）╯︵ ┻━┻",
        ":totem:" to "☉_☉",
        ":maths:" to "√(π+x)=L",
        ":snail:" to "@'-'",
        ":gimme:" to "༼つ◕_◕༽つ",
        ":cute:" to "(✿◠‿◠)",
        ":dj:" to "ヽ(⌐■_■)ノ♬",
        ":oof:" to "OOF",
        ":puffer:" to "<(\"O\")>",
        ":cat:" to "= ＾● ⋏ ●＾ ="
    )

    @JvmStatic
    fun init() {
        ClientSendMessageEvents.MODIFY_CHAT.register { msg -> replace(msg) }
    }

    @JvmStatic
    fun replace(msg: String): String {
        if (!FishSettings.chatEmotes) return msg
        if (msg.startsWith("/")) return msg
        if (TABLE.keys.none { msg.contains(it) }) return msg
        return msg.split(" ").joinToString(" ") { TABLE[it] ?: it }
    }
}
