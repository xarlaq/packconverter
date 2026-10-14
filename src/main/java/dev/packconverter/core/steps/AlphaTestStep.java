package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Until 1.16 items and the GUI were drawn with an alpha test: pixels at 10% opacity or less were never shown. Many
 * packs carry such a haze, often over a whole texture, from their image editors. 26.3 shows it in the GUI (the
 * crosshair inverts what is behind it whatever the opacity, so the haze becomes a full-strength blotch), and builds
 * an item's 3D edges only around fully clear pixels (a hazy sword is a flat card). The haze is cleared, which is
 * what the old game showed.
 * <p>
 * 1.7 also drew held items without blending: every pixel the test let through was solid. Packs of that era
 * (format 1) soften edges with half-clear dark pixels that showed as a crisp outline; blended, they are a pale halo
 * with striped 3D edges. Their item textures get those pixels solid. (1.8, same format, blended; these packs were
 * made for 1.7. Inventory icons use the same texture, so their edges are solid too.)
 */
public final class AlphaTestStep implements Step {
	private static final Pattern TARGETS = Pattern.compile("assets/[^/]+/(textures/(item|gui|mob_effect)/(?!title/background/).+|optifine/cit/.+)\\.png");
	private static final Pattern HELD = Pattern.compile("assets/[^/]+/(textures/item/.+|optifine/cit/.+)\\.png");
	/** Old alpha test: drawn if alpha > 0.1. */
	private static final int MAX_HIDDEN = 25;

	@Override
	public int format() {
		return 7;
	}

	@Override
	public String name() {
		return "1.17 alpha test";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		List<String> paths = tree.paths(p -> TARGETS.matcher(p).matches());
		boolean solidItems = ctx.before(2);
		tree.prefetchImages(paths);
		Map<String, BufferedImage> changed = new java.util.concurrent.ConcurrentHashMap<>();
		paths.parallelStream().forEach(path -> {
			BufferedImage img = tree.image(path);
			if (img == null) return;
			BufferedImage out = alphaTest(img, solidItems && HELD.matcher(path).matches());
			if (out != null) changed.put(path, out);
		});
		changed.forEach(tree::putImage);
		ctx.count("images alpha-tested like the old game", changed.size());
	}

	/** A copy with hidden pixels made clear (and, if solid, shown ones opaque), or null if nothing changes. */
	static BufferedImage alphaTest(BufferedImage img, boolean solid) {
		int[] px = Img.pixels(Img.toArgb(img));
		int first = -1;
		for (int i = 0; i < px.length; i++) {
			int a = px[i] >>> 24;
			if (a > 0 && (a <= MAX_HIDDEN || solid && a < 255)) {
				first = i;
				break;
			}
		}
		if (first < 0) return null;
		BufferedImage out = Img.copy(img);
		int[] o = Img.pixels(out);
		for (int i = first; i < o.length; i++) {
			int a = o[i] >>> 24;
			if (a == 0 || a == 255) continue;
			if (a <= MAX_HIDDEN) o[i] &= 0x00FFFFFF;
			else if (solid) o[i] |= 0xFF000000;
		}
		return out;
	}
}
