/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import fishmod.compat.hud
import fishmod.pv.api.PvConfig
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.nav.PvTab
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.neu.SkyBlockPackCache
import fishmod.pv.price.ItemPricer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

object PvClient {
	/** Runs after [Event.DEFAULT_PHASE] so we replace Skyblocker's `/pv`. */
	private val PV_COMMAND_PHASE: Identifier = ProfileViewer.id("override_pv")
	/** Set when Brigadier reflection could not strip another mod's `/pv`. */
	private val PV_OVERRIDE_FAILED = AtomicBoolean(false)
	private val PV_SHADOW_CHAT_SENT = AtomicBoolean(false)
	private val INITIALIZED = AtomicBoolean(false)

	@JvmStatic
	fun init() {
		if (!INITIALIZED.compareAndSet(false, true)) return
		PvConfig.load()
		fishmod.pv.price.HypixelItemsCache.start()
		NeuRepoCache.start()
		SkyBlockPackCache.start()

		// Late phase so Skyblocker (and similar) register /pv first; we then strip their
		// literal and install ours. Phase order alone is not enough: Brigadier's
		// CommandNode.addChild merges a duplicate literal into the existing node instead of
		// replacing it, so a second dispatcher.register("pv") would leave Skyblocker's
		// executes() in place. Fabric has no supported unregister API (docs still say to
		// reflect into Brigadier's private children/literals maps).
		ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PV_COMMAND_PHASE)
		ClientCommandRegistrationCallback.EVENT.register(PV_COMMAND_PHASE) { dispatcher, _ ->
			removeLiteral(dispatcher, "pv")
			dispatcher.register(buildPvCommand(ClientCommands.literal("pv")))
		}

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
			object : SimpleSynchronousResourceReloadListener {
				override fun getFabricId(): Identifier = Identifier.fromNamespaceAndPath("fishmod", "pv_icon_probe_cache")

				override fun onResourceManagerReload(resourceManager: ResourceManager) {
					SkyBlockIconRenderer.invalidateProbeCache()
				}
			}
		)

		PlayerInteractPvOpener.register()
		HypixelProfileSpyButton.register()
		// Inject /pv clicks on party / friends-list lines that Hypixel left non-clickable.
		// Does not remap existing SocialOptions (avoids chat color bleach).
		ClientReceiveMessageEvents.MODIFY_GAME.register { message, overlay ->
			// process() returns null with PV disabled; Fabric needs the message back.
			if (overlay) message else ChatClickProcessor.process(message) ?: message
		}
		ClientTickEvents.END_CLIENT_TICK.register { ProfileViewerOpener.tick(it) }
		ClientTickEvents.END_CLIENT_TICK.register { warnPvShadowedOnce(it) }
		ProfileViewer.LOGGER.info("Profile viewer ready - /pv")
	}

	/** PV stays off while BetterPV is installed; tell the player once. */
	@JvmStatic
	fun registerStandaloneNote() {
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			if (FabricLoader.getInstance().isModLoaded("betterpv")) {
				sendShadowNoteOnce(client, "FishMod profile viewer is off: BetterPV is installed and owns /pv.")
			}
		}
	}

	/** One in-game chat tip if bare `/pv` could not be claimed from another mod. */
	private fun warnPvShadowedOnce(client: Minecraft?) {
		if (PV_OVERRIDE_FAILED.get()) {
			sendShadowNoteOnce(client, "FishMod PV: another mod owns /pv. Open profiles from chat name clicks instead.")
		}
	}

	private fun sendShadowNoteOnce(client: Minecraft?, text: String) {
		if (client == null || client.player == null || client.gui == null) {
			return
		}
		if (!PV_SHADOW_CHAT_SENT.compareAndSet(false, true)) {
			return
		}
		client.hud.chat.addClientSystemMessage(Component.literal(text).withColor(0xFFD36A))
	}

	private fun buildPvCommand(
		root: LiteralArgumentBuilder<FabricClientCommandSource>
	): LiteralArgumentBuilder<FabricClientCommandSource> =
		root
			.executes {
				ProfileViewerOpener.openSelfOr(null)
				1
			}
			.then(
				ClientCommands.argument("player", StringArgumentType.word())
					.executes { ctx ->
						ProfileViewerOpener.handleTypedArg(StringArgumentType.getString(ctx, "player"))
						1
					}
					.then(
						ClientCommands.argument("page", StringArgumentType.word())
							.suggests { _, builder -> suggestPages(builder) }
							.executes { ctx ->
								val player = StringArgumentType.getString(ctx, "player")
								val page = StringArgumentType.getString(ctx, "page")
								ProfileViewerOpener.handleTypedArg("$player $page")
								1
							}
					)
			)

	private fun suggestPages(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
		val remaining = builder.remainingLowerCase
		for (alias in PvTab.commandAliases()) {
			if (remaining.isEmpty() || alias.startsWith(remaining)) {
				builder.suggest(alias)
			}
		}
		return builder.buildFuture()
	}

	/**
	 * Removes an already-registered root literal from the client command dispatcher.
	 *
	 * Necessary before re-registering `/pv`: Brigadier merges duplicate literals
	 * into the existing node, so a late `dispatcher.register` would not override
	 * Skyblocker's handler. Fabric documents reflection as the only unregister path.
	 *
	 * If Brigadier field names change, this fails closed: we still register
	 * `/pv` and log that bare `/pv` may stay owned by another mod.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun removeLiteral(dispatcher: CommandDispatcher<FabricClientCommandSource>, name: String) {
		try {
			val root: CommandNode<FabricClientCommandSource> = dispatcher.root
			val childrenField = CommandNode::class.java.getDeclaredField("children")
			childrenField.isAccessible = true
			val children = childrenField.get(root) as MutableMap<String, CommandNode<FabricClientCommandSource>>
			children.remove(name)

			val literalsField = CommandNode::class.java.getDeclaredField("literals")
			literalsField.isAccessible = true
			val literals = literalsField.get(root) as MutableMap<String, LiteralCommandNode<FabricClientCommandSource>>
			literals.remove(name)
		} catch (exception: Exception) {
			// warn (not error): chat name clicks still open PV; loud only so mapping breaks are noticed.
			PV_OVERRIDE_FAILED.set(true)
			ProfileViewer.LOGGER.warn(
				"Could not strip existing /{} via Brigadier reflection (children/literals). " +
					"/{} may stay owned by another mod (e.g. Skyblocker).",
				name,
				name,
				exception
			)
		}
	}
}
