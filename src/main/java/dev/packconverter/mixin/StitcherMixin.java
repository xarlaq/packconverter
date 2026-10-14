package dev.packconverter.mixin;

import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Fixes sprites silently missing from atlases with HD packs (a 4x pack's 728 px hotbar showed as the missing
 * texture). When nothing has room, the stitcher grows the atlas right or down; growing down adds a strip as wide as
 * the atlas so far, and a sprite wider than that is dropped without a word. Such sprites grow it right instead,
 * which vanilla also does when the atlas is taller than wide. Every other case is left to vanilla, unchanged.
 */
@Mixin(Stitcher.class)
public abstract class StitcherMixin<T extends Stitcher.Entry> {
	@Shadow
	private int storageX;
	@Shadow
	private int storageY;
	@Shadow
	@Final
	private int maxWidth;
	@Shadow
	@Final
	private List<Stitcher.Region<T>> storage;

	@Inject(method = "expand", at = @At("HEAD"), cancellable = true)
	private void packconverter$growRightForWideSprites(Stitcher.Holder<T> holder, CallbackInfoReturnable<Boolean> cir) {
		if (this.storageY == 0 || holder.width() <= this.storageX) return;
		int xNewSize = Mth.smallestEncompassingPowerOfTwo(this.storageX + holder.width());
		if (xNewSize > this.maxWidth) return;
		// sprites come tallest first, so the strip (as tall as the atlas) always fits it
		Stitcher.Region<T> slot = new Stitcher.Region<>(this.storageX, 0, xNewSize - this.storageX, this.storageY);
		this.storageX = xNewSize;
		slot.add(holder);
		this.storage.add(slot);
		cir.setReturnValue(true);
	}
}
