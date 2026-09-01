package dev.voidmark.client.mixin;

import dev.voidmark.client.ui.MenuChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
	private void voidmark$listPanel(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
		MenuChrome.listPanel(graphics, self.getX(), self.getY(), self.getWidth(), self.getHeight());
		ci.cancel();
	}

	@Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
	private void voidmark$listRules(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
		MenuChrome.listSeparators(graphics, self.getX(), self.getY(), self.getWidth(), self.getBottom());
		ci.cancel();
	}

	@Inject(
		method = "extractSelection(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void voidmark$selection(GuiGraphicsExtractor graphics, Object entry, int color, CallbackInfo ci) {
		if (!MenuChrome.enabled() || !(entry instanceof LayoutElement row)) {
			return;
		}
		MenuChrome.selection(graphics, row.getX(), row.getY(), row.getWidth(), row.getHeight());
		ci.cancel();
	}
}
