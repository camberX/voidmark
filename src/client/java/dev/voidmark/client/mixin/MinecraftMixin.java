package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.VoidmarkTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen voidmark$titleScreen(Screen screen) {
		if (screen instanceof TitleScreen) {
			return new VoidmarkTitleScreen();
		}
		return screen;
	}
}
