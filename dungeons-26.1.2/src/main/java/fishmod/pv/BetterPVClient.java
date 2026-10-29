/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fishmod.pv.BetterPV;
import fishmod.pv.api.BetterPVConfig;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.nav.PvTab;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.neu.SkyBlockPackCache;
import fishmod.pv.price.ItemPricer;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class BetterPVClient {
	/** Runs after {@link Event#DEFAULT_PHASE} so we replace Skyblocker's {@code /pv}. */
	private static final Identifier PV_COMMAND_PHASE = Identifier.fromNamespaceAndPath("betterpv", "override_pv");
		/** Set when Brigadier reflection could not strip another mod's {@code /pv}. */
	private static final AtomicBoolean PV_OVERRIDE_FAILED = new AtomicBoolean(false);
	private static final AtomicBoolean PV_SHADOW_CHAT_SENT = new AtomicBoolean(false);
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

	public static void init() {
		if (!INITIALIZED.compareAndSet(false, true)) return;
		BetterPVConfig.load();
		ItemPricer.start();
		NeuRepoCache.start();
		SkyBlockPackCache.start();

		// Late phase so Skyblocker (and similar) register /pv first; we then strip their
		// literal and install ours. Phase order alone is not enough: Brigadier's
		// CommandNode.addChild merges a duplicate literal into the existing node instead of
		// replacing it, so a second dispatcher.register("pv") would leave Skyblocker's
		// executes() in place. Fabric has no supported unregister API (docs still say to
		// reflect into Brigadier's private children/literals maps).
		ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PV_COMMAND_PHASE);
		ClientCommandRegistrationCallback.EVENT.register(PV_COMMAND_PHASE, (dispatcher, registryAccess) -> {
			removeLiteral(dispatcher, "pv");
			dispatcher.register(buildPvCommand(ClientCommands.literal("pv")));
			dispatcher.register(
				ClientCommands.literal("betterpv")
					.then(buildPvCommand(ClientCommands.literal("pv")))
			);
		});

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
			new SimpleSynchronousResourceReloadListener() {
				@Override
				public Identifier getFabricId() {
					// fishmod namespace: the standalone BetterPV mod registers betterpv:icon_probe_cache too
					return Identifier.fromNamespaceAndPath("fishmod", "pv_icon_probe_cache");
				}

				@Override
				public void onResourceManagerReload(ResourceManager resourceManager) {
					SkyBlockIconRenderer.invalidateProbeCache();
				}
			}
		);

		PlayerInteractPvOpener.register();
		HypixelProfileSpyButton.register();
		PartyJoinPvNotifier.register();
		// Inject /pv clicks on party / friends-list lines that Hypixel left non-clickable.
		// Does not remap existing SocialOptions (avoids chat color bleach).
		ClientReceiveMessageEvents.MODIFY_GAME.register(
			(message, overlay) -> overlay ? message : ChatClickProcessor.process(message)
		);
		ClientTickEvents.END_CLIENT_TICK.register(ProfileViewerOpener::tick);
		ClientTickEvents.END_CLIENT_TICK.register(BetterPVClient::warnPvShadowedOnce);
		BetterPV.LOGGER.info("BetterPV client ready - /pv");
	}

	/** One in-game chat tip if bare {@code /pv} could not be claimed from another mod. */
	private static void warnPvShadowedOnce(net.minecraft.client.Minecraft client) {
		if (!PV_OVERRIDE_FAILED.get() || client == null || client.player == null || client.gui == null) {
			return;
		}
		if (!PV_SHADOW_CHAT_SENT.compareAndSet(false, true)) {
			return;
		}
		fishmod.compat.McCompatKt.getHud(client).getChat().addClientSystemMessage(
			net.minecraft.network.chat.Component.literal(
				"BetterPV: another mod uses /pv. Use /betterpv pv [name] instead."
			).withColor(0xFFD36A)
		);
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> buildPvCommand(
		com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> root
	) {
		return root
			.executes(ctx -> {
				ProfileViewerOpener.openSelfOr(null);
				return 1;
			})
			.then(
				ClientCommands.argument("player", StringArgumentType.word())
					.executes(ctx -> {
						ProfileViewerOpener.handleTypedArg(StringArgumentType.getString(ctx, "player"));
						return 1;
					})
					.then(
						ClientCommands.argument("page", StringArgumentType.word())
							.suggests((ctx, builder) -> suggestPages(builder))
							.executes(ctx -> {
								String player = StringArgumentType.getString(ctx, "player");
								String page = StringArgumentType.getString(ctx, "page");
								ProfileViewerOpener.handleTypedArg(player + " " + page);
								return 1;
							})
					)
			);
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPages(
		com.mojang.brigadier.suggestion.SuggestionsBuilder builder
	) {
		String remaining = builder.getRemainingLowerCase();
		for (String alias : PvTab.commandAliases()) {
			if (remaining.isEmpty() || alias.startsWith(remaining)) {
				builder.suggest(alias);
			}
		}
		return builder.buildFuture();
	}

	/**
	 * Removes an already-registered root literal from the client command dispatcher.
	 *
	 * <p>Necessary before re-registering {@code /pv}: Brigadier merges duplicate literals
	 * into the existing node, so a late {@code dispatcher.register} would not override
	 * Skyblocker's handler. Fabric documents reflection as the only unregister path.
	 *
	 * <p>If Brigadier field names change, this fails closed: we still register
	 * {@code /betterpv pv}, and log that bare {@code /pv} may stay owned by another mod.
	 */
	@SuppressWarnings("unchecked")
	private static void removeLiteral(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		try {
			CommandNode<FabricClientCommandSource> root = dispatcher.getRoot();
			Field childrenField = CommandNode.class.getDeclaredField("children");
			childrenField.setAccessible(true);
			Map<String, CommandNode<FabricClientCommandSource>> children =
				(Map<String, CommandNode<FabricClientCommandSource>>) childrenField.get(root);
			children.remove(name);

			Field literalsField = CommandNode.class.getDeclaredField("literals");
			literalsField.setAccessible(true);
			Map<String, LiteralCommandNode<FabricClientCommandSource>> literals =
				(Map<String, LiteralCommandNode<FabricClientCommandSource>>) literalsField.get(root);
			literals.remove(name);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			// warn (not error): mod still works via /betterpv pv; loud only so mapping breaks are noticed.
			PV_OVERRIDE_FAILED.set(true);
			BetterPV.LOGGER.warn(
				"Could not strip existing /{} via Brigadier reflection (children/literals). "
					+ "/{} may stay owned by another mod (e.g. Skyblocker). Use /betterpv {} instead.",
				name,
				name,
				name,
				exception
			);
		}
	}
}