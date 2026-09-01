package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.MenuChrome;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.StringWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StringWidget.class)
public abstract class StringWidgetMixin {
	@Inject(method = "visitLines", at = @At("HEAD"))
	private void voidmark$titleFont(ActiveTextCollector collector, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		StringWidget self = (StringWidget) (Object) this;
		self.setMessage(MenuChrome.titleLabel(self.getMessage()));
	}
}
