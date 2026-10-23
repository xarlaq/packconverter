package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.io.Json;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Netherite (1.16) for packs that have diamond items but no netherite ones (a setting, on by default):
 * the pack's own diamond tools, armor items and worn armor recoloured to netherite, or to a colour of the player's
 * choosing. Only the diamond-coloured pixels change (handles and outlines in other colours stay), and each keeps its
 * shading relative to the rest, so the pack's style carries over. A pack's own diamond item model (e.g. a lowered
 * sword) is copied for the netherite one when it uses the diamond texture.
 */
public final class NetheriteStep implements Step {
	private static final String ITEM = "assets/minecraft/textures/item/";
	private static final String EQUIPMENT = "assets/minecraft/textures/entity/equipment/";
	private static final String MODEL = "assets/minecraft/models/item/";
	/** Items with a diamond and a netherite version. */
	public static final List<String> ITEMS = List.of("sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots");
	/** Worn armor textures. */
	public static final List<String> ARMOR = List.of("humanoid", "humanoid_leggings");

	/** Pixels at least this saturated count as coloured. */
	private static final float MIN_SATURATION = 0.1F;
	/** How far (degrees) from the item's main hue a pixel may be and still be recoloured. */
	private static final float HUE_RANGE = 40;
	/** Netherite is duller than diamond: lightness differences are compressed by this power. */
	private static final double LIGHTNESS_CONTRAST = 0.7;
	private static final float MAX_LIGHTNESS = 0.92F;
	/** Image hues further than this from the pack's main hue count a quarter (handles, gems in another colour). */
	private static final float PACK_HUE_RANGE = 60;
	/** Grey pixels at least this light, next to the main colour, are its shine. */
	private static final float SHINE_LIGHTNESS = 0.6F;
	/** For grey art (iron): pixels this unsaturated count as the metal (packs tint their iron a little). */
	private static final float GREY_SATURATION = 0.18F;

