package dev.voidmark.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import dev.voidmark.client.render.GlowBlurRadius;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(PostPass.class)
public class PostPassMixin {
	@Shadow
	@Final
	private Map<String, GpuBuffer> customUniforms;

	@ModifyArg(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"
		),
		index = 1
	)
	private int voidmark$uniformCopyDst(int usage) {
		return usage | GpuBuffer.USAGE_COPY_DST;
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void voidmark$trackGlowBlur(
		RenderPipeline pipeline,
		Identifier outputTargetId,
		Map<String, List<UniformValue>> uniforms,
		List<PostPass.Input> inputs,
		CallbackInfo ci
	) {
		GlowBlurRadius.register((PostPass) (Object) this, pipeline, uniforms);
	}

	@Inject(method = "addToFrame", at = @At("HEAD"))
	private void voidmark$applyGlowRadius(
		FrameGraphBuilder builder,
		Map<Identifier, ResourceHandle<RenderTarget>> targets,
		GpuBufferSlice projection,
		CallbackInfo ci
	) {
		GlowBlurRadius.apply((PostPass) (Object) this, customUniforms);
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void voidmark$forgetGlowBlur(CallbackInfo ci) {
		GlowBlurRadius.unregister((PostPass) (Object) this);
	}
}
