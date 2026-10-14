package dev.packconverter.core.io;

import java.awt.image.BufferedImage;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Lean PNG decoder for the formats packs use (non-interlaced; greyscale, RGB, palette, grey+alpha, RGBA; 8-bit,
 * and 1/2/4-bit palettes and greyscale). Several times faster than ImageIO, which stays the fallback for anything
 * else (16-bit, interlaced, broken files): {@link #decode} returns null for those.
 */
final class PngDecoder {
	private PngDecoder() {
	}

	static BufferedImage decode(byte[] d) {
		if (d.length < 33 || (d[0] & 0xFF) != 0x89 || d[1] != 'P' || d[2] != 'N' || d[3] != 'G') return null;
		int pos = 8;
		int width = 0, height = 0, depth = 0, colorType = -1;
		int[] palette = null;
		byte[] trns = null;
		byte[] idat = new byte[Math.max(16, d.length)];
		int idatLen = 0;
		while (pos + 8 <= d.length) {
			int len = int32(d, pos);
			if (len < 0 || pos + 12 + len > d.length) return null;
			int type = int32(d, pos + 4);
			int body = pos + 8;
			switch (type) {
				case 0x49484452 -> { // IHDR
					width = int32(d, body);
					height = int32(d, body + 4);
					depth = d[body + 8] & 0xFF;
					colorType = d[body + 9] & 0xFF;
					if (d[body + 12] != 0) return null; // interlaced
				}
				case 0x504C5445 -> { // PLTE
					palette = new int[len / 3];
					for (int i = 0; i < palette.length; i++) {
						palette[i] = 0xFF000000 | (d[body + i * 3] & 0xFF) << 16 | (d[body + i * 3 + 1] & 0xFF) << 8 | (d[body + i * 3 + 2] & 0xFF);
					}
				}
				case 0x74524E53 -> { // tRNS
					trns = new byte[len];
					System.arraycopy(d, body, trns, 0, len);
				}
				case 0x49444154 -> { // IDAT
					System.arraycopy(d, body, idat, idatLen, len);
					idatLen += len;
				}
				case 0x49454E44 -> pos = d.length; // IEND
				default -> {
				}
			}
			pos = Math.min(d.length, pos + 12 + len);
		}
		if (width <= 0 || height <= 0 || (long) width * height > 1 << 28) return null;
		int channels = switch (colorType) {
			case 0, 3 -> 1;
			case 2 -> 3;
			case 4 -> 2;
			case 6 -> 4;
			default -> -1;
		};
		if (channels < 0) return null;
		boolean subByte = depth < 8;
		if (depth == 16 || (subByte && channels != 1) || (depth != 8 && !subByte)) return null;
		if (colorType == 3 && palette == null) return null;
		int bitsPerPixel = channels * depth;
		int stride = (width * bitsPerPixel + 7) / 8;
		int bpp = Math.max(1, bitsPerPixel / 8);
		byte[] raw = new byte[(stride + 1) * height];
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(idat, 0, idatLen);
			int n = 0;
			while (n < raw.length && !inflater.finished()) {
				int got = inflater.inflate(raw, n, raw.length - n);
				if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
				n += got;
			}
			if (n < raw.length) return null;
		} catch (DataFormatException e) {
			return null;
		} finally {
			inflater.end();
		}
		// undo the per-row filters in place
		for (int y = 0; y < height; y++) {
			int row = y * (stride + 1), filter = raw[row] & 0xFF, cur = row + 1, prev = row - stride;
			boolean first = y == 0;
			switch (filter) {
				case 0 -> {
				}
				case 1 -> {
					for (int i = bpp; i < stride; i++) raw[cur + i] += raw[cur + i - bpp];
				}
				case 2 -> {
					if (!first) for (int i = 0; i < stride; i++) raw[cur + i] += raw[prev + i];
				}
				case 3 -> {
					for (int i = 0; i < stride; i++) {
						int left = i >= bpp ? raw[cur + i - bpp] & 0xFF : 0, up = first ? 0 : raw[prev + i] & 0xFF;
						raw[cur + i] += (byte) ((left + up) >>> 1);
					}
				}
				case 4 -> {
					for (int i = 0; i < stride; i++) {
						int a = i >= bpp ? raw[cur + i - bpp] & 0xFF : 0, b = first ? 0 : raw[prev + i] & 0xFF;
						int c = i >= bpp && !first ? raw[prev + i - bpp] & 0xFF : 0;
						int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
						raw[cur + i] += (byte) (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
					}
				}
				default -> {
					return null;
				}
			}
		}
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] out = Img.pixels(img);
		int trnsGray = -1, trnsRgb = -1;
		if (trns != null && colorType == 0 && trns.length >= 2) trnsGray = ((trns[0] & 0xFF) << 8 | (trns[1] & 0xFF)) & ((1 << depth) - 1);
		if (trns != null && colorType == 2 && trns.length >= 6) trnsRgb = (trns[1] & 0xFF) << 16 | (trns[3] & 0xFF) << 8 | (trns[5] & 0xFF);
		if (colorType == 3 && trns != null) {
			palette = palette.clone();
			for (int i = 0; i < trns.length && i < palette.length; i++) palette[i] = (palette[i] & 0xFFFFFF) | (trns[i] & 0xFF) << 24;
		}
		for (int y = 0; y < height; y++) {
			int src = y * (stride + 1) + 1, o = y * width;
			switch (colorType) {
				case 6 -> {
					for (int x = 0; x < width; x++, src += 4) {
						out[o + x] = (raw[src + 3] & 0xFF) << 24 | (raw[src] & 0xFF) << 16 | (raw[src + 1] & 0xFF) << 8 | (raw[src + 2] & 0xFF);
					}
				}
				case 2 -> {
					for (int x = 0; x < width; x++, src += 3) {
						int rgb = (raw[src] & 0xFF) << 16 | (raw[src + 1] & 0xFF) << 8 | (raw[src + 2] & 0xFF);
						out[o + x] = rgb == trnsRgb ? rgb : 0xFF000000 | rgb;
					}
				}
				case 4 -> {
					for (int x = 0; x < width; x++, src += 2) {
						int g = raw[src] & 0xFF;
						out[o + x] = (raw[src + 1] & 0xFF) << 24 | g << 16 | g << 8 | g;
					}
				}
				default -> { // 0 (grey) and 3 (palette), any bit depth up to 8
					int mask = (1 << depth) - 1, perByte = 8 / depth;
					for (int x = 0; x < width; x++) {
						int v = subByte ? ((raw[src + x / perByte] & 0xFF) >> (8 - depth * (x % perByte + 1))) & mask : raw[src + x] & 0xFF;
						if (colorType == 3) {
							out[o + x] = v < palette.length ? palette[v] : 0;
						} else {
							int g = subByte ? v * 255 / mask : v;
							out[o + x] = v == trnsGray ? 0 : 0xFF000000 | g << 16 | g << 8 | g;
						}
					}
				}
			}
		}
		return img;
	}

	private static int int32(byte[] d, int p) {
		return (d[p] & 0xFF) << 24 | (d[p + 1] & 0xFF) << 16 | (d[p + 2] & 0xFF) << 8 | (d[p + 3] & 0xFF);
	}
}
