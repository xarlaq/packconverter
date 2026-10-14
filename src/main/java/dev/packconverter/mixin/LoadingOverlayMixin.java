package dev.packconverter.mixin;

import dev.packconverter.PackConverterClient;
import dev.packconverter.mc.FastReload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With fast pack switching on, reloads in game (not the one at startup) don't cover the screen: vanilla keeps
 * drawing the world under its loading screen anyway, and builds the new resources in the background while the old
 * ones stay in use, swapping them in at the end. So the screen stays as it is, with a thin progress bar, until the
 * swap; then the overlay goes at once (vanilla keeps it up at least a second and fades it over two more, during
 * which the game can't be used). Not with RRLS (Remove Reloading Screen), which does the same its own way. Also
 * logs how long the reload took.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	private ReloadInstance reload;
	@Shadow
	@Final
	private boolean fadeIn;
	@Shadow
	private long fadeOutStart;
	@Unique
	private final long packconverter$start = Util.getMillis();
	@Unique
	private float packconverter$progress;
	@Unique
	private boolean packconverter$logged;
	/** Set if drawing the screen under it failed once: then only the progress bar is drawn. */
	@Unique
	private boolean packconverter$noScreen;
	/** RRLS (Remove Reloading Screen) does this its own way and draws the overlay itself: leave it to that. */
	@Unique
	private static final boolean PACKCONVERTER$RRLS = FabricLoader.getInstance().isModLoaded("rrls");

	/** In game (it fades in), not at startup, where there is nothing to show yet. */
	@Unique
	private boolean packconverter$seamless() {
		return this.fadeIn && FastReload.enabled() && !PACKCONVERTER$RRLS;
	}

	@Inject(method = "isReadyToFadeOut", at = @At("HEAD"), cancellable = true)
	private void packconverter$noMinimum(CallbackInfoReturnable<Boolean> cir) {
		if (this.packconverter$seamless()) cir.setReturnValue(true);
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void packconverter$seamless(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!this.packconverter$seamless()) return;
		ci.cancel();
		if (this.fadeOutStart > -1L) {
			// done and swapped in (the screen was set up again for the new resources)
			this.minecraft.gui.setOverlay(null);
			return;
		}
		// the screen as it is (the world and HUD are drawn under every overlay), mouse away so nothing lights up
		if (this.minecraft.gui.screen() != null && !this.packconverter$noScreen) {
			try {
				this.minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, -1, -1, a);
			} catch (RuntimeException e) {
				this.packconverter$noScreen = true;
				PackConverterClient.LOGGER.warn("Could not draw the screen during a reload, showing only the progress", e);
			}
		}
		graphics.nextStratum();
		this.packconverter$progress = Math.max(this.packconverter$progress, this.reload.getActualProgress());
		int width = 120, x0 = graphics.guiWidth() / 2 - width / 2, y = graphics.guiHeight() - 6;
		graphics.fill(x0 - 1, y - 1, x0 + width + 1, y + 3, 0x80000000);
		graphics.fill(x0, y, x0 + Math.round(width * this.packconverter$progress), y + 2, 0xFFFFFFFF);
	}

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private void packconverter$logTime(CallbackInfo ci) {
		if (this.packconverter$logged) return;
		this.packconverter$logged = true;
		String skipped = FastReload.skipped();
		PackConverterClient.LOGGER.info("Resources reloaded in {} ms{}", Util.getMillis() - this.packconverter$start,
			skipped.isEmpty() ? "" : " (" + skipped + " unchanged, kept)");
	}
}
