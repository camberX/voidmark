package dev.voidmark.client.mixin;

import dev.voidmark.client.render.MobGlowRenderer;
import dev.voidmark.client.ui.LoadoutsScreen;
import dev.voidmark.client.ui.VoidmarkTitleScreen;
import dev.voidmark.client.ui.WardrobeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen voidmark$titleScreen(Screen screen) {
		if (screen instanceof TitleScreen) {
			return new VoidmarkTitleScreen();
		}
		return WardrobeScreen.wrap(LoadoutsScreen.wrap(screen));
	}

	/**
	 * Push ESP targets into the outline buffer (including holograms). Entities
	 * that already have vanilla GLOWING keep Minecraft's own outline.
	 */
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void voidmark$espGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (MobGlowRenderer.hasVanillaGlow(entity)) {
			return;
		}
		if (MobGlowRenderer.shouldForceGlow(entity)) {
			cir.setReturnValue(true);
		}
	}
}
