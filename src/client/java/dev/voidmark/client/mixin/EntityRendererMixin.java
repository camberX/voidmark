package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.NickHider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void voidmark$nickTag(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if (state.nameTag != null) {
			state.nameTag = NickHider.rewrite(state.nameTag);
		}
	}
}
