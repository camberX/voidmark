package dev.voidmark.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.net.ConnectionPing;
import dev.voidmark.client.node.EnderNodeTracker;
import dev.voidmark.client.render.NodeHudRenderer;
import dev.voidmark.client.render.NodeWorldRenderer;
import dev.voidmark.client.render.WatermarkRenderer;
import dev.voidmark.client.ui.Theme;
import dev.voidmark.client.ui.VoidmarkScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
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

	@Override
	public void onInitializeClient() {
		VoidmarkConfig.load();
		Theme.refresh();
		NodeWorldRenderer.init();
		WatermarkRenderer.init();
		NodeHudRenderer.init();

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
			dispatcher.register(root);
			dispatcher.register(ClientCommands.literal("vm").executes(context -> openScreen()));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGui.consumeClick()) {
				openScreen();
			}

			SkyblockLocation.tick(client);
			EnderNodeTracker.get().tick(client);
			ConnectionPing.tick(client);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SkyblockLocation.reset();
			EnderNodeTracker.get().clear();
			ConnectionPing.reset();
		});
	}

	private static int openScreen() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new VoidmarkScreen()));
		return Command.SINGLE_SUCCESS;
	}
}
