package dev.voidmark.client.ui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.voidmark.client.item.WardrobeMenus;
import dev.voidmark.client.location.SkyblockLocation;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public final class WardrobeCommands {
	private WardrobeCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("wardrobe").executes(context -> open());
	}

	public static int open() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			return 0;
		}
		if (!SkyblockLocation.inSkyblock && !SkyblockLocation.onHypixel) {
			client.gui.getChat().addClientSystemMessage(
				Component.literal("Voidmark | Join Skyblock, then /wardrobe opens the custom menu.")
			);
		}
		try {
			client.player.connection.send(new ServerboundChatCommandPacket("wardrobe"));
		} catch (RuntimeException ignored) {
			try {
				client.player.connection.sendCommand("wardrobe");
			} catch (RuntimeException ignoredToo) {
			}
		}
		WardrobeScreen.allowReopen();
		if (WardrobeMenus.enabled()
			&& WardrobeScreen.hasCache()
			&& !(client.screen instanceof WardrobeScreen)) {
			client.setScreen(WardrobeScreen.fromCache());
		}
		return Command.SINGLE_SUCCESS;
	}
}
