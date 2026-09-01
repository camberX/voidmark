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
import net.minecraft.util.Mth;
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
 * Tags scale with camera distance, then by the Size slider.
 */
public final class NametagRenderer {
	private static final float TAG_H = 16f;
	private static final float TAG_GAP = 3f;
	private static final float PAD_X = 7f;
	private static final String BADGE_BRAND = "VOIDMARK";
	private static final String BADGE_ROLE = "Dev";

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
			tags.add(new Tag(label(player), dist, x, y, player == client.player));
		}
		tags.sort(Comparator.comparingDouble((Tag tag) -> tag.dist).reversed());
		Font font = client.font;
		float userScale = VoidmarkConfig.clampHudScale(config.nametagScale);
		for (Tag tag : tags) {
			drawStack(graphics, font, tag, config.nametagDistance, userScale);
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

	private static void drawStack(GuiGraphicsExtractor graphics, Font font, Tag tag, boolean showDistance, float userScale) {
		float scale = distanceScale(tag.dist) * userScale;
		boolean showDist = showDistance && !tag.self;
		float nameW = pillWidth(font.width(tag.name), showDist ? distWidth(font, tag.dist) : 0f);
		float cursorY = -2f;

		graphics.pose().pushMatrix();
		graphics.pose().translate(tag.x, tag.y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		if (tag.self) {
			float badgeW = badgeWidth(font);
			cursorY -= TAG_H;
			drawBadge(graphics, font, -badgeW * 0.5f, cursorY, badgeW);
			cursorY -= TAG_GAP;
		}

		cursorY -= TAG_H;
		drawName(graphics, font, tag, -nameW * 0.5f, cursorY, nameW, showDist);
		graphics.pose().popMatrix();
	}

	private static void drawName(
		GuiGraphicsExtractor graphics,
		Font font,
		Tag tag,
		float x,
		float y,
		float w,
		boolean showDist
	) {
		GuiDraw.panel(graphics, x, y, w, TAG_H, 4, Theme.WINDOW, Theme.LINE);
		float textY = y + (TAG_H - font.lineHeight) * 0.5f;
		GuiDraw.text(graphics, font, tag.name, x + PAD_X, textY, 0xFFFFFFFF, false);
		if (showDist) {
			String dist = GuiDraw.meters(tag.dist);
			float distX = x + w - PAD_X - GuiDraw.smallWidth(font, dist);
			GuiDraw.small(graphics, font, dist, distX, GuiDraw.middle(y, TAG_H) + 1f, Theme.MUTED);
		}
	}

	private static void drawBadge(GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		GuiDraw.panel(graphics, x, y, w, TAG_H, 4, Theme.WINDOW, Theme.LINE);
		float textY = GuiDraw.middle(y, TAG_H);
		float cx = x + PAD_X;
		GuiDraw.menu(graphics, font, BADGE_BRAND, cx, textY, Theme.ACCENT);
		cx += GuiDraw.menuWidth(font, BADGE_BRAND) + 3f;
		GuiDraw.small(graphics, font, BADGE_ROLE, cx, textY, Theme.WARN);
	}

	private static float pillWidth(float nameW, float distW) {
		return PAD_X + nameW + (distW > 0f ? 5f + distW : 0f) + PAD_X;
	}

	private static float distWidth(Font font, double dist) {
		return GuiDraw.smallWidth(font, GuiDraw.meters(dist));
	}

	private static float badgeWidth(Font font) {
		return PAD_X + GuiDraw.menuWidth(font, BADGE_BRAND) + 3f + GuiDraw.smallWidth(font, BADGE_ROLE) + PAD_X;
	}

	/** Larger up close, smaller far away. Anchored so ~12m reads as 1.0 before the Size slider. */
	private static float distanceScale(double dist) {
		return Mth.clamp(24f / (12f + (float) dist), 0.52f, 1.35f);
	}

	private record Tag(Component name, double dist, float x, float y, boolean self) {
	}
}
