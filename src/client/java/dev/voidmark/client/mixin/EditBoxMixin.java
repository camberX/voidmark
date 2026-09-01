package dev.voidmark.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.voidmark.client.ui.MenuChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class EditBoxMixin {
	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
	private void voidmark$field(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		EditBox self = (EditBox) (Object) this;
		if (!MenuChrome.enabled() || !self.isVisible() || !self.isBordered()) {
			return;
		}
		MenuChrome.field(graphics, self);
	}

	@WrapOperation(
		method = "extractWidgetRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
		)
	)
	private void voidmark$skipVanillaBorder(
		GuiGraphicsExtractor graphics,
		RenderPipeline pipeline,
		Identifier sprite,
		int x,
		int y,
		int w,
		int h,
		Operation<Void> original
	) {
		if (!MenuChrome.enabled()) {
			original.call(graphics, pipeline, sprite, x, y, w, h);
		}
	}
}
