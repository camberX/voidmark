package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.MenuChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractScrollArea.class)
public abstract class AbstractScrollAreaMixin {
	@Shadow
	protected abstract boolean scrollable();

	@Shadow
	public abstract int scrollbarWidth();

	@Shadow
	protected abstract int scrollerHeight();

	@Shadow
	protected abstract int scrollBarX();

	@Shadow
	public abstract int scrollBarY();

	@Inject(method = "extractScrollbar", at = @At("HEAD"), cancellable = true)
	private void voidmark$scrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		MenuChrome.scrollbar(
			graphics,
			scrollBarX(),
			self.getY(),
			scrollbarWidth(),
			self.getHeight(),
			scrollBarY(),
			scrollerHeight(),
			scrollable()
		);
		ci.cancel();
	}
}
