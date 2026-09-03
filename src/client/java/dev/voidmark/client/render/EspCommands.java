package dev.voidmark.client.render;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.voidmark.client.config.UnloadState;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;

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
		List<String> labels = config.nametagEspLabels();
		if (labels.isEmpty()) {
			tell(muted("Glow ESP nametags that contain a word: /vm esp <text>. Add more than one. /vm esp clear turns them all off."));
			return Command.SINGLE_SUCCESS;
		}
		tell(brand().append(sep()).append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" glowing nametags containing ").withStyle(style(Theme.MUTED)))
			.append(Component.literal(String.join(", ", labels)).withStyle(style(Theme.TEXT))));
		int learned = 0;
		for (String label : labels) {
			learned += EspMobPrint.learned(label.toLowerCase(Locale.ROOT));
		}
		if (learned > 0) {
			tell(muted("Learned " + learned + " look" + (learned == 1 ? "" : "s") + " from named mobs — copies glow at render distance."));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int set(String query) {
		String trimmed = query == null ? "" : query.trim();
		VoidmarkConfig config = VoidmarkConfig.get();
		if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("clear") || trimmed.equalsIgnoreCase("off") || trimmed.equalsIgnoreCase("none")) {
			config.clearNametagEsp();
			EspMobPrint.clear();
			config.save();
			UnloadState.markDirty();
			tell(brand().append(sep()).append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" nametag filters cleared").withStyle(style(Theme.MUTED))));
			return Command.SINGLE_SUCCESS;
		}
		if (trimmed.toLowerCase(Locale.ROOT).startsWith("clear ")) {
			String drop = trimmed.substring(6).trim();
			if (config.removeNametagEsp(drop)) {
				EspMobPrint.drop(drop.toLowerCase(Locale.ROOT));
				config.save();
				UnloadState.markDirty();
				tell(brand().append(sep()).append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
					.append(Component.literal(" removed ").withStyle(style(Theme.MUTED)))
					.append(Component.literal(drop).withStyle(style(Theme.TEXT))));
			} else {
				tell(muted("No nametag ESP named " + drop + "."));
			}
			return Command.SINGLE_SUCCESS;
		}
		if (!config.addNametagEsp(trimmed)) {
			tell(brand().append(sep()).append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" already glowing ").withStyle(style(Theme.MUTED)))
				.append(Component.literal(trimmed).withStyle(style(Theme.TEXT))));
			return Command.SINGLE_SUCCESS;
		}
		config.save();
		UnloadState.markDirty();
		int nearby = MobGlowRenderer.nearbyCount();
		MutableComponent line = brand()
			.append(sep())
			.append(Component.literal("ESP").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" added ").withStyle(style(Theme.MUTED)))
			.append(Component.literal(trimmed).withStyle(style(Theme.TEXT)));
		List<String> labels = config.nametagEspLabels();
		if (labels.size() > 1) {
			line.append(Component.literal("  " + labels.size() + " filters").withStyle(style(Theme.MUTED)));
		}
		if (nearby > 0) {
			line.append(Component.literal("  " + nearby + " nearby").withStyle(style(Theme.MUTED)));
		}
		tell(line);
		int learned = EspMobPrint.learned(trimmed.toLowerCase(Locale.ROOT));
		if (learned == 0) {
			tell(muted("Walk up to one named mob in this world. Voidmark copies its type and armor, then forgets the look when you change worlds."));
		}
		return Command.SINGLE_SUCCESS;
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
