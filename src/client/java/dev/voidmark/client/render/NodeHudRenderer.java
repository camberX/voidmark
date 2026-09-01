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
	public static final float WIDTH = 168;
	public static final float HEIGHT_EMPTY = 42;
	public static final float HEIGHT_TRACK = 58;

	private NodeHudRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("ender_nodes"),
			NodeHudRenderer::extract
		);
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		return tracking() ? HEIGHT_TRACK : HEIGHT_EMPTY;
	}

	private static boolean tracking() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && SkyblockLocation.shouldMarkNodes() && EnderNodeTracker.get().count() > 0;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}

		VoidmarkConfig config = VoidmarkConfig.get();
		boolean editor = HudLayout.editorOpen();
		if (!config.hudEnabled) {
			return;
		}
		if (!editor && !SkyblockLocation.shouldMarkNodes()) {
			return;
		}

		Font font = client.font;
		HudLayout.Box box = HudLayout.box(HudLayout.Id.NODES, font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client, font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.NODES), deltaTracker);
	}

	public static void draw(GuiGraphicsExtractor graphics, Minecraft client, Font font, float x, float y, float scale, DeltaTracker deltaTracker) {
		int count = EnderNodeTracker.get().count();
		EnderNodeTracker.TrackedNode nearest = null;
		if (client.player != null) {
			Vec3 eyes = client.player.getEyePosition(deltaTracker.getGameTimeDeltaPartialTick(false));
			nearest = EnderNodeTracker.get().nearest(eyes);
		}
		float width = WIDTH;
		float height = nearest == null ? HEIGHT_EMPTY : HEIGHT_TRACK;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		GuiDraw.panel(graphics, 0, 0, width, height, 6, Theme.WINDOW, Theme.LINE, Theme.ACCENT);

		GuiDraw.small(graphics, font, "NODES", 10, 5, Theme.ACCENT);
		String headline = count == 0 ? "No nodes in range" : count == 1 ? "1 ender node" : count + " ender nodes";
		GuiDraw.menu(graphics, font, headline, 10, 18, Theme.TEXT);

		if (nearest != null && client.player != null) {
			Vec3 eyes = client.player.getEyePosition(deltaTracker.getGameTimeDeltaPartialTick(false));
			double distance = nearest.distanceTo(eyes);
			float yaw = nearest.yawTo(eyes);
			float delta = GuiDraw.wrapDegrees(yaw - client.player.getYRot());
			String detail = GuiDraw.meters(distance) + "  ·  " + GuiDraw.compass(delta);
			GuiDraw.menu(graphics, font, detail, 10, 32, Theme.MUTED);

			float barWidth = width - 24;
			float needle = Mth.clamp((delta + 180.0f) / 360.0f, 0.0f, 1.0f);
			GuiDraw.fill(graphics, 12, 47, barWidth, 2, Theme.TRACK);
			GuiDraw.fill(graphics, 12 + needle * (barWidth - 4), 45, 4, 6, Theme.ACCENT);
		}
		graphics.pose().popMatrix();
	}
}
