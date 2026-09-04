package dev.voidmark.client.mixin;

import dev.voidmark.client.combat.Hitsound;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public class AbstractArrowMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void voidmark$hitsoundArrow(EntityHitResult hit, CallbackInfo ci) {
		Hitsound.onArrowHit((AbstractArrow) (Object) this, hit.getEntity());
	}
}
