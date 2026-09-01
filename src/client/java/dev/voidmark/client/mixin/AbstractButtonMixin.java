package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.MenuChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void voidmark$button(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		MenuChrome.button(graphics, (AbstractWidget) (Object) this);
		ci.cancel();
	}
}
