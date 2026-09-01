package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.MenuChrome;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	@Shadow
	protected double value;

	@Shadow
	protected abstract void extractScrollingStringOverContents(ActiveTextCollector collector, Component text, int margin);

	@Shadow
	protected abstract void handleCursor(GuiGraphicsExtractor graphics);

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
	private void voidmark$slider(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		AbstractSliderButton self = (AbstractSliderButton) (Object) this;
		MenuChrome.slider(graphics, self, value);
		extractScrollingStringOverContents(
			graphics.textRendererForWidget(self, GuiGraphicsExtractor.HoveredTextEffects.NONE),
			self.getMessage(),
			2
		);
		handleCursor(graphics);
		ci.cancel();
	}
}
