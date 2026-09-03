package dev.voidmark.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmark.client.render.ChamsRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@Inject(
		method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void voidmark$chams(
		Model<?> model,
		Object state,
		PoseStack pose,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int color,
		TextureAtlasSprite sprite,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumbling,
		CallbackInfo ci
	) {
		if (!ChamsRenderer.shouldRewrite(renderType)) {
			return;
		}
		ci.cancel();
		ChamsRenderer.submit(
			(SubmitNodeCollection) (Object) this,
			model,
			state,
			pose,
			renderType,
			lightCoords,
			overlayCoords,
			color,
			sprite,
			outlineColor,
			crumbling
		);
	}
}
