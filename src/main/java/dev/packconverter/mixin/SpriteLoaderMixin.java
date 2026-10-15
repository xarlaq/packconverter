package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.packconverter.mc.AtlasUpload;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Once a stitched atlas's sprites have their mip levels, the atlas images are put together too ({@link AtlasUpload}). */
@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@WrapOperation(method = "stitch", at = @At(value = "NEW", target = "net/minecraft/client/renderer/texture/SpriteLoader$Preparations"))
	private SpriteLoader.Preparations packconverter$composeAtlas(int width, int height, int mipLevel, TextureAtlasSprite missing,
		Map<Identifier, TextureAtlasSprite> regions, CompletableFuture<Void> readyForUpload, Operation<SpriteLoader.Preparations> original,
		@Local(argsOnly = true) Executor executor) {
		return original.call(width, height, mipLevel, missing, regions, AtlasUpload.after(readyForUpload, width, height, mipLevel, regions.values(), executor));
	}
}
