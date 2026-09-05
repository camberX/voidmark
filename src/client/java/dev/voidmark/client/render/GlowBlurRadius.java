package dev.voidmark.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites the glow-blur UBO each frame so the Radius slider reaches the
 * post shader. Vanilla bakes JSON uniforms once when the pass is built.
 */
public final class GlowBlurRadius {
	public static final float MIN = 2f;
	public static final float MAX = 32f;
	public static final float DEFAULT = 12f;
	private static final Identifier SHADER = Voidmark.id("post/glow_blur");
	private static final IdentityHashMap<PostPass, Vector2f> DIRECTIONS = new IdentityHashMap<>();

	private GlowBlurRadius() {
	}

	public static boolean isGlowBlur(RenderPipeline pipeline) {
		return pipeline != null && SHADER.equals(pipeline.getFragmentShader());
	}

	public static void register(PostPass pass, RenderPipeline pipeline, Map<String, List<UniformValue>> uniforms) {
		if (!isGlowBlur(pipeline) || uniforms == null) {
			return;
		}
		Vector2f dir = new Vector2f(1f, 0f);
		List<UniformValue> values = uniforms.get("BlurConfig");
		if (values != null) {
			for (UniformValue value : values) {
				if (value instanceof UniformValue.Vec2Uniform vec) {
					dir.set(vec.value());
				}
			}
		}
		DIRECTIONS.put(pass, dir);
	}

	public static void unregister(PostPass pass) {
		DIRECTIONS.remove(pass);
	}

	public static void apply(PostPass pass, Map<String, GpuBuffer> customUniforms) {
		Vector2f dir = DIRECTIONS.get(pass);
		if (dir == null || customUniforms == null) {
			return;
		}
		GpuBuffer buffer = customUniforms.get("BlurConfig");
		if (buffer == null || buffer.isClosed()) {
			return;
		}
		float radius = VoidmarkConfig.clamp(VoidmarkConfig.get().mobGlowRadius, MIN, MAX);
		if (BlockOutlineGlow.cheapBlur()) {
			radius = Math.min(radius, 5f);
		}
		int size = new Std140SizeCalculator().putVec2().putFloat().get();
		if (buffer.size() < size) {
			return;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
				buffer.slice(),
				Std140Builder.onStack(stack, size)
					.putVec2(dir.x, dir.y)
					.putFloat(radius)
					.get()
			);
		}
	}
}
