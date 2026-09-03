package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.MenuChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
	@Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
	private void voidmark$separators(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		MenuChrome.listSeparators(graphics, self.getX(), self.getY(), self.getWidth(), self.getBottom());
		ci.cancel();
	}

	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
	private void voidmark$noListDirt(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (MenuChrome.enabled()) {
			ci.cancel();
		}
	}
}
