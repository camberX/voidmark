package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Anim;
import dev.voidmark.client.ui.MenuFont;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Voidmark-styled player nametags that keep drawing past vanilla's 64-block cutoff.
 * Tags scale with camera distance, then by the Size slider. UUID v2 entities are
 * treated as NPCs and skipped; only v4 (and the local player) get a plate.
 */
public final class NametagRenderer {
	private static final float TAG_H = 14f;
	private static final float BADGE_H = 11f;
	private static final float PAD_X = 8f;
	private static final String BADGE_BRAND = "VOIDMARK";
	private static final String BADGE_ROLE = "Dev";
	private static final UUID DEV_UUID = UUID.fromString("f1b21931-667f-4be2-91bb-a06074978e0e");
	private static final int NPC_UUID_VERSION = 2;
	private static final int PLAYER_UUID_VERSION = 4;
	private static final Map<UUID, Track> FADES = new HashMap<>();
	private static long lastNs = System.nanoTime();

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
			FADES.clear();
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.nametagsEnabled) {
			FADES.clear();
			return;
		}
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		double maxRange = VoidmarkConfig.clamp(config.nametagRange, 64, 256);
		double maxSq = maxRange * maxRange;
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		Map<UUID, Tag> visible = new HashMap<>();
		for (AbstractClientPlayer player : client.level.players()) {
			if (!include(client, player, config, maxSq, camPos)) {
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
			visible.put(player.getUUID(), new Tag(
				label(player),
				dist,
				x,
				y,
				player == client.player,
				DEV_UUID.equals(player.getUUID()),
				1f
			));
		}
		List<Tag> tags = tickFades(visible, dt);
		tags.sort(Comparator.comparingDouble((Tag tag) -> tag.dist).reversed());
		Font font = client.font;
		float userScale = VoidmarkConfig.clampHudScale(config.nametagScale);
		float opacity = VoidmarkConfig.clamp(config.nametagOpacity, 0.15f, 1f);
		for (Tag tag : tags) {
			drawStack(graphics, font, tag, config.nametagDistance, userScale, opacity * tag.fade);
		}
	}

	private static List<Tag> tickFades(Map<UUID, Tag> visible, float dt) {
		for (Map.Entry<UUID, Tag> entry : visible.entrySet()) {
			Track track = FADES.computeIfAbsent(entry.getKey(), id -> new Track());
			Tag tag = entry.getValue();
			track.name = tag.name;
			track.dist = tag.dist;
			track.x = tag.x;
			track.y = tag.y;
			track.self = tag.self;
			track.dev = tag.dev;
			track.seen = true;
			track.fade = approach(track.fade, 1f, 9f, dt);
		}
		List<Tag> out = new ArrayList<>();
		Iterator<Map.Entry<UUID, Track>> it = FADES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Track> entry = it.next();
			Track track = entry.getValue();
			if (!visible.containsKey(entry.getKey())) {
				track.seen = false;
				track.fade = approach(track.fade, 0f, 8f, dt);
				if (track.fade < 0.02f) {
					it.remove();
					continue;
				}
			}
			if (track.fade > 0.02f && track.name != null) {
				out.add(new Tag(track.name, track.dist, track.x, track.y, track.self, track.dev, track.fade));
			}
		}
		return out;
	}

	private static boolean include(
		Minecraft client,
		AbstractClientPlayer player,
		VoidmarkConfig config,
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
		return !(player.isDiscrete() && !config.nametagThroughWalls && distSq > 32.0 * 32.0);
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
		boolean showDist = showDistance && !tag.self;
		Component name = menuText(tag.name);
		float nameW = pillWidth(nameWidth(font, name), showDist ? distWidth(font, tag.dist) : 0f);
		float badgeW = tag.dev ? badgeWidth(font) : 0f;
		float w = nameW;
		float x = -w * 0.5f;
		float y = -2f - TAG_H;

		graphics.pose().pushMatrix();
		graphics.pose().translate(tag.x, tag.y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		int pane = Anim.fade(Theme.WINDOW, alpha);
		int line = Anim.fade(Theme.LINE, alpha);
		GuiDraw.panel(graphics, x, y, w, TAG_H, 5, pane, line);
		drawNameText(graphics, font, name, tag, x, y, w, showDist, alpha);
		if (tag.dev) {
			float bx = -badgeW * 0.5f;
			float by = y - BADGE_H - 2f;
			GuiDraw.panel(graphics, bx, by, badgeW, BADGE_H, 4, pane, line);
			drawBadgeText(graphics, font, bx, by, badgeW, alpha);
		}
		graphics.pose().popMatrix();
	}

	private static Component menuText(Component name) {
		return applyFont(name, MenuFont.SMALL);
	}

	private static Component applyFont(Component src, Style fontStyle) {
		if (src == null) {
			return Component.empty().setStyle(fontStyle);
		}
		MutableComponent out = src.plainCopy().setStyle(src.getStyle().withFont(fontStyle.getFont()));
		for (Component sibling : src.getSiblings()) {
			out.append(applyFont(sibling, fontStyle));
		}
		return out;
	}

	private static float nameWidth(Font font, Component name) {
		return Math.max(font.width(name), GuiDraw.smallWidth(font, name.getString()));
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
		float alpha
	) {
		GuiDraw.text(graphics, font, name, x + PAD_X, GuiDraw.middle(y, TAG_H), Anim.fade(0xFFFFFFFF, alpha), false);
		if (showDist) {
			String dist = GuiDraw.meters(tag.dist);
			float distX = x + w - PAD_X - GuiDraw.smallWidth(font, dist);
			GuiDraw.small(graphics, font, dist, distX, GuiDraw.middle(y, TAG_H) + 1f, Anim.fade(Theme.MUTED, alpha));
		}
	}

	private static void drawBadgeText(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float alpha) {
		float brandW = GuiDraw.menuWidth(font, BADGE_BRAND);
		float roleW = GuiDraw.smallWidth(font, BADGE_ROLE);
		float inner = brandW + 3f + roleW;
		float cx = x + (w - inner) * 0.5f;
		float textY = GuiDraw.middle(y, BADGE_H);
		GuiDraw.menu(graphics, font, BADGE_BRAND, cx, textY, Anim.fade(Theme.ACCENT, alpha));
		GuiDraw.small(graphics, font, BADGE_ROLE, cx + brandW + 3f, textY, Anim.fade(Theme.WARN, alpha));
	}

	private static float pillWidth(float nameW, float distW) {
		return PAD_X + nameW + (distW > 0f ? 5f + distW : 0f) + PAD_X;
	}

	private static float distWidth(Font font, double dist) {
		return GuiDraw.smallWidth(font, GuiDraw.meters(dist));
	}

	private static float badgeWidth(Font font) {
		return 5f + GuiDraw.menuWidth(font, BADGE_BRAND) + 3f + GuiDraw.smallWidth(font, BADGE_ROLE) + 5f;
	}

	public static void drawPreview(GuiGraphicsExtractor graphics, Font font, float cx, float y, Component name, boolean dev) {
		if (name == null || name.getString().isBlank()) {
			return;
		}
		drawStack(graphics, font, new Tag(name, 12, cx, y, true, dev, 1f), false, 0.82f, 1f);
	}

	public static boolean isDev(UUID uuid) {
		return uuid != null && DEV_UUID.equals(uuid);
	}

	private static float approach(float current, float target, float speed, float dt) {
		if (Math.abs(target - current) < 0.003f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	/** Larger up close, smaller far away. Anchored so ~12m reads as 1.0 before the Size slider. */
	private static float distanceScale(double dist) {
		return Mth.clamp(24f / (12f + (float) dist), 0.52f, 1.35f);
	}

	private record Tag(Component name, double dist, float x, float y, boolean self, boolean dev, float fade) {
	}

	private static final class Track {
		Component name;
		double dist;
		float x;
		float y;
		boolean self;
		boolean dev;
		boolean seen;
		float fade;
	}
}
