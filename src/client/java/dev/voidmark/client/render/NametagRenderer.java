package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import dev.voidmark.client.visual.NickHider;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Voidmark-styled player nametags that keep drawing past vanilla's 64-block cutoff.
 */
public final class NametagRenderer {
	private static final float TAG_H = 14f;
	private static final float NAME_MAX = 92f;

	private NametagRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("nametags"),
			NametagRenderer::extract
		);
	}

	public static boolean hidingVanilla(Entity entity) {
		return entity instanceof Player && VoidmarkConfig.get().nametagsEnabled;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.nametagsEnabled) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		double maxRange = VoidmarkConfig.clamp(config.nametagRange, 64, 256);
		double maxSq = maxRange * maxRange;
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		List<Tag> tags = new ArrayList<>();
		for (AbstractClientPlayer player : client.level.players()) {
			if (!include(client.player, player, camera, config, maxSq, camPos)) {
				continue;
			}
			Vec3 head = player.getPosition(partial).add(0.0, player.getBbHeight() + 0.28, 0.0);
			Vec3 rel = head.subtract(camPos);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			if (!config.nametagThroughWalls && occluded(client, camPos, head)) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			double dist = Math.sqrt(player.distanceToSqr(camPos));
			tags.add(new Tag(label(player), dist, x, y));
		}
		tags.sort(Comparator.comparingDouble((Tag tag) -> tag.dist).reversed());
		Font font = client.font;
		for (Tag tag : tags) {
			drawTag(graphics, font, tag, config.nametagDistance);
		}
	}

	private static boolean include(
		LocalPlayer self,
		AbstractClientPlayer player,
		Camera camera,
		VoidmarkConfig config,
		double maxSq,
		Vec3 camPos
	) {
		if (player.isInvisibleTo(self)) {
			return false;
		}
		if (player == self && !camera.isDetached()) {
			return false;
		}
		double distSq = player.distanceToSqr(camPos);
		if (distSq > maxSq) {
			return false;
		}
		return !(player.isDiscrete() && !config.nametagThroughWalls && distSq > 32.0 * 32.0);
	}

	private static boolean occluded(Minecraft client, Vec3 from, Vec3 to) {
		HitResult hit = client.level.clip(new ClipContext(
			from,
			to,
			ClipContext.Block.VISUAL,
			ClipContext.Fluid.NONE,
			client.player
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		return hit.getLocation().distanceToSqr(from) + 0.36 < to.distanceToSqr(from);
	}

	private static Component label(AbstractClientPlayer player) {
		Component name = player.getDisplayName();
		if (player == Minecraft.getInstance().player) {
			name = NickHider.rewrite(name);
		}
		return name == null ? Component.literal(player.getScoreboardName()) : name;
	}

	private static void drawTag(GuiGraphicsExtractor graphics, Font font, Tag tag, boolean showDist) {
		String dist = GuiDraw.meters(tag.dist);
		Component name = tag.name;
		float nameW = Math.min(NAME_MAX, font.width(name));
		float distW = showDist ? GuiDraw.smallWidth(font, dist) + 5f : 0f;
		float w = 8f + nameW + distW + 6f;
		float x = tag.x - w * 0.5f;
		float y = tag.y - TAG_H - 2f;
		GuiDraw.panel(graphics, x, y, w, TAG_H, 4, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		boolean clipped = GuiDraw.scissor(graphics, x + 6f, y, nameW, TAG_H);
		GuiDraw.text(graphics, font, name, x + 6f, GuiDraw.middle(y, TAG_H), 0xFFFFFFFF, false);
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
		if (showDist) {
			GuiDraw.small(graphics, font, dist, x + 6f + nameW + 4f, GuiDraw.middle(y, TAG_H) + 1f, Theme.MUTED);
		}
	}

	private record Tag(Component name, double dist, float x, float y) {
	}
}
