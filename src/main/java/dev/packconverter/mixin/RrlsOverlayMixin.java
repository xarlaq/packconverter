package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.packconverter.PackConverterConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * With RRLS (Remove Reloading Screen) installed, reloads are its business ({@code LoadingOverlayMixin} stands aside)
 * and it draws its own small bar (or text) while they run, from a method its mixin adds to the loading screen.
 * "Hide loading bar" hides that too: only the drawing calls in that method are skipped, as the method also works out
 * when the reload is over (the overlay goes only after that; skipping the whole method left it up for good, and every
 * later pack switch waiting behind it). Applied after RRLS's mixin (higher priority), so the method is there to
 * target; without RRLS there is nothing to wrap, and nothing happens.
 */
@Mixin(value = LoadingOverlay.class, priority = 1100)
public abstract class RrlsOverlayMixin {
	private static boolean packconverter$hidden() {
		return PackConverterConfig.get().hideLoadingBar;
	}

	@WrapOperation(method = "rrls$miniRender", require = 0, expect = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"))
	private void packconverter$rrlsBar(LoadingOverlay overlay, GuiGraphics graphics, int x0, int y0, int x1, int y1, float alpha,
		Operation<Void> original) {
		if (!packconverter$hidden()) original.call(overlay, graphics, x0, y0, x1, y1, alpha);
	}

	@WrapOperation(method = "rrls$miniRender", require = 0, expect = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void packconverter$rrlsText(GuiGraphics graphics, Font font, String text, int x, int y, int color, Operation<Void> original) {
		if (!packconverter$hidden()) original.call(graphics, font, text, x, y, color);
	}

	@WrapOperation(method = "rrls$miniRender", require = 0, expect = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/FocusableTextWidget;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void packconverter$rrlsTextBox(FocusableTextWidget widget, GuiGraphics graphics, int mouseX, int mouseY, float a,
		Operation<Void> original) {
		if (!packconverter$hidden()) original.call(widget, graphics, mouseX, mouseY, a);
	}
}
