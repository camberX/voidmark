package dev.voidmark.client.render;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class EspCommands {
	private EspCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("esp")
			.executes(context -> status())
			.then(ClientCommands.argument("nametag", StringArgumentType.greedyString())
				.executes(context -> set(StringArgumentType.getString(context, "nametag"))));
	}

	private static int status() {
		VoidmarkConfig config = VoidmarkConfig.get();
		String needle = needle(config);
		if (needle.isEmpty()) {
			tell(muted("Glow ESP nametags that contain a word: /vm esp <text>. /vm esp clear turns it off."));
			return Command.SINGLE_SUCCESS;
		}
		tell(brand().append(sep()).append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" glowing nametags containing ").withStyle(style(Theme.MUTED)))
			.append(Component.literal(needle).withStyle(style(Theme.TEXT))));
		return Command.SINGLE_SUCCESS;
	}

	private static int set(String query) {
		String trimmed = query == null ? "" : query.trim();
		VoidmarkConfig config = VoidmarkConfig.get();
		if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("clear") || trimmed.equalsIgnoreCase("off") || trimmed.equalsIgnoreCase("none")) {
			config.mobGlowName = "";
			config.save();
			tell(brand().append(sep()).append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" nametag filter cleared").withStyle(style(Theme.MUTED))));
			return Command.SINGLE_SUCCESS;
		}
		config.mobGlowName = trimmed;
		config.mobGlowEnabled = true;
		config.save();
		int nearby = MobGlowRenderer.nearbyCount();
		MutableComponent line = brand()
			.append(sep())
			.append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" glowing nametags containing ").withStyle(style(Theme.MUTED)))
			.append(Component.literal(trimmed).withStyle(style(Theme.TEXT)));
		if (nearby > 0) {
			line.append(Component.literal("  " + nearby + " nearby").withStyle(style(Theme.MUTED)));
		}
		tell(line);
		return Command.SINGLE_SUCCESS;
	}

	private static String needle(VoidmarkConfig config) {
		return config.mobGlowName == null ? "" : config.mobGlowName.trim();
	}

	private static MutableComponent brand() {
		return Component.literal("VOIDMARK").withStyle(style(Theme.ACCENT).withBold(true));
	}

	private static MutableComponent sep() {
		return Component.literal(" | ").withStyle(style(Theme.MUTED));
	}

	private static MutableComponent muted(String value) {
		return Component.literal(value).withStyle(style(Theme.MUTED));
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}

	private static void tell(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(message);
		}
	}
}
