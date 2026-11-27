package fishmod.features.alerts

import fishmod.features.extras.*

import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.client.Minecraft
import fishmod.features.extras.ExtrasSettings as S

object VanquisherShare {
    private var lastMs = 0L

    fun init() {
        FishEvents.chat.register { text ->
            if (S.vanquisherShare && text.chatPlain.trim() == "A Vanquisher is spawning nearby!") share()
            false
        }
    }

    private fun share() {
        val now = System.currentTimeMillis()
        if (now - lastMs < 10_000) return
        lastMs = now
        val p = Minecraft.getInstance().player ?: return
        val b = p.blockPosition()
        FishUtil.executeCommand("pc x: ${b.x}, y: ${b.y}, z: ${b.z} | Vanquisher")
    }
}
