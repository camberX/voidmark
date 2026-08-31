package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.WorldTint;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Lightmap.class)
public class LightmapMixin {
	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
	private LightmapRenderState voidmark$tintWorld(LightmapRenderState state) {
		WorldTint.tintLightmap(state);
		return state;
	}
}
