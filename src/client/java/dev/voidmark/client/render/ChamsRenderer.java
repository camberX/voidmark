package dev.voidmark.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mixin.RenderPipelinesAccessor;
import dev.voidmark.client.mixin.RenderSetupAccessor;
import dev.voidmark.client.mixin.RenderSetupTextureBindingAccessor;
import dev.voidmark.client.mixin.RenderTypeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colored player/mob models. Fill is an unlit solid, Default keeps lighting on
 * a solid color, Tint multiplies the real skin. Through-walls draws a depth-less
 * pass first so the model stays visible behind blocks.
 */
public final class ChamsRenderer {
	public enum Mode {
		FILL,
		DEFAULT,
		TINT
	}

	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
	private static final int FULL_BRIGHT = 0x00F000F0;
	private static final double MAX_RANGE_SQ = 96.0 * 96.0;
	private static final ThreadLocal<Integer> PREVIEW = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Integer> SKIP = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Style> ACTIVE = new ThreadLocal<>();
	private static final ThreadLocal<EntityRenderState> SELF_STATE = new ThreadLocal<>();
	private static final Map<CacheKey, RenderType> TYPES = new ConcurrentHashMap<>();

	private static RenderPipeline fillWalls;
	private static boolean registered;

	private ChamsRenderer() {
	}

	public record Style(Mode mode, int color, boolean throughWalls) {
	}

	private record CacheKey(String kind, Identifier texture) {
	}

	public static void init() {
		registerPipelines();
	}

	public static synchronized void registerPipelines() {
		if (registered) {
			return;
		}
		fillWalls = copyDepthless(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, "pipeline/chams_fill_walls");
		registered = true;
	}

