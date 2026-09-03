package dev.voidmark.client.mixin;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface RenderSetupTextureBindingAccessor {
	@Invoker("location")
	Identifier voidmark$location();
}
