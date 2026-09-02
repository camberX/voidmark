package dev.voidmark.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.net.ConnectionPing;
import dev.voidmark.client.node.EnderNodeTracker;
import dev.voidmark.client.item.ItemAppearance;
import dev.voidmark.client.item.ItemIds;
import dev.voidmark.client.item.RawmatsCommands;
import dev.voidmark.client.item.RawmatsTracker;
import dev.voidmark.client.item.SkyblockItems;
import dev.voidmark.client.item.SkyblockProfileApi;
import dev.voidmark.client.item.SkyblockRecipes;
import dev.voidmark.client.media.MediaChat;
import dev.voidmark.client.media.MediaSession;
import dev.voidmark.client.mining.MiningTracker;
import dev.voidmark.client.mining.TitaniumTracker;
import dev.voidmark.client.render.InventoryHudRenderer;
import dev.voidmark.client.render.MusicHudRenderer;
import dev.voidmark.client.render.NametagRenderer;
import dev.voidmark.client.render.NodeHudRenderer;
import dev.voidmark.client.render.RawmatsHudRenderer;
import dev.voidmark.client.render.MiningHudRenderer;
import dev.voidmark.client.render.MiningWorldRenderer;
import dev.voidmark.client.render.BlockOutlineGlow;
import dev.voidmark.client.render.MobGlowRenderer;
import dev.voidmark.client.render.NodeWorldRenderer;
import dev.voidmark.client.render.VanillaHud;
import dev.voidmark.client.render.WatermarkRenderer;
import dev.voidmark.client.ui.HudEditorScreen;
import dev.voidmark.client.ui.ItemEditScreen;
import dev.voidmark.client.ui.Theme;
import dev.voidmark.client.ui.VoidmarkScreen;
import dev.voidmark.client.visual.CustomCape;
import dev.voidmark.client.visual.FakeBan;
import dev.voidmark.client.visual.ShopCape;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class VoidmarkClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Voidmark.MOD_ID, "main"));
	private static KeyMapping openGui;
	private static boolean itemAppearancesLoaded;

	@Override
	public void onInitializeClient() {
		VoidmarkConfig.load();
		Theme.refresh();
		SkyblockItems.load();
		SkyblockRecipes.load();
		RawmatsTracker.init();
		CustomCape.init();
		NodeWorldRenderer.init();
		MobGlowRenderer.init();
		BlockOutlineGlow.init();
		MiningWorldRenderer.init();
		WatermarkRenderer.init();
		InventoryHudRenderer.init();
		NodeHudRenderer.init();
		MusicHudRenderer.init();
		RawmatsHudRenderer.init();
		MiningHudRenderer.init();
		NametagRenderer.init();
		VanillaHud.init();
		MediaSession.init();

		openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.voidmark.open",
			InputConstants.Type.KEYSYM,
			InputConstants.KEY_RSHIFT,
			CATEGORY
		));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var root = ClientCommands.literal("voidmark").executes(context -> openScreen());
			root.then(ClientCommands.literal("toggle").executes(context -> {
				VoidmarkConfig config = VoidmarkConfig.get();
				config.markersEnabled = !config.markersEnabled;
				config.save();
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					client.player.sendSystemMessage(
						Component.literal("Voidmark markers " + (config.markersEnabled ? "enabled" : "disabled"))
					);
				}
				return Command.SINGLE_SUCCESS;
			}));
			root.then(ClientCommands.literal("edit").executes(context -> openItemEdit()));
			root.then(musicCommand());
			root.then(RawmatsCommands.command());
			dispatcher.register(root);
			var vm = ClientCommands.literal("vm").executes(context -> openScreen());
			vm.then(ClientCommands.literal("edit").executes(context -> openItemEdit()));
			vm.then(musicCommand());
			vm.then(RawmatsCommands.command());
			dispatcher.register(vm);
		});

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (itemAppearancesLoaded) {
				return;
			}
			if (!ItemIds.componentsReady()) {
				return;
			}
			try {
				ItemAppearance.reload();
				itemAppearancesLoaded = true;
			} catch (RuntimeException ignored) {
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGui.consumeClick()) {
				if (client.screen instanceof HudEditorScreen) {
					client.setScreen(new VoidmarkScreen());
				} else if (client.screen instanceof VoidmarkScreen screen) {
					screen.requestClose();
				} else if (client.screen instanceof ItemEditScreen) {
					client.setScreen(null);
				} else {
					openScreen();
				}
			}

			SkyblockLocation.tick(client);
			EnderNodeTracker.get().tick(client);
			ConnectionPing.tick(client);
			RawmatsTracker.tick(client);
			MiningTracker.tick(client);
			TitaniumTracker.get().tick(client);
			ShopCape.tick();
			FakeBan.tick();
		});

		ClientPlayConnectionEvents.INIT.register((handler, client) -> FakeBan.onPlayInit());

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			FakeBan.onJoin();
			SkyblockProfileApi.refresh();
			ShopCape.onJoin();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SkyblockLocation.reset();
			EnderNodeTracker.get().clear();
			ConnectionPing.reset();
			MiningTracker.reset();
			TitaniumTracker.get().clear();
			FakeBan.reset();
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> musicCommand() {
		return ClientCommands.literal("music")
			.executes(context -> MediaChat.nowPlaying())
			.then(ClientCommands.literal("play").executes(context -> MediaChat.toggle()))
			.then(ClientCommands.literal("pause").executes(context -> MediaChat.toggle()))
			.then(ClientCommands.literal("next").executes(context -> MediaChat.skip(true)))
			.then(ClientCommands.literal("prev").executes(context -> MediaChat.skip(false)))
			.then(ClientCommands.literal("np").executes(context -> MediaChat.nowPlaying()));
	}

	private static int openScreen() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.screen instanceof VoidmarkScreen screen) {
				screen.requestClose();
			} else {
				client.setScreen(new VoidmarkScreen());
			}
		});
		return Command.SINGLE_SUCCESS;
	}

	private static int openItemEdit() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new ItemEditScreen()));
		return Command.SINGLE_SUCCESS;
	}
}