	public static void rememberSelf(Entity entity, EntityRenderState state) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && entity == client.player) {
			SELF_STATE.set(state);
		}
	}

	public static void pushPreview() {
		PREVIEW.set(PREVIEW.get() + 1);
	}

	public static void popPreview() {
		int depth = PREVIEW.get() - 1;
		if (depth <= 0) {
			PREVIEW.remove();
		} else {
			PREVIEW.set(depth);
		}
	}

	public static boolean previewing() {
		return PREVIEW.get() > 0;
	}

	public static void begin(LivingEntityRenderState state) {
		ACTIVE.remove();
		if (state == null || skipping()) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.chamsEnabled) {
			return;
		}
		if (!previewing()) {
			if (state == SELF_STATE.get()) {
				return;
			}
			EntityType<?> type = state.entityType;
			if (type == null || !MobGlowRenderer.listed(type)) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			if (client.player == null || client.level == null) {
				return;
			}
			Vec3 camera = client.gameRenderer.getMainCamera().position();
			double dx = state.x - camera.x;
			double dy = state.y - camera.y;
			double dz = state.z - camera.z;
			if (dx * dx + dy * dy + dz * dz > MAX_RANGE_SQ) {
				return;
			}
			if (!config.chamsThroughWalls) {
				Vec3 eye = new Vec3(state.x, state.y + state.eyeHeight, state.z);
				if (occluded(client, camera, eye)) {
					return;
				}
			}
		}
		ACTIVE.set(new Style(mode(config), packColor(config), !previewing() && config.chamsThroughWalls));
	}

	public static void end() {
		ACTIVE.remove();
	}

	public static boolean shouldRewrite(RenderType renderType) {
		return ACTIVE.get() != null && renderType != null && !renderType.isOutline() && !skipping();
	}

	public static void submit(
		OrderedSubmitNodeCollector collector,
		Model<?> model,
		Object state,
		PoseStack pose,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int color,
		TextureAtlasSprite sprite,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumbling
	) {
		Style style = ACTIVE.get();
		if (style == null) {
			rawSubmit(collector, model, state, pose, renderType, lightCoords, overlayCoords, color, sprite, outlineColor, crumbling);
			return;
		}
		Identifier texture = style.mode == Mode.TINT ? textureOf(renderType) : WHITE;
		if (texture == null) {
			texture = WHITE;
		}
		int tinted = tint(style, color);
		int visibleLight = style.mode == Mode.FILL ? FULL_BRIGHT : lightCoords;
		RenderType visible = visibleType(style, texture, renderType);
		enterSkip();
		try {
			if (style.throughWalls) {
				RenderType wall = wallType(texture);
				if (wall != null) {
					rawSubmit(
						collector,
						model,
						state,
						pose,
						wall,
						FULL_BRIGHT,
						overlayCoords,
						tinted,
						sprite,
						0,
						crumbling
					);
				}
			}
			rawSubmit(
				collector,
				model,
				state,
				pose,
				visible,
				visibleLight,
				overlayCoords,
				tinted,
				sprite,
				outlineColor,
				crumbling
			);
		} finally {
			leaveSkip();
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void rawSubmit(
		OrderedSubmitNodeCollector collector,
		Model model,
		Object state,
		PoseStack pose,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int color,
		TextureAtlasSprite sprite,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumbling
	) {
		collector.submitModel(model, state, pose, renderType, lightCoords, overlayCoords, color, sprite, outlineColor, crumbling);
	}

	public static Mode mode(VoidmarkConfig config) {
		return switch (VoidmarkConfig.normalizeChamsMode(config.chamsMode)) {
			case "fill" -> Mode.FILL;
			case "tint" -> Mode.TINT;
			default -> Mode.DEFAULT;
		};
	}

	public static int packColor(VoidmarkConfig config) {
		float opacity = VoidmarkConfig.clamp(config.chamsOpacity, 0.15f, 1f);
		int alpha = Math.round(opacity * 255f);
		return (alpha << 24) | (config.chamsRgb & 0xFFFFFF);
	}

	private static int tint(Style style, int original) {
		int rgb = style.color & 0xFFFFFF;
		int alpha = Math.max(1, (style.color >>> 24) & 0xFF);
		if (style.mode == Mode.TINT) {
			float t = alpha / 255f;
			int r = lerp(255, (rgb >> 16) & 0xFF, t);
			int g = lerp(255, (rgb >> 8) & 0xFF, t);
			int b = lerp(255, rgb & 0xFF, t);
			return ARGB.multiply(original, ARGB.color(255, r, g, b));
		}
		return ARGB.color(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
	}

	private static int lerp(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}

	private static RenderType visibleType(Style style, Identifier texture, RenderType original) {
		if (style.mode == Mode.TINT) {
			return original;
		}
		if (style.mode == Mode.FILL) {
			return RenderTypes.entityTranslucentEmissive(texture);
		}
		boolean translucent = ((style.color >>> 24) & 0xFF) < 250;
		return translucent ? RenderTypes.entityTranslucent(texture) : RenderTypes.entitySolid(texture);
	}

	private static RenderType wallType(Identifier texture) {
		registerPipelines();
		if (fillWalls == null) {
			return RenderTypes.entityTranslucentEmissive(texture);
		}
		return TYPES.computeIfAbsent(new CacheKey("walls", texture), key -> createWall(fillWalls, key.kind, key.texture));
	}

	private static RenderType createWall(RenderPipeline pipeline, String kind, Identifier texture) {
		RenderSetup setup = RenderSetup.builder(pipeline)
			.withTexture("Sampler0", texture)
			.useLightmap()
			.useOverlay()
			.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
			.createRenderSetup();
		return RenderTypeAccessor.voidmark$create("voidmark_chams_" + kind, setup);
	}

	private static Identifier textureOf(RenderType type) {
		try {
			RenderSetup setup = ((RenderTypeAccessor) (Object) type).voidmark$state();
			Map<String, Object> textures = ((RenderSetupAccessor) (Object) setup).voidmark$textures();
			if (textures == null || textures.isEmpty()) {
				return null;
			}
			Object binding = textures.get("Sampler0");
			if (binding == null) {
				binding = textures.values().iterator().next();
			}
			if (binding == null) {
				return null;
			}
			return ((RenderSetupTextureBindingAccessor) binding).voidmark$location();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static RenderPipeline copyDepthless(RenderPipeline source, String path) {
		if (source == null) {
			return null;
		}
		try {
			RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Voidmark.id(path))
				.withVertexShader(source.getVertexShader())
				.withFragmentShader(source.getFragmentShader())
				.withColorTargetState(source.getColorTargetState())
				.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
				.withCull(source.isCull())
				.withPolygonMode(source.getPolygonMode())
				.withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode());
			for (String sampler : source.getSamplers()) {
				builder.withSampler(sampler);
			}
			for (RenderPipeline.UniformDescription uniform : source.getUniforms()) {
				if (uniform.type() == UniformType.TEXEL_BUFFER && uniform.textureFormat() != null) {
					builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
				} else if (uniform.type() != null) {
					builder.withUniform(uniform.name(), uniform.type());
				}
			}
			var defines = source.getShaderDefines();
			if (defines != null && !defines.isEmpty()) {
				for (String flag : defines.flags()) {
					builder.withShaderDefine(flag);
				}
				defines.values().forEach((name, value) -> {
					try {
						if (value.indexOf('.') >= 0) {
							builder.withShaderDefine(name, Float.parseFloat(value));
						} else {
							builder.withShaderDefine(name, Integer.parseInt(value));
						}
					} catch (NumberFormatException ignored) {
						builder.withShaderDefine(name);
					}
				});
			}
			return RenderPipelinesAccessor.voidmark$register(builder.build());
		} catch (Throwable exception) {
			Voidmark.LOGGER.warn("Could not register chams pipeline {}", path, exception);
			return null;
		}
	}

	private static boolean skipping() {
		return SKIP.get() > 0;
	}

	private static void enterSkip() {
		SKIP.set(SKIP.get() + 1);
	}

	private static void leaveSkip() {
		int depth = SKIP.get() - 1;
		if (depth <= 0) {
			SKIP.remove();
		} else {
			SKIP.set(depth);
		}
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
}
