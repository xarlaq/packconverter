package dev.packconverter.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.packconverter.mc.AtlasUpload;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fills the atlas from images put together in the background, one upload per mip level ({@link AtlasUpload}). */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
	@Shadow
	private int maxMipLevel;
	@Shadow
	private int width;
	@Shadow
	private int height;
	@Unique
	private NativeImage @Nullable [] packconverter$composed;

	@Shadow
	protected abstract void uploadAnimationFrames();

	@Inject(method = "upload", at = @At("HEAD"))
	private void packconverter$takeComposed(SpriteLoader.Preparations preparations, CallbackInfo ci) {
		this.packconverter$composed = preparations.readyForUpload() instanceof AtlasUpload.Composed composed ? composed.take() : null;
	}

	@Inject(method = "uploadInitialContents", at = @At("HEAD"), cancellable = true)
	private void packconverter$uploadComposed(CallbackInfo ci) {
		NativeImage[] levels = this.packconverter$composed;
		this.packconverter$composed = null;
		if (levels == null) return;
		try {
			if (levels.length != this.maxMipLevel + 1 || levels[0].getWidth() > this.width || levels[0].getHeight() > this.height) return;
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			for (int level = 0; level < levels.length; level++) {
				encoder.writeToTexture(((TextureAtlas) (Object) this).getTexture(), levels[level], level, 0, 0, 0);
			}
		} finally {
			for (NativeImage image : levels) image.close();
		}
		AtlasUpload.uploaded();
		this.uploadAnimationFrames();
		ci.cancel();
	}
}
