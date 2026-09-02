package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Anim;
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
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Voidmark-styled player nametags that keep drawing past vanilla's 64-block cutoff.
 * Tags scale with camera distance, then by the Size slider. UUID v2 entities are
 * treated as NPCs and skipped; only v4 (and the local player) get a plate.
 */
public final class NametagRenderer {
	private static final float TAG_H = 14f;
	private static final float BADGE_H = 13f;
	private static final float PAD_X = 8f;
	private static final String BADGE_BRAND = "VOIDMARK";
	private static final String BADGE_ROLE = "Dev";
	private static final UUID DEV_UUID = UUID.fromString("f1b21931-667f-4be2-91bb-a06074978e0e");
	private static final int NPC_UUID_VERSION = 2;
	private static final int PLAYER_UUID_VERSION = 4;

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
		return entity instanceof Avatar && VoidmarkConfig.get().nametagsEnabled;
	}

	public static boolean hidingVanillaState(EntityRenderState state) {
		if (!VoidmarkConfig.get().nametagsEnabled || state.entityType == null) {
			return false;
		}
		return state.entityType == EntityType.PLAYER || state.entityType == EntityType.MANNEQUIN;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		boolean plates = config.nametagsEnabled;
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		double maxRange = plates ? VoidmarkConfig.clamp(config.nametagRange, 64, 256) : 64;
		double maxSq = maxRange * maxRange;
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		boolean through = plates && config.nametagThroughWalls;
		List<Tag> tags = new ArrayList<>();
		for (AbstractClientPlayer player : client.level.players()) {
			boolean dev = DEV_UUID.equals(player.getUUID());
			if (!plates && !dev) {
				continue;
			}
			if (!include(client, player, through, maxSq, camPos)) {
				continue;
			}
			Vec3 head = player.getPosition(partial).add(0.0, player.getBbHeight() + (plates ? 0.28 : 0.50), 0.0);
			Vec3 rel = head.subtract(camPos);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			if (!through && occluded(client, camPos, head)) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			double dist = Math.sqrt(player.distanceToSqr(camPos));
			tags.add(new Tag(
				label(player),
				dist,
				x,
				y,
				player == client.player,
				dev,
				plates
			));
		}
		tags.sort(Comparator.comparingDouble((Tag tag) -> tag.dist).reversed());
		Font font = client.font;
		float userScale = plates ? VoidmarkConfig.clampHudScale(config.nametagScale) : 1f;
		float opacity = plates ? VoidmarkConfig.clamp(config.nametagOpacity, 0.15f, 1f) : 1f;
		for (Tag tag : tags) {
			drawStack(graphics, font, tag, config.nametagDistance, userScale, opacity);
		}
	}

	private static boolean include(
		Minecraft client,
		AbstractClientPlayer player,
		boolean throughWalls,
		double maxSq,
		Vec3 camPos
	) {
		LocalPlayer self = client.player;
		if (player.isInvisibleTo(self)) {
			return false;
		}
		if (!realPlayer(player, self)) {
			return false;
		}
		if (player == self && client.options.getCameraType().isFirstPerson()) {
			return false;
		}
		double distSq = player.distanceToSqr(camPos);
		if (distSq > maxSq) {
			return false;
		}
		return !(player.isDiscrete() && !throughWalls && distSq > 32.0 * 32.0);
	}

	/** Hypixel NPCs use UUID version 2. Real accounts use version 4. */
	private static boolean realPlayer(AbstractClientPlayer player, LocalPlayer self) {
		int version = player.getUUID().version();
		if (version == NPC_UUID_VERSION) {
			return false;
		}
		return player == self || version == PLAYER_UUID_VERSION;
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
		if (player == Minecraft.getInstance().player && VoidmarkConfig.get().nickEnabled) {
			Component nick = NickHider.formattedNick();
			if (nick != null && !nick.getString().isBlank()) {
				return nick;
			}
		}
		Component name = player.getDisplayName();
		if (player == Minecraft.getInstance().player) {
			name = NickHider.rewrite(name);
		}
		return name == null ? Component.literal(player.getScoreboardName()) : name;
	}

	private static void drawStack(GuiGraphicsExtractor graphics, Font font, Tag tag, boolean showDistance, float userScale, float alpha) {
		if (alpha < 0.02f) {
			return;
		}
		float scale = distanceScale(tag.dist) * userScale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(tag.x, tag.y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		int pane = Anim.fade(Theme.WINDOW, alpha);
		int line = Anim.fade(Theme.LINE, alpha);
		if (tag.plates) {
			boolean showDist = showDistance && !tag.self;
			Component name = tag.name;
			float nameW = pillWidth(font.width(name), showDist ? distWidth(font, tag.dist) : 0f);
			float badgeW = tag.dev ? badgeWidth(font) : 0f;
			float w = Math.max(nameW, badgeW);
			float h = TAG_H + (tag.dev ? BADGE_H : 0f);
			float x = -w * 0.5f;
			float y = -2f - h;
			GuiDraw.panel(graphics, x, y, w, h, 5, pane, line);
			if (tag.dev) {
				drawBadgeText(graphics, font, x, y, w, alpha);
				drawNameText(graphics, font, name, tag, x, y + BADGE_H, w, showDist, true, alpha);
			} else {
				drawNameText(graphics, font, name, tag, x, y, w, showDist, false, alpha);
			}
		} else if (tag.dev) {
			float badgeW = badgeWidth(font);
			float bx = -badgeW * 0.5f;
			float by = -14f - BADGE_H;
			GuiDraw.panel(graphics, bx, by, badgeW, BADGE_H, 5, pane, line);
			drawBadgeText(graphics, font, bx, by, badgeW, alpha);
		}
		graphics.pose().popMatrix();
	}

	private static void drawNameText(
		GuiGraphicsExtractor graphics,
		Font font,
		Component name,
		Tag tag,
		float x,
		float y,
		float w,
		boolean showDist,
		boolean center,
		float alpha
	) {
		float nameW = font.width(name);
		float distW = showDist ? distWidth(font, tag.dist) : 0f;
		float inner = nameW + (distW > 0f ? 5f + distW : 0f);
		float tx = center ? x + (w - inner) * 0.5f : x + PAD_X;
		GuiDraw.text(graphics, font, name, tx, GuiDraw.middle(y, TAG_H), Anim.fade(0xFFFFFFFF, alpha), false);
		if (showDist) {
			GuiDraw.text(
				graphics,
				font,
				GuiDraw.meters(tag.dist),
				tx + nameW + 5f,
				GuiDraw.middle(y, TAG_H),
				Anim.fade(Theme.MUTED, alpha),
				false
			);
		}
	}

	private static void drawBadgeText(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float alpha) {
		float brandW = GuiDraw.menuWidth(font, BADGE_BRAND);
		float roleW = GuiDraw.menuWidth(font, BADGE_ROLE);
		float inner = brandW + 3f + roleW;
		float cx = x + (w - inner) * 0.5f;
		float textY = GuiDraw.middle(y, BADGE_H);
		GuiDraw.menu(graphics, font, BADGE_BRAND, cx, textY, Anim.fade(Theme.ACCENT, alpha));
		GuiDraw.menu(graphics, font, BADGE_ROLE, cx + brandW + 3f, textY, Anim.fade(Theme.WARN, alpha));
	}

	private static float pillWidth(float nameW, float distW) {
		return PAD_X + nameW + (distW > 0f ? 5f + distW : 0f) + PAD_X;
	}

	private static float distWidth(Font font, double dist) {
		return font.width(GuiDraw.meters(dist));
	}

	private static float badgeWidth(Font font) {
		return PAD_X + GuiDraw.menuWidth(font, BADGE_BRAND) + 3f + GuiDraw.menuWidth(font, BADGE_ROLE) + PAD_X;
	}

	/** Vanilla nametag: default font, option background, white text, centered. */
	public static void drawVanilla(GuiGraphicsExtractor graphics, Font font, float cx, float y, Component name) {
		if (name == null || name.getString().isBlank()) {
			return;
		}
		int w = font.width(name);
		int x = Math.round(cx - w * 0.5f);
		int top = Math.round(y);
		int bg = Minecraft.getInstance().options.getBackgroundColor(0.25f);
		graphics.fill(x - 1, top - 1, x + w + 1, top + 9, bg);
		graphics.text(font, name, x, top, 0xFFFFFFFF, false);
	}

	public static boolean isDev(UUID uuid) {
		return uuid != null && DEV_UUID.equals(uuid);
	}

	/** Larger up close, smaller far away. Anchored so ~12m reads as 1.0 before the Size slider. */
	private static float distanceScale(double dist) {
		return Mth.clamp(24f / (12f + (float) dist), 0.52f, 1.35f);
	}

	private record Tag(Component name, double dist, float x, float y, boolean self, boolean dev, boolean plates) {
	}
}
