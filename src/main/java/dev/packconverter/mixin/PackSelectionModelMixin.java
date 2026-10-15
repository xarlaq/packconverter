package dev.packconverter.mixin;

import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.folders.ResourcePackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A pack moved to Selected in the resource pack screen starts converting in the background ({@link LegacyPacks#prepare}),
 * so "Done" only has to read it from the cache instead of converting it then, on the render thread.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$UnselectedPackEntry")
public abstract class PackSelectionModelMixin {
	@Inject(method = "select", at = @At("TAIL"))
	private void packconverter$convertAhead(CallbackInfo ci) {
		if (Minecraft.getInstance().gui.screen() instanceof ResourcePackScreen screen && screen.packconverter$isResourcePacks()) {
			LegacyPacks.prepare(((PackSelectionModel.Entry) this).getId());
		}
	}
}
