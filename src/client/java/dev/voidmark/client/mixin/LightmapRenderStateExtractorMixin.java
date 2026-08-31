package dev.voidmark.client.mixin;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.visual.WorldTint;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
	@Shadow
	private boolean needsUpdate;

	@Inject(method = "extract", at = @At("HEAD"))
	private void voidmark$refreshTintedLightmap(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (VoidmarkConfig.get().worldTintEnabled && !WorldTint.sodiumTerrainTint()) {
			this.needsUpdate = true;
		}
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void voidmark$tintLightmap(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		WorldTint.tintLightmap(state);
	}
}
