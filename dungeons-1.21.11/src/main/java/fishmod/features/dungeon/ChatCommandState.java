package fishmod.features.dungeon;

/**
 * Shared state between PartyCommandHandler (writer) and ChatHudMixin (reader)
 * for suppressing Hypixel error-reply chat lines that fire shortly after a
 * mod-issued party command.
 *
 * Lives in a plain class because Mixin classes disallow non-private static fields.
 */
public final class ChatCommandState {
    public static volatile long lastPartyCommandAt = 0L;
    private ChatCommandState() {}
}
