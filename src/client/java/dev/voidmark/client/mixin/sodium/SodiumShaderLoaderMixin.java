package dev.voidmark.client.mixin.sodium;

import dev.voidmark.client.visual.WorldTint;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderLoader.class, remap = false)
public class SodiumShaderLoaderMixin {
	@Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
	private static void voidmark$injectWorldTint(Identifier name, CallbackInfoReturnable<String> cir) {
		String path = name.getPath();
		if (!path.endsWith(".fsh") || !path.contains("block_layer")) {
			return;
		}
		cir.setReturnValue(WorldTint.injectTerrainFragmentSource(cir.getReturnValue()));
	}
}
