package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.VoidmarkTitleScreen;
import dev.voidmark.client.visual.FakeBan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void voidmark$holdJoiningWorld(Screen screen, CallbackInfo ci) {
		if (FakeBan.holdJoiningWorld(screen)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen voidmark$titleScreen(Screen screen) {
		if (screen instanceof TitleScreen) {
			return new VoidmarkTitleScreen();
		}
		return screen;
	}
}
