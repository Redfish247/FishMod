package fishmod.utils.events

import fishmod.utils.events.interfaces.BlockEntityEvent
import fishmod.utils.events.interfaces.BlockInteractionEvent
import fishmod.utils.events.interfaces.EntityEvent
import fishmod.utils.events.interfaces.GameMessageEvent
import fishmod.utils.events.interfaces.LeapEvent
import fishmod.utils.events.interfaces.LocationChangeEvent
import fishmod.utils.events.interfaces.PacketEvent
import fishmod.utils.events.interfaces.ParticleEvent
import fishmod.utils.events.interfaces.PartyMessageEvent
import fishmod.utils.events.interfaces.PetEvent
import fishmod.utils.events.interfaces.PhaseEvent
import fishmod.utils.events.interfaces.PlaySoundEvent
import fishmod.utils.events.interfaces.PlayerListEvent
import fishmod.utils.events.interfaces.RunEndEvent
import fishmod.utils.events.interfaces.ScoreBoardEvent
import fishmod.utils.events.interfaces.SectionEvent
import fishmod.utils.events.interfaces.ServerTickEvent
import fishmod.utils.events.interfaces.SlotChangeEvent
import fishmod.utils.events.interfaces.TerminalEvent
import fishmod.utils.events.interfaces.WorldEvent

object Events {
    @JvmField val ON_SERVER_TICK = EventHandler<ServerTickEvent>()
    @JvmField val ON_SLOT_CHANGE = EventHandler<SlotChangeEvent>()

    @JvmField val ON_LOCATION_CHANGE = EventHandler<LocationChangeEvent>()
    @JvmField val ON_WORLD_CHANGE = EventHandler<WorldEvent>()

    @JvmField val ON_LEAP = EventHandler<LeapEvent>()
    @JvmField val ON_RUN_END = EventHandler<RunEndEvent>()
    @JvmField val ON_PLAYER_ENTRY = EventHandler<PlayerListEvent>()
    @JvmField val ON_TEAM = EventHandler<ScoreBoardEvent>()
    @JvmField val ON_PHASE_CHANGE = EventHandler<PhaseEvent>()
    @JvmField val ON_PET = EventHandler<PetEvent>()
    @JvmField val ON_PARTY_MESSAGE = EventHandler<PartyMessageEvent>()

    @JvmField val ON_TERMINAL = EventHandler<TerminalEvent>()
    @JvmField val ON_SECTION_CHANGE = EventHandler<SectionEvent>()

    @JvmField val ON_ENTITY_SPAWNED = EventHandler<EntityEvent>()

    @JvmField val ON_GAME_MESSAGE = EventHandler<GameMessageEvent>()
    @JvmField val ON_BLOCK_INTERACTION = EventHandler<BlockInteractionEvent>()

    @JvmField val ON_SOUND = EventHandler<PlaySoundEvent>()

    @JvmField val ON_BLOCK_ENTITY = EventHandler<BlockEntityEvent>()

    @JvmField val ON_PARTICLE = EventHandler<ParticleEvent>()
    @JvmField val ON_PACKET = EventHandler<PacketEvent>()
}