	@Override
	public String name() {
		return "netherite from diamond";
	}

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		if (!ctx.options().netherite()) return;
		int color = ctx.options().netheriteColor();
		List<BufferedImage> diamond = new ArrayList<>();
		for (String item : ITEMS) addImage(tree, ITEM + "diamond_" + item + ".png", diamond);
		for (String layer : ARMOR) addImage(tree, EQUIPMENT + layer + "/diamond.png", diamond);
		float hue = mainHue(diamond);
		int made = 0;
		for (String item : ITEMS) {
			if (make(tree, ITEM + "diamond_" + item + ".png", ITEM + "netherite_" + item + ".png", img -> recolor(img, color, hue))) {
				made++;
				copyModel(tree, "diamond_" + item, "netherite_" + item);
			}
		}
		for (String layer : ARMOR) {
			if (make(tree, EQUIPMENT + layer + "/diamond.png", EQUIPMENT + layer + "/netherite.png", img -> recolor(img, color, hue))) made++;
		}
		ctx.count("netherite textures made from diamond", made);
	}

	private static void addImage(PackTree tree, String path, List<BufferedImage> out) {
		if (!tree.has(path)) return;
		BufferedImage img = tree.image(path);
		if (img != null) out.add(img);
	}

	/** {@code to} made from the pack's {@code from} (its animation too), unless the pack has its own. */
	static boolean make(PackTree tree, String from, String to, UnaryOperator<BufferedImage> recolor) {
		if (!tree.has(from) || tree.has(to)) return false;
		BufferedImage img = tree.image(from);
		if (img == null) return false;
		tree.putImage(to, recolor.apply(img));
		if (tree.has(from + ".mcmeta")) tree.copy(from + ".mcmeta", to + ".mcmeta");
		return true;
	}

	/**
	 * The pack's own model of item {@code fromItem} (e.g. diamond_sword), if it draws that item's texture, for item
	 * {@code toItem} too, with its texture (same hold, same shape).
	 */
	static void copyModel(PackTree tree, String fromItem, String toItem) {
		String from = MODEL + fromItem + ".json", to = MODEL + toItem + ".json";
		if (!tree.has(from) || tree.has(to)) return;
		JsonObject model = Json.obj(tree.json(from));
		JsonObject textures = model == null ? null : Json.obj(model, "textures");
		if (textures == null) return;
		JsonObject copy = model.deepCopy();
		JsonObject copyTextures = Json.obj(copy, "textures");
		boolean uses = false;
		for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
			if (!e.getValue().isJsonPrimitive()) continue;
			String texture = e.getValue().getAsString();
			String path = texture.startsWith("minecraft:") ? texture.substring("minecraft:".length()) : texture;
			if (path.equals("item/" + fromItem)) {
				copyTextures.addProperty(e.getKey(), "minecraft:item/" + toItem);
				uses = true;
			}
		}
		if (uses) tree.putJson(to, copy);
	}

	/**
	 * The main hue (degrees) of a pack's diamond art, from all its diamond images together (the armor is all diamond
	 * colour, so a hoe whose handle has more pixels than its head still gets the right one), or -1 for grey art.
	 */
	public static float mainHue(Collection<BufferedImage> images) {
		float[] bins = new float[36];
		for (BufferedImage img : images) histogram(img, bins);
		return peak(bins);
	}

	/** {@link #recolor(BufferedImage, int, float)} with the image's own main hue. */
	public static BufferedImage recolor(BufferedImage src, int rgb) {
		return recolor(src, rgb, -1);
	}

	/**
	 * The image with its main colour turned into {@code rgb}: hue and saturation from the colour, lightness scaled from
	 * the colour's around the image's average. The main colour is the image's most common hue among coloured pixels,
	 * favouring those near {@code packHue} (the pack's, from {@link #mainHue}; -1 for none). Near-white highlights
	 * touching it change too. Images without a main colour (grey art) have their grey pixels recoloured instead.
	 */
	public static BufferedImage recolor(BufferedImage src, int rgb, float packHue) {
		return recolor(src, rgb, packHue, false);
	}

	/**
	 * Grey art (iron) recoloured: its grey pixels take {@code rgb}'s hue and saturation, lightness scaled as in
	 * {@link #recolor(BufferedImage, int, float)}. Coloured pixels (a wooden handle) stay.
	 */
	public static BufferedImage recolorGrey(BufferedImage src, int rgb) {
		return recolor(src, rgb, -1, true);
	}

	private static BufferedImage recolor(BufferedImage src, int rgb, float packHue, boolean greyArt) {
		BufferedImage out = Img.copy(Img.toArgb(src));
		int[] px = Img.pixels(out);
		int w = out.getWidth();
		float hue = -1;
		if (!greyArt) {
			float[] bins = new float[36];
			histogram(out, bins);
			if (packHue >= 0) {
				for (int i = 0; i < 36; i++) if (hueDistance(i * 10 + 5, packHue) > PACK_HUE_RANGE) bins[i] *= 0.25F;
			}
			hue = peak(bins);
		}
		boolean grey = hue < 0;
		float greySaturation = greyArt ? GREY_SATURATION : MIN_SATURATION;
		float[] hsl = new float[3];
		boolean[] pick = new boolean[px.length];
		double sumL = 0, sumS = 0;
		int n = 0;
		for (int i = 0; i < px.length; i++) {
			if (px[i] >>> 24 == 0) continue;
			hsl(px[i], hsl);
			boolean take = grey ? hsl[1] < greySaturation : hsl[1] >= MIN_SATURATION && hueDistance(hsl[0], hue) <= HUE_RANGE;
			if (!take) continue;
			pick[i] = true;
			sumL += hsl[2];
			sumS += hsl[1];
			n++;
		}
		if (n == 0) return out;
		// shine: near-white pixels next to the colour (left white, they'd glare on dark netherite)
		boolean[] shine = new boolean[px.length];
		if (!grey) {
			for (int i = 0; i < px.length; i++) {
				if (pick[i] || px[i] >>> 24 == 0) continue;
				hsl(px[i], hsl);
				if (hsl[1] < MIN_SATURATION && hsl[2] >= SHINE_LIGHTNESS && touches(pick, i, w, out.getHeight())) shine[i] = true;
			}
		}
		float avgL = (float) Math.max(0.02, sumL / n), avgS = (float) Math.max(0.02, sumS / n);
		float[] target = new float[3];
		hsl(0xFF000000 | rgb, target);
		for (int i = 0; i < px.length; i++) {
			if (!pick[i] && !shine[i]) continue;
			hsl(px[i], hsl);
			float l = (float) Math.min(MAX_LIGHTNESS, target[2] * Math.pow(Math.max(hsl[2], 0.001) / avgL, LIGHTNESS_CONTRAST));
			float s = grey ? target[1] : (float) Math.min(1, target[1] * Math.sqrt(hsl[1] / avgS));
			px[i] = (px[i] & 0xFF000000) | rgb(target[0], s, l);
		}
		return out;
	}

	/** Saturation-weighted hues of the coloured pixels, in 10° bins. */
	private static void histogram(BufferedImage img, float[] bins) {
		float[] hsl = new float[3];
		for (int p : Img.pixels(Img.toArgb(img))) {
			if (p >>> 24 == 0) continue;
			hsl(p, hsl);
			if (hsl[1] >= MIN_SATURATION && hsl[2] > 0.03F && hsl[2] < 0.97F) bins[(int) (hsl[0] / 10) % 36] += hsl[1];
		}
	}

	/** The centre of the strongest 30° of the histogram, or -1 if it is empty. */
	private static float peak(float[] bins) {
		int peak = -1;
		float best = 0;
		for (int i = 0; i < 36; i++) {
			float around = bins[(i + 35) % 36] + bins[i] + bins[(i + 1) % 36];
			if (around > best) {
				best = around;
				peak = i;
			}
		}
		return peak < 0 ? -1 : peak * 10 + 5;
	}

	private static boolean touches(boolean[] pick, int i, int w, int h) {
		int x = i % w, y = i / w;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx, ny = y + dy;
				if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < w && ny < h && pick[ny * w + nx]) return true;
			}
		}
		return false;
	}

	private static float hueDistance(float a, float b) {
		float d = Math.abs(a - b) % 360;
		return d > 180 ? 360 - d : d;
	}

	/** Hue (degrees), saturation and lightness of an ARGB colour. */
	static void hsl(int argb, float[] out) {
		float r = ((argb >> 16) & 255) / 255F, g = ((argb >> 8) & 255) / 255F, b = (argb & 255) / 255F;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float l = (max + min) / 2, d = max - min, h = 0, s = 0;
		if (d > 0) {
			s = l > 0.5F ? d / (2 - max - min) : d / (max + min);
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h *= 60;
			if (h < 0) h += 360;
		}
		out[0] = h;
		out[1] = s;
		out[2] = l;
	}

	static int rgb(float h, float s, float l) {
		float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
		float r, g, b;
		if (h < 60) { r = c; g = x; b = 0; }
		else if (h < 120) { r = x; g = c; b = 0; }
		else if (h < 180) { r = 0; g = c; b = x; }
		else if (h < 240) { r = 0; g = x; b = c; }
		else if (h < 300) { r = x; g = 0; b = c; }
		else { r = c; g = 0; b = x; }
		return channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m);
	}

	private static int channel(float v) {
		return Math.round(Math.max(0, Math.min(1, v)) * 255);
	}
}
