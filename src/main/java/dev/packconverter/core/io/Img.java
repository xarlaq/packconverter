package dev.packconverter.core.io;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Image helpers. All images handled by the converter are TYPE_INT_ARGB (non-premultiplied). */
public final class Img {
	static {
		ImageIO.setUseCache(false); // no temp files: decode straight from memory
	}

	private Img() {
	}

	/** Decodes any PNG ImageIO understands into ARGB, or null if it can't be read. */
	public static BufferedImage decode(byte[] data) {
		if (data == null) return null;
		try {
			BufferedImage fast = PngDecoder.decode(data);
			if (fast != null) return fast;
		} catch (RuntimeException ignored) {
			// unusual file: ImageIO below decides
		}
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			return img == null ? null : toArgb(img);
		} catch (Exception | Error e) { // broken chunks, unsupported ICC profiles...
			return null;
		}
	}

	/** Width and height from a PNG header without decoding it, or null if it isn't a PNG. */
	public static int[] pngSize(byte[] data) {
		if (data == null || data.length < 24 || data[0] != (byte) 0x89 || data[1] != 'P' || data[12] != 'I' || data[13] != 'H') return null;
		int w = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
		int h = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
		return new int[]{w, h};
	}

	public static BufferedImage toArgb(BufferedImage img) {
		if (img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
		int w = img.getWidth(), h = img.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		System.arraycopy(px, 0, pixels(out), 0, px.length);
		return out;
	}

	public static int[] pixels(BufferedImage argb) {
		return ((DataBufferInt) argb.getRaster().getDataBuffer()).getData();
	}

	public static BufferedImage create(int w, int h) {
		return new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
	}

	/** Copy of a region (clamped to the image); pixels outside the image are transparent. */
	public static BufferedImage crop(BufferedImage src, int x, int y, int w, int h) {
		BufferedImage out = create(w, h);
		blit(src, x, y, w, h, out, 0, 0);
		return out;
	}

	/** Copies a w*h region from src (sx,sy) to dst (dx,dy), replacing pixels; out-of-bounds parts are skipped. */
	public static void blit(BufferedImage src, int sx, int sy, int w, int h, BufferedImage dst, int dx, int dy) {
		int[] s = pixels(toArgb(src)), d = pixels(dst);
		int sw = src.getWidth(), sh = src.getHeight(), dw = dst.getWidth(), dh = dst.getHeight();
		for (int row = 0; row < h; row++) {
			int syy = sy + row, dyy = dy + row;
			if (syy < 0 || syy >= sh || dyy < 0 || dyy >= dh) continue;
			for (int col = 0; col < w; col++) {
				int sxx = sx + col, dxx = dx + col;
				if (sxx < 0 || sxx >= sw || dxx < 0 || dxx >= dw) continue;
				d[dyy * dw + dxx] = s[syy * sw + sxx];
			}
		}
	}

	/** True if every pixel of the region (clamped) has alpha 0. Regions entirely outside the image count as empty. */
	public static boolean isEmpty(BufferedImage img, int x, int y, int w, int h) {
		int[] p = pixels(toArgb(img));
		int iw = img.getWidth(), ih = img.getHeight();
		int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(iw, x + w), y1 = Math.min(ih, y + h);
		for (int yy = y0; yy < y1; yy++) {
			for (int xx = x0; xx < x1; xx++) {
				if ((p[yy * iw + xx] >>> 24) != 0) return false;
			}
		}
		return true;
	}

	public static boolean isEmpty(BufferedImage img) {
		return isEmpty(img, 0, 0, img.getWidth(), img.getHeight());
	}

	/** Nearest-neighbour resize. */
	public static BufferedImage resize(BufferedImage src, int w, int h) {
		if (src.getWidth() == w && src.getHeight() == h) return src;
		BufferedImage out = create(w, h);
		int[] s = pixels(toArgb(src)), d = pixels(out);
		int sw = src.getWidth(), sh = src.getHeight();
		for (int y = 0; y < h; y++) {
			int sy = (int) ((long) y * sh / h);
			for (int x = 0; x < w; x++) {
				d[y * w + x] = s[sy * sw + (int) ((long) x * sw / w)];
			}
		}
		return out;
	}

	public static BufferedImage copy(BufferedImage src) {
		BufferedImage out = create(src.getWidth(), src.getHeight());
		System.arraycopy(pixels(toArgb(src)), 0, pixels(out), 0, src.getWidth() * src.getHeight());
		return out;
	}

	public static BufferedImage mirrorVertically(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage out = create(w, h);
		int[] s = pixels(toArgb(src)), d = pixels(out);
		for (int y = 0; y < h; y++) System.arraycopy(s, (h - 1 - y) * w, d, y * w, w);
		return out;
	}

	public static BufferedImage mirrorHorizontally(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage out = create(w, h);
		int[] s = pixels(toArgb(src)), d = pixels(out);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) d[y * w + x] = s[y * w + (w - 1 - x)];
		return out;
	}

	/** Clockwise quarter turns. */
	public static BufferedImage rotate(BufferedImage src, int quarterTurns) {
		int q = ((quarterTurns % 4) + 4) % 4;
		if (q == 0) return copy(src);
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage out = q == 2 ? create(w, h) : create(h, w);
		int ow = out.getWidth();
		int[] s = pixels(toArgb(src)), d = pixels(out);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int v = s[y * w + x];
				switch (q) {
					case 1 -> d[x * ow + (h - 1 - y)] = v;
					case 2 -> d[(h - 1 - y) * ow + (w - 1 - x)] = v;
					default -> d[(w - 1 - x) * ow + y] = v;
				}
			}
		}
		return out;
	}

	/** Fast PNG encoder (RGBA8, filter 0, fastest deflate): generated files are written often, read rarely. */
	public static byte[] encodePng(BufferedImage img) {
		BufferedImage argb = toArgb(img);
		int w = argb.getWidth(), h = argb.getHeight();
		int[] px = pixels(argb);
		try {
			ByteArrayOutputStream raw = new ByteArrayOutputStream(w * h + h + 64);
			Deflater deflater = new Deflater(Deflater.BEST_SPEED);
			try (DeflaterOutputStream z = new DeflaterOutputStream(raw, deflater, 1 << 16)) {
				byte[] row = new byte[1 + w * 4];
				for (int y = 0; y < h; y++) {
					row[0] = 0;
					for (int x = 0, i = 1; x < w; x++) {
						int v = px[y * w + x];
						row[i++] = (byte) (v >>> 16);
						row[i++] = (byte) (v >>> 8);
						row[i++] = (byte) v;
						row[i++] = (byte) (v >>> 24);
					}
					z.write(row);
				}
			} finally {
				deflater.end();
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream(raw.size() + 64);
			DataOutputStream data = new DataOutputStream(out);
			data.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
			ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
			DataOutputStream ih = new DataOutputStream(ihdr);
			ih.writeInt(w);
			ih.writeInt(h);
			ih.write(new byte[]{8, 6, 0, 0, 0});
			chunk(data, "IHDR", ihdr.toByteArray());
			chunk(data, "IDAT", raw.toByteArray());
			chunk(data, "IEND", new byte[0]);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void chunk(DataOutputStream out, String type, byte[] body) throws IOException {
		byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		out.writeInt(body.length);
		out.write(t);
		out.write(body);
		CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(body);
		out.writeInt((int) crc.getValue());
	}
}
