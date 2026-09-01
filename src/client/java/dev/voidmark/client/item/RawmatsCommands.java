package dev.voidmark.client.item;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

public final class RawmatsCommands {
	private RawmatsCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("rawmats")
			.executes(context -> trackHeld())
			.then(ClientCommands.argument("id", StringArgumentType.greedyString())
				.suggests(suggest())
				.executes(context -> track(StringArgumentType.getString(context, "id"))));
	}

	private static SuggestionProvider<FabricClientCommandSource> suggest() {
		return (context, builder) -> {
			String remaining = builder.getRemaining();
			String lower = remaining == null ? "" : remaining.trim().toLowerCase(Locale.ROOT);
			if (lower.isEmpty() || "clear".startsWith(lower)) {
				builder.suggest("clear");
			}
			if (lower.isEmpty() || "raw".startsWith(lower)) {
				builder.suggest("raw");
			}
			if (lower.isEmpty() || "enchanted".startsWith(lower)) {
				builder.suggest("enchanted");
			}
			if (lower.isEmpty() || "refresh".startsWith(lower)) {
				builder.suggest("refresh");
			}
			if (lower.isEmpty()) {
				return builder.buildFuture();
			}
			List<String> hits;
			if (lower.startsWith("sb:") || lower.startsWith("skyblock:")) {
				hits = ItemIds.suggest(remaining, 40);
			} else {
				hits = ItemIds.suggest("sb:" + remaining.trim(), 40);
				if (hits.isEmpty()) {
					hits = ItemIds.suggest(remaining, 20);
				}
			}
			for (String hit : hits) {
				builder.suggest(hit);
			}
			return builder.buildFuture();
		};
	}

	public static int trackHeld() {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		ItemStack held = ItemIds.held(player);
		String id = ItemStorage.idOf(held);
		if (id == null) {
			tell(muted("Hold a Skyblock item or type /vm rawmats <id>."));
			return 0;
		}
		return track(id);
	}

	public static int track(String query) {
		if (query == null || query.isBlank()) {
			return trackHeld();
		}
		String trimmed = query.trim();
		if (trimmed.equalsIgnoreCase("clear") || trimmed.equalsIgnoreCase("off") || trimmed.equalsIgnoreCase("none")) {
			RawmatsTracker.clear();
			tell(brand().append(sep()).append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" cleared").withStyle(style(Theme.MUTED))));
			return Command.SINGLE_SUCCESS;
		}
		if (trimmed.equalsIgnoreCase("refresh")) {
			SkyblockProfileApi.refresh();
			tell(brand().append(sep()).append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" refreshing storage").withStyle(style(Theme.MUTED))));
			return Command.SINGLE_SUCCESS;
		}
		if (trimmed.equalsIgnoreCase("raw") || trimmed.equalsIgnoreCase("enchanted") || trimmed.equalsIgnoreCase("ench")) {
			VoidmarkConfig config = VoidmarkConfig.get();
			config.rawmatsEnchanted = trimmed.toLowerCase(Locale.ROOT).startsWith("ench");
			config.save();
			tell(brand().append(sep()).append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" " + config.rawmatsModeLabel()).withStyle(style(Theme.TEXT))));
			return Command.SINGLE_SUCCESS;
		}
		ItemIds.Preview preview = ItemIds.resolve(trimmed);
		String id = preview.kind() == ItemIds.Kind.SKYBLOCK
			? SkyblockRecipes.normalize(preview.canonical())
			: SkyblockRecipes.normalize(trimmed);
		if (id.isBlank()) {
			tell(muted("Unknown item. Try sb:HYPERION or hold the item and run /vm rawmats."));
			return 0;
		}
		RawmatsTracker.set(id);
		RawmatsTracker.Snapshot snap = RawmatsTracker.snapshot();
		MutableComponent line = brand()
			.append(sep())
			.append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" " + snap.name()).withStyle(style(Theme.TEXT)));
		if (snap.recipe()) {
			line.append(Component.literal("  " + VoidmarkConfig.get().rawmatsModeLabel().toLowerCase(Locale.ROOT) + "  " + snap.complete() + "/" + snap.total() + " materials").withStyle(style(Theme.MUTED)));
		} else {
			line.append(Component.literal("  no craft tree, tracking the item itself").withStyle(style(Theme.MUTED)));
		}
		tell(line);
		int shown = 0;
		for (RawmatsTracker.Line row : snap.lines()) {
			if (shown >= 8) {
				tell(muted("  +" + (snap.lines().size() - shown) + " more on the HUD"));
				break;
			}
			String mark = row.done() ? "done" : row.have() + "/" + row.need();
			tell(Component.literal("  " + row.name() + "  " + mark).withStyle(style(row.done() ? Theme.ACCENT : Theme.TEXT)));
			shown++;
		}
		if (!ItemStorage.hasApiStorage() && (!snap.sawEnder() || !snap.sawBackpack())) {
			tell(muted("Ender Chest and backpacks load from your Skyblock profile. /vm rawmats refresh if they look stale."));
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
