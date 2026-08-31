package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.WorldTint;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void voidmark$tintSkyState(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		state.skyColor = WorldTint.tintSky(state.skyColor);
		state.sunriseAndSunsetColor = WorldTint.tintSky(state.sunriseAndSunsetColor);
	}

	@Inject(method = "renderEndSky", at = @At("RETURN"))
	private void voidmark$tintEndSky(CallbackInfo ci) {
		if (!WorldTint.skyTintActive()) {
			return;
		}
		((SkyRenderer) (Object) this).renderSkyDisc(WorldTint.skyDiscColor(0xFF120018));
	}
}
