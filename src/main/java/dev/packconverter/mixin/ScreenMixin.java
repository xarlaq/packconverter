package dev.packconverter.mixin;

import dev.packconverter.mc.folders.ResourcePackScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pack tooltips ({@code PackTooltip}) are set once the resource pack screen is drawn, just before tooltips are: for
 * the row under the mouse, however rows are drawn (Lunar Client draws them its own way).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
	private void packconverter$packTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if ((Object) this instanceof ResourcePackScreen packs) packs.packconverter$packTooltip(graphics, mouseX, mouseY);
	}
}
