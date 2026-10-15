package dev.packconverter.mc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.packconverter.PackConverterClient;
import dev.packconverter.mixin.SpriteContentsAccessor;
import dev.packconverter.mixin.TextureAtlasSpriteAccessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Atlases put together in the background. 26.3 fills an atlas on the render thread when a reload is applied: for
 * every sprite a texture of its own is made and uploaded, then drawn into the atlas, mip level by mip level. With a
 * few thousand sprites that is most of the hitch at the end of a pack switch. Here the atlas images (one per mip
 * level) are put together while the reload is still preparing, and the render thread uploads each in one go
 * ({@code TextureAtlasMixin}). The result is the same, pixel for pixel: that draw copies the sprite's pixels and
 * stretches its edge pixels over the padding around it; sprite sizes, positions and padding are all multiples of
 * 2^mip level, so each atlas pixel takes exactly one sprite pixel. Animated sprites are still drawn by their
 * animation, as before. Anything unexpected (or atlases too big to hold twice) goes the usual way.
 */
public final class AtlasUpload {
	/** Atlases bigger than this (in pixels, mip level 0) are filled the usual way, to keep memory use down. */
	private static final long MAX_PIXELS = 4096L * 4096L;
	/**
	 * What the usual way costs per sprite (its own texture, uploads, a draw at every mip level), as the bytes an upload
	 * sends in the same time: about 25 µs against 0.75 ms per MB, measured on a pack switch.
	 */
	private static final long BYTES_PER_SPRITE = 24 * 1024;
	private static final AtomicInteger UPLOADS = new AtomicInteger();

	private AtlasUpload() {
	}

	/**
	 * Done when the sprites' mip levels are and the atlas images are put together. Passed on as the preparations'
	 * {@code readyForUpload}, so the reload waits for it while preparing.
	 */
	public static final class Composed extends CompletableFuture<Void> {
		private NativeImage @Nullable [] levels;

		/** The images, once (null if they weren't made); whoever takes them closes them. */
		public synchronized NativeImage @Nullable [] take() {
			NativeImage[] taken = this.levels;
			this.levels = null;
			return taken;
		}

		private synchronized void set(NativeImage @Nullable [] levels) {
			this.levels = levels;
		}
	}

	public static CompletableFuture<Void> after(CompletableFuture<Void> mipLevels, int width, int height, int mipLevel,
		Collection<TextureAtlasSprite> sprites, Executor executor) {
		if (!FastReload.enabled() || (long) width * height > MAX_PIXELS || Boolean.getBoolean("packconverter.noCompose")) return mipLevels;
		Composed composed = new Composed();
		mipLevels.thenRunAsync(() -> composed.set(compose(width, height, mipLevel, sprites)), executor).whenComplete((v, t) -> {
			if (t != null) composed.completeExceptionally(t);
			else composed.complete(null);
		});
		return composed;
	}

	/** The atlas at each mip level, as far as its sprites reach, or null to leave it to the game. */
	static NativeImage @Nullable [] compose(int width, int height, int mipLevel, Collection<TextureAtlasSprite> sprites) {
		// how far the sprites reach (the stitcher fills from the top left), and how much of that they cover
		long count = 0, covered = 0;
		int right = 0, bottom = 0;
		for (TextureAtlasSprite sprite : sprites) {
			if (sprite.contents().isAnimated()) continue;
			int padding = ((TextureAtlasSpriteAccessor) sprite).packconverter$padding();
			int w = sprite.contents().width() + 2 * padding, h = sprite.contents().height() + 2 * padding;
			count++;
			covered += (long) w * h;
			right = Math.max(right, sprite.getX() + w);
			bottom = Math.max(bottom, sprite.getY() + h);
		}
		int unit = 1 << mipLevel;
		right = Math.min(width, (right + unit - 1) / unit * unit);
		bottom = Math.min(height, (bottom + unit - 1) / unit * unit);
		if (count == 0 || ((long) right * bottom - covered) * 4 > count * BYTES_PER_SPRITE) return null;
		NativeImage[] levels = new NativeImage[mipLevel + 1];
		try {
			for (int level = 0; level <= mipLevel; level++) levels[level] = new NativeImage(right >> level, bottom >> level, true);
			for (TextureAtlasSprite sprite : sprites) {
				if (sprite.contents().isAnimated()) continue; // drawn by its animation
				int padding = ((TextureAtlasSpriteAccessor) sprite).packconverter$padding();
				NativeImage[] mips = ((SpriteContentsAccessor) sprite.contents()).packconverter$byMipLevel();
				if (mips == null || mips.length <= mipLevel) return discard(levels);
				for (int level = 0; level <= mipLevel; level++) {
					int step = 1 << level;
					int w = sprite.contents().width(), h = sprite.contents().height();
					if (w % step != 0 || h % step != 0 || padding % step != 0 || sprite.getX() % step != 0 || sprite.getY() % step != 0) return discard(levels);
					if (!copy(mips[level], w >> level, h >> level, levels[level], sprite.getX() >> level, sprite.getY() >> level, padding >> level)) {
						return discard(levels);
					}
				}
			}
			return levels;
		} catch (RuntimeException e) {
			PackConverterClient.LOGGER.warn("Could not put an atlas together in the background, filling it the usual way", e);
			return discard(levels);
		}
	}

	/** One sprite at one mip level: its pixels, with its edge pixels repeated over the padding around it. */
	private static boolean copy(NativeImage source, int w, int h, NativeImage target, int x, int y, int pad) {
		if (source.format() != NativeImage.Format.RGBA || target.format() != NativeImage.Format.RGBA) return false;
		if (source.getWidth() != w || source.getHeight() != h) return false;
		if (x < 0 || y < 0 || x + w + 2 * pad > target.getWidth() || y + h + 2 * pad > target.getHeight()) return false;
		long from = source.getPointer(), to = target.getPointer();
		int targetWidth = target.getWidth();
		for (int row = 0; row < h + 2 * pad; row++) {
			long sourceRow = from + (long) Math.clamp(row - pad, 0, h - 1) * w * 4;
			long targetRow = to + ((long) (y + row) * targetWidth + x) * 4;
			int first = MemoryUtil.memGetInt(sourceRow), last = MemoryUtil.memGetInt(sourceRow + (w - 1) * 4L);
			for (int i = 0; i < pad; i++) MemoryUtil.memPutInt(targetRow + i * 4L, first);
			MemoryUtil.memCopy(sourceRow, targetRow + pad * 4L, w * 4L);
			for (int i = 0; i < pad; i++) MemoryUtil.memPutInt(targetRow + (pad + w + i) * 4L, last);
		}
		return true;
	}

	/** Atlases filled from images put together in the background, since the game started (for the game test). */
	public static int uploads() {
		return UPLOADS.get();
	}

	public static void uploaded() {
		UPLOADS.incrementAndGet();
	}

	private static NativeImage @Nullable [] discard(NativeImage[] levels) {
		for (NativeImage image : levels) if (image != null) image.close();
		return null;
	}
}
