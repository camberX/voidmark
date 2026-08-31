package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.NickHider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {
	@ModifyVariable(
		method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private String voidmark$nickString(String text) {
		return NickHider.rewrite(text);
	}

	@ModifyVariable(
		method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private Component voidmark$nickComponent(Component text) {
		return NickHider.rewrite(text);
	}
}
