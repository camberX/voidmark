package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.ui.Theme;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.node.EnderNodeTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class NodeHudRenderer {
	private NodeHudRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("ender_nodes"),
			NodeHudRenderer::extract
		);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}

		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hudEnabled || !SkyblockLocation.shouldMarkNodes()) {
			return;
		}

		Font font = client.font;
		int count = EnderNodeTracker.get().count();
		Vec3 eyes = client.player.getEyePosition(deltaTracker.getGameTimeDeltaPartialTick(false));
		EnderNodeTracker.TrackedNode nearest = EnderNodeTracker.get().nearest(eyes);

		float x = 8;
		float y = 8 + WatermarkRenderer.occupiedHeight();
		float width = 168;
		float height = nearest == null ? 42 : 58;

		int accent = Theme.ACCENT;
		GuiDraw.fill(graphics, x, y, width, height, 0xE0080C12);
		GuiDraw.fill(graphics, x, y, 2, height, accent);
		GuiDraw.fill(graphics, x + 2, y, width - 2, 1, 0x22FFFFFF);

		GuiDraw.text(graphics, font, "VOIDMARK", x + 12, y + 7, 0xFF6B7A8A, false);
		String headline = count == 0 ? "No nodes in range" : count == 1 ? "1 ender node" : count + " ender nodes";
		GuiDraw.text(graphics, font, headline, x + 12, y + 19, 0xFFF4F4F5, false);

		if (nearest != null) {
			double distance = nearest.distanceTo(eyes);
			float yaw = nearest.yawTo(eyes);
			float delta = GuiDraw.wrapDegrees(yaw - client.player.getYRot());
			String detail = GuiDraw.meters(distance) + "  ·  " + GuiDraw.compass(delta);
			GuiDraw.text(graphics, font, detail, x + 12, y + 33, 0xFFD4D4D8, false);

			float barWidth = width - 24;
			float needle = Mth.clamp((delta + 180.0f) / 360.0f, 0.0f, 1.0f);
			GuiDraw.fill(graphics, x + 12, y + 47, barWidth, 2, 0x33FFFFFF);
			GuiDraw.fill(graphics, x + 12 + needle * (barWidth - 4), y + 45, 4, 6, accent);
		}
	}
}
