package dev.voidmark.client.mixin.sodium;

import dev.voidmark.client.visual.WorldTint;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultShaderInterface.class, remap = false)
public class SodiumDefaultShaderInterfaceMixin {
	@Unique
	private GlUniformFloat4v voidmark$worldTint;

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void voidmark$bindWorldTint(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
		this.voidmark$worldTint = context.bindUniformOptional("u_WorldTint", GlUniformFloat4v::new);
	}

	@Inject(method = "setupState", at = @At("RETURN"), remap = false)
	private void voidmark$uploadWorldTint(CallbackInfo ci) {
		if (this.voidmark$worldTint == null) {
			return;
		}
		int rgb = WorldTint.shaderRgb();
		this.voidmark$worldTint.set(
			((rgb >> 16) & 0xFF) / 255f,
			((rgb >> 8) & 0xFF) / 255f,
			(rgb & 0xFF) / 255f,
			WorldTint.shaderStrength()
		);
	}
}
