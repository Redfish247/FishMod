package fishmod.utils.events;

import fishmod.utils.events.interfaces.BlockEntityEvent;
import fishmod.utils.events.interfaces.BlockInteractionEvent;
import fishmod.utils.events.interfaces.EntityEvent;
import fishmod.utils.events.interfaces.GameMessageEvent;
import fishmod.utils.events.interfaces.LeapEvent;
import fishmod.utils.events.interfaces.LocationChangeEvent;
import fishmod.utils.events.interfaces.PacketEvent;
import fishmod.utils.events.interfaces.ParticleEvent;
import fishmod.utils.events.interfaces.PartyMessageEvent;
import fishmod.utils.events.interfaces.PetEvent;
import fishmod.utils.events.interfaces.PhaseEvent;
import fishmod.utils.events.interfaces.PlaySoundEvent;
import fishmod.utils.events.interfaces.PlayerListEvent;
import fishmod.utils.events.interfaces.RunEndEvent;
import fishmod.utils.events.interfaces.ScoreBoardEvent;
import fishmod.utils.events.interfaces.SectionEvent;
import fishmod.utils.events.interfaces.ServerTickEvent;
import fishmod.utils.events.interfaces.SlotChangeEvent;
import fishmod.utils.events.interfaces.TerminalEvent;
import fishmod.utils.events.interfaces.WorldEvent;

public class Events {
    public static final EventHandler<ServerTickEvent> ON_SERVER_TICK = new EventHandler<>();
    public static final EventHandler<SlotChangeEvent> ON_SLOT_CHANGE = new EventHandler<>();

    public static final EventHandler<LocationChangeEvent> ON_LOCATION_CHANGE = new EventHandler<>();
    public static final EventHandler<WorldEvent> ON_WORLD_CHANGE = new EventHandler<>();

    public static final EventHandler<LeapEvent> ON_LEAP = new EventHandler<>();
    public static final EventHandler<RunEndEvent> ON_RUN_END = new EventHandler<>();
    public static final EventHandler<PlayerListEvent> ON_PLAYER_ENTRY = new EventHandler<>();
    public static final EventHandler<ScoreBoardEvent> ON_TEAM = new EventHandler<>();
    public static final EventHandler<PhaseEvent> ON_PHASE_CHANGE = new EventHandler<>();
    public static final EventHandler<PetEvent> ON_PET = new EventHandler<>();
    public static final EventHandler<PartyMessageEvent> ON_PARTY_MESSAGE = new EventHandler<>();

    public static final EventHandler<TerminalEvent> ON_TERMINAL = new EventHandler<>();
    public static final EventHandler<SectionEvent> ON_SECTION_CHANGE = new EventHandler<>();

    public static final EventHandler<EntityEvent> ON_ENTITY_TRACKED = new EventHandler<>();
    public static final EventHandler<EntityEvent> ON_ENTITY_SPAWNED = new EventHandler<>();

    public static final EventHandler<GameMessageEvent> ON_GAME_MESSAGE = new EventHandler<>();
    public static final EventHandler<BlockInteractionEvent> ON_BLOCK_INTERACTION = new EventHandler<>();

    public static final EventHandler<PlaySoundEvent> ON_SOUND = new EventHandler<>();

    public static final EventHandler<BlockEntityEvent> ON_BLOCK_ENTITY = new EventHandler<>();


    public static final EventHandler<ParticleEvent> ON_PARTICLE = new EventHandler<>();
    public static final EventHandler<PacketEvent> ON_PACKET = new EventHandler<>();

}
