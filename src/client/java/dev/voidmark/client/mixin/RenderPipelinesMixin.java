package dev.voidmark.client.mixin;

import dev.voidmark.client.render.ChamsRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPipelines.class)
public class RenderPipelinesMixin {
	@Inject(method = "<clinit>", at = @At("RETURN"))
	private static void voidmark$chamsPipelines(CallbackInfo ci) {
		try {
			ChamsRenderer.registerPipelines();
		} catch (Throwable ignored) {
		}
	}
}
