package fishmod.utils.events

import fishmod.utils.events.interfaces.EntityHook
import fishmod.utils.events.interfaces.GameMessageHook
import fishmod.utils.events.interfaces.LeapHook
import fishmod.utils.events.interfaces.LocationHook
import fishmod.utils.events.interfaces.PacketHook
import fishmod.utils.events.interfaces.ParticleHook
import fishmod.utils.events.interfaces.PartyChatHook
import fishmod.utils.events.interfaces.PhaseHook
import fishmod.utils.events.interfaces.SoundHook
import fishmod.utils.events.interfaces.TabEntryHook
import fishmod.utils.events.interfaces.RunEndHook
import fishmod.utils.events.interfaces.SectionHook
import fishmod.utils.events.interfaces.ServerTickHook
import fishmod.utils.events.interfaces.TerminalHook
import fishmod.utils.events.interfaces.WorldHook

object FishEvents {
    @JvmField val serverTick = FishEventBus<ServerTickHook>()

    @JvmField val locationChange = FishEventBus<LocationHook>()
    @JvmField val worldChange = FishEventBus<WorldHook>()

    @JvmField val leap = FishEventBus<LeapHook>()
    @JvmField val runEnd = FishEventBus<RunEndHook>()
    @JvmField val tabEntry = FishEventBus<TabEntryHook>()
    @JvmField val phaseChange = FishEventBus<PhaseHook>()
    @JvmField val partyChat = FishEventBus<PartyChatHook>()

    @JvmField val terminalStep = FishEventBus<TerminalHook>()
    @JvmField val sectionChange = FishEventBus<SectionHook>()

    @JvmField val entitySpawn = FishEventBus<EntityHook>()

    @JvmField val chat = FishEventBus<GameMessageHook>()

    @JvmField val sound = FishEventBus<SoundHook>()

    @JvmField val particle = FishEventBus<ParticleHook>()
    @JvmField val packet = FishEventBus<PacketHook>()
}
