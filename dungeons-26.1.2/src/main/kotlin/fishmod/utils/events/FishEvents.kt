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
    @JvmField val ON_SERVER_TICK = FishEventBus<ServerTickHook>()

    @JvmField val ON_LOCATION_CHANGE = FishEventBus<LocationHook>()
    @JvmField val ON_WORLD_CHANGE = FishEventBus<WorldHook>()

    @JvmField val ON_LEAP = FishEventBus<LeapHook>()
    @JvmField val ON_RUN_END = FishEventBus<RunEndHook>()
    @JvmField val ON_PLAYER_ENTRY = FishEventBus<TabEntryHook>()
    @JvmField val ON_PHASE_CHANGE = FishEventBus<PhaseHook>()
    @JvmField val ON_PARTY_MESSAGE = FishEventBus<PartyChatHook>()

    @JvmField val ON_TERMINAL = FishEventBus<TerminalHook>()
    @JvmField val ON_SECTION_CHANGE = FishEventBus<SectionHook>()

    @JvmField val ON_ENTITY_SPAWNED = FishEventBus<EntityHook>()

    @JvmField val ON_GAME_MESSAGE = FishEventBus<GameMessageHook>()

    @JvmField val ON_SOUND = FishEventBus<SoundHook>()

    @JvmField val ON_PARTICLE = FishEventBus<ParticleHook>()
    @JvmField val ON_PACKET = FishEventBus<PacketHook>()
}
