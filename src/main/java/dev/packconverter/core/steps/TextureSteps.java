package dev.packconverter.core.steps;

import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.io.Json;

import java.awt.image.BufferedImage;

/** Texture conversions that rearrange or recolour a pack's own images (no new art is added). */
public final class TextureSteps {
	private static final String TEX = "assets/minecraft/textures/";

	private TextureSteps() {
	}

	/**
	 * 1.9 replaced the animated compass and clock strips with one texture per frame (compass_00..31,
	 * clock_00..63), picked by the item's angle/time. Old strips are cut into frames; strips with a different
	 * frame count are resampled to the new count.
	 */
	public static Step compassClock19() {
		return new SimpleStep(4, "1.9 compass and clock frames", (tree, ctx) -> {
			ctx.count("sliced sprites", frames(tree, ctx, TEX + "items/compass.png", TEX + "items/compass_", 32)
				+ frames(tree, ctx, TEX + "items/clock.png", TEX + "items/clock_", 64));
		});
	}

	private static int frames(PackTree tree, Context ctx, String strip, String prefix, int count) {
		if (!tree.has(strip) || tree.has(prefix + "00.png")) return 0;
		BufferedImage img = tree.image(strip);
		if (img == null || img.getHeight() < img.getWidth()) return 0;
		int size = img.getWidth(), have = img.getHeight() / size;
		for (int i = 0; i < count; i++) {
			int source = (int) ((long) i * have / count);
			tree.putImage(prefix + String.format("%02d", i) + ".png", Img.crop(img, 0, source * size, size, size));
		}
		return count;
	}

	/**
	 * 1.9 added the off-hand slot beside the hotbar. Vanilla's is hand-drawn; an older pack's is made from its own
	 * hotbar, the two ends joined into one slot, placed where vanilla's sits in its 29x24 sprites.
	 */
	public static Step offhandHotbar19() {
		return new SimpleStep(2, "1.9 off-hand hotbar slot", (tree, ctx) -> {
			BufferedImage widgets = tree.image(TEX + "gui/widgets.png");
			if (widgets == null || widgets.getWidth() < 256 || widgets.getWidth() % 256 != 0) return;
			int u = widgets.getWidth() / 256;
			if (Img.isEmpty(widgets, 0, 0, 182 * u, 22 * u)) return;
			int n = offhandSlot(tree, widgets, u, TEX + "gui/sprites/hud/hotbar_offhand_left.png", 0)
				+ offhandSlot(tree, widgets, u, TEX + "gui/sprites/hud/hotbar_offhand_right.png", 7);
			ctx.count("converted textures", n);
		});
	}

	private static int offhandSlot(PackTree tree, BufferedImage widgets, int u, String path, int x) {
		if (tree.has(path)) return 0;
		BufferedImage out = Img.create(29 * u, 24 * u);
		Img.blit(widgets, 0, 0, 11 * u, 22 * u, out, x * u, u);
		Img.blit(widgets, 171 * u, 0, 11 * u, 22 * u, out, (x + 11) * u, u);
		tree.putImage(path, out);
		return 1;
	}

	/**
	 * Until 1.13 animation frames were always square (as wide as the texture); the mcmeta's width/height were
	 * ignored. 1.13 started reading them, so the fields come out and the frames stay square.
	 */
	public static Step squareFrames113() {
		return new SimpleStep(4, "1.13 animation frames", (tree, ctx) -> {
			int changed = 0;
			for (String path : tree.paths(p -> p.endsWith(".png.mcmeta") && p.contains("/textures/"))) {
				JsonObject meta = Json.obj(tree.json(path));
				JsonObject animation = Json.obj(meta, "animation");
				if (animation == null || !(animation.has("width") || animation.has("height"))) continue;
				animation.remove("width");
				animation.remove("height");
				tree.putJson(path, meta);
				changed++;
			}
			ctx.count("animation frames squared", changed);
		});
	}

	/**
	 * 1.13 made water textures greyscale and tinted per biome. The old blue texture becomes the grey that,
	 * tinted with the default water colour (#3F76E4), comes closest to the original colour (least squares).
	 */
	public static Step water113() {
		return new SimpleStep(4, "1.13 tinted water", (tree, ctx) -> {
			int changed = 0;
			for (String name : new String[]{"water_still", "water_flow", "water_overlay"}) {
				String path = TEX + "block/" + name + ".png";
				BufferedImage img = tree.image(path);
				if (img == null) continue;
				BufferedImage out = Img.copy(img);
				int[] px = Img.pixels(out);
				double tr = 0x3F / 255.0, tg = 0x76 / 255.0, tb = 0xE4 / 255.0, norm = tr * tr + tg * tg + tb * tb;
				for (int i = 0; i < px.length; i++) {
					int v = px[i];
					double r = (v >> 16 & 0xFF) / 255.0, g = (v >> 8 & 0xFF) / 255.0, b = (v & 0xFF) / 255.0;
					int gray = (int) Math.round(Math.min(1, (r * tr + g * tg + b * tb) / norm) * 255);
					px[i] = (v & 0xFF000000) | gray << 16 | gray << 8 | gray;
				}
				tree.putImage(path, out);
				changed++;
			}
			ctx.count("recoloured textures", changed);
		});
	}

	/**
	 * Before 1.15 the enchantment glint texture was greyscale and drawn tinted (0.5, 0.25, 0.8); since 1.15
	 * the texture carries the colour itself. Bake the old tint in.
	 */
	public static Step glint115() {
		return new SimpleStep(5, "1.15 coloured glint", (tree, ctx) -> {
			String path = TEX + "misc/enchanted_item_glint.png";
			BufferedImage img = tree.image(path);
			if (img == null) return;
			BufferedImage out = Img.copy(img);
			int[] px = Img.pixels(out);
			for (int i = 0; i < px.length; i++) {
				int v = px[i];
				int r = (v >> 16 & 0xFF) * 128 / 255, g = (v >> 8 & 0xFF) * 64 / 255, b = (v & 0xFF) * 204 / 255;
				px[i] = (v & 0xFF000000) | r << 16 | g << 8 | b;
			}
			tree.putImage(path, out);
			ctx.count("recoloured textures", 1);
		});
	}

	private enum Flip {NONE, VERTICAL, HORIZONTAL, ROTATE_180}

	/** Copies the region (x1,y1)-(x2,y2) of a sheet in its own units to (dx,dy), flipped as asked. */
	private static void part(BufferedImage src, int unitsW, BufferedImage dst, int x1, int y1, int x2, int y2, int dx, int dy, Flip flip) {
		double s = src.getWidth() / (double) unitsW;
		int sx = (int) Math.round(x1 * s), sy = (int) Math.round(y1 * s);
		int w = (int) Math.round(x2 * s) - sx, h = (int) Math.round(y2 * s) - sy;
		BufferedImage piece = Img.crop(src, sx, sy, w, h);
		piece = switch (flip) {
			case NONE -> piece;
			case VERTICAL -> Img.mirrorVertically(piece);
			case HORIZONTAL -> Img.mirrorHorizontally(piece);
			case ROTATE_180 -> Img.rotate(piece, 2);
		};
		Img.blit(piece, 0, 0, w, h, dst, (int) Math.round(dx * s), (int) Math.round(dy * s));
	}

	/**
	 * 1.15 rebuilt the chest model: new UV layout for single chests, and double chests split into a left and
	 * a right half texture. Coordinates from agentdid127's ResourcePackConverter (MIT), checked against vanilla.
	 */
	public static Step chest115() {
		return new SimpleStep(5, "1.15 chest layout", (tree, ctx) -> {
			String dir = TEX + "entity/chest/";
			int n = 0;
			for (String name : new String[]{"normal", "trapped", "christmas"}) n += doubleChest(tree, dir, name);
			for (String name : new String[]{"normal", "trapped", "christmas", "ender"}) n += singleChest(tree, dir + name + ".png");
			ctx.count("converted textures", n);
		});
	}

	private static int singleChest(PackTree tree, String path) {
		BufferedImage src = tree.image(path);
		if (src == null || src.getWidth() != src.getHeight()) return 0;
		BufferedImage out = Img.create(src.getWidth(), src.getHeight());
		final Flip V = Flip.VERTICAL, R = Flip.ROTATE_180, N = Flip.NONE;
		// Body
		part(src, 64, out, 14, 0, 28, 14, 28, 0, V);
		part(src, 64, out, 28, 0, 42, 14, 14, 0, V);
		part(src, 64, out, 28, 19, 42, 33, 14, 19, V);
		part(src, 64, out, 14, 19, 28, 33, 28, 19, V);
		part(src, 64, out, 28, 14, 42, 19, 28, 14, R);
		part(src, 64, out, 42, 14, 56, 19, 14, 14, R);
		part(src, 64, out, 14, 14, 28, 19, 42, 14, R);
		part(src, 64, out, 0, 14, 14, 19, 0, 14, R);
		part(src, 64, out, 0, 33, 14, 43, 0, 33, R);
		part(src, 64, out, 42, 33, 56, 43, 14, 33, R);
		part(src, 64, out, 28, 33, 42, 43, 28, 33, R);
		part(src, 64, out, 14, 33, 28, 43, 42, 33, R);
		// Latch
		part(src, 64, out, 0, 1, 1, 5, 0, 1, V);
		part(src, 64, out, 3, 0, 5, 1, 1, 0, N);
		part(src, 64, out, 1, 0, 3, 1, 3, 0, N);
		part(src, 64, out, 1, 1, 3, 5, 4, 1, R);
		part(src, 64, out, 3, 1, 6, 5, 1, 1, R);
		tree.putImage(path, out);
		return 1;
	}

	private static int doubleChest(PackTree tree, String dir, String name) {
		String path = dir + name + "_double.png";
		BufferedImage src = tree.image(path);
		if (src == null || src.getWidth() != src.getHeight() * 2) return 0;
		int unit = src.getWidth() / 128;
		final Flip V = Flip.VERTICAL, R = Flip.ROTATE_180, N = Flip.NONE;
		if (!tree.has(dir + name + "_left.png")) {
			BufferedImage left = Img.create(64 * unit, 64 * unit);
			part(src, 128, left, 59, 19, 74, 33, 14, 19, V);
			part(src, 128, left, 59, 0, 74, 14, 14, 0, V);
			part(src, 128, left, 29, 19, 44, 33, 29, 19, V);
			part(src, 128, left, 29, 14, 44, 19, 43, 14, R);
			part(src, 128, left, 29, 0, 44, 14, 29, 0, V);
			part(src, 128, left, 44, 33, 58, 43, 29, 33, R);
			part(src, 128, left, 29, 33, 44, 43, 43, 33, R);
			part(src, 128, left, 58, 33, 73, 43, 14, 33, R);
			part(src, 128, left, 44, 14, 58, 19, 29, 14, R);
			part(src, 128, left, 58, 14, 73, 19, 14, 14, R);
			part(src, 128, left, 4, 0, 5, 1, 1, 0, N);
			part(src, 128, left, 2, 0, 3, 1, 2, 0, N);
			part(src, 128, left, 2, 1, 5, 5, 1, 1, R);
			tree.putImage(dir + name + "_left.png", left);
		}
		if (!tree.has(dir + name + "_right.png")) {
			BufferedImage right = Img.create(64 * unit, 64 * unit);
			part(src, 128, right, 44, 0, 59, 14, 14, 0, V);
			part(src, 128, right, 14, 0, 29, 14, 29, 0, V);
			part(src, 128, right, 14, 19, 29, 33, 29, 19, V);
			part(src, 128, right, 0, 14, 14, 19, 0, 14, R);
			part(src, 128, right, 73, 14, 88, 19, 14, 14, R);
			part(src, 128, right, 14, 14, 29, 19, 43, 14, R);
			part(src, 128, right, 44, 19, 59, 33, 14, 19, V);
			part(src, 128, right, 14, 33, 29, 43, 43, 33, R);
			part(src, 128, right, 0, 33, 14, 43, 0, 33, R);
			part(src, 128, right, 73, 33, 88, 43, 14, 33, R);
			part(src, 128, right, 3, 0, 4, 1, 1, 0, N);
			part(src, 128, right, 1, 0, 2, 1, 2, 0, N);
			part(src, 128, right, 0, 1, 1, 5, 0, 1, V);
			part(src, 128, right, 5, 1, 6, 5, 1, 1, V);
			part(src, 128, right, 1, 1, 2, 5, 3, 1, V);
			tree.putImage(dir + name + "_right.png", right);
		}
		return 2;
	}

	/**
	 * 1.19.3 narrowed creative inventory tabs from 28 to 26 pixels (one extra tab slot). Each old tab is cut
	 * in half and the halves are pushed together. From agentdid127's ResourcePackConverter (MIT).
	 */
	public static Step creativeTabs1193() {
		return new SimpleStep(12, "1.19.3 creative tabs", (tree, ctx) -> {
			String path = TEX + "gui/container/creative_inventory/tabs.png";
			BufferedImage src = tree.image(path);
			if (src == null) return;
			BufferedImage out = Img.create(src.getWidth(), src.getHeight());
			int[][] tabs = {{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}, {5, 4}, {6, 5}, {7, 6}};
			for (int[] t : tabs) {
				int index = t[0], original = t[1];
				int start = original * 28, end = start + 28;
				part(src, 256, out, start, 0, end - 14, 160, index * 26, 0, Flip.NONE);
				part(src, 256, out, start + 14, 0, end, 160, index * 26 + 12, 0, Flip.NONE);
			}
			part(src, 256, out, 232, 0, 256, 15, 232, 0, Flip.NONE); // scrollers
			tree.putImage(path, out);
			ctx.count("converted textures", 1);
		});
	}

	/**
	 * 1.19.4 gave sliders their own texture. Before, the track was drawn with the disabled button from
	 * widgets.png and the handle with the normal/hovered button, so slider.png is built from those rows.
	 */
	public static Step slider1194() {
		return new SimpleStep(13, "1.19.4 slider texture", (tree, ctx) -> {
			String widgets = TEX + "gui/widgets.png", slider = TEX + "gui/slider.png";
			if (tree.has(slider)) return;
			BufferedImage src = tree.image(widgets);
			if (src == null) return;
			BufferedImage out = Img.create(src.getWidth(), src.getHeight());
			part(src, 256, out, 0, 46, 200, 66, 0, 0, Flip.NONE);
			part(src, 256, out, 0, 46, 200, 66, 0, 20, Flip.NONE);
			part(src, 256, out, 0, 66, 200, 86, 0, 40, Flip.NONE);
			part(src, 256, out, 0, 86, 200, 106, 0, 60, Flip.NONE);
			tree.putImage(slider, out);
			ctx.count("converted textures", 1);
		});
	}

	/**
	 * 1.20 put the title logo on one row (drawn 256x44 from a 256x64-unit texture) instead of two halves
	 * stacked in a 256x256 sheet ("Minec" at y 0 and "raft" at y 45, drawn side by side: 155 + 119 = 274 units
	 * wide). The old logo is rebuilt in one piece and scaled into the narrower new space; the texture is made 4x
	 * finer than the pack's so the uneven pixel scaling stays invisible.
	 */
	public static Step title120() {
		return new SimpleStep(15, "1.20 title logo", (tree, ctx) -> {
			int n = 0;
			for (String name : new String[]{"minecraft", "minceraft"}) {
				String path = TEX + "gui/title/" + name + ".png";
				BufferedImage src = tree.image(path);
				if (src == null || src.getWidth() != src.getHeight()) continue;
				int unit = Math.max(1, src.getWidth() / 256);
				BufferedImage logo = Img.create(274 * unit, 44 * unit);
				part(src, 256, logo, 0, 0, 155, 44, 0, 0, Flip.NONE);
				part(src, 256, logo, 0, 45, 119, 89, 155, 0, Flip.NONE);
				// low-resolution logos are drawn finer so the uneven scaling stays invisible; HD ones are fine as they are
				int fine = Math.max(4, unit);
				BufferedImage out = Img.create(256 * fine, 64 * fine);
				Img.blit(Img.resize(logo, 256 * fine, 44 * fine), 0, 0, 256 * fine, 44 * fine, out, 0, 0);
				tree.putImage(path, out);
				n++;
			}
			ctx.count("converted textures", n);
		});
	}

	/**
	 * Until 1.11 beds were drawn from block textures (bed_head_top, bed_feet_side...); 1.12 made them entity
	 * models, and 26.2 block models again with one texture per face. Old beds were all red, so the old textures
	 * become the red bed's faces (the head end is shared by every colour). Orientation follows the two models:
	 * sides and ends map straight across (west sides mirrored); the old top was shown turned 90 degrees and flipped,
	 * which is a mirror along the anti-diagonal in the new layout. Runs after the 26.2 slicer: a pack that also has
	 * a 1.12+ bed keeps that one.
	 */
	public static Step beds111() {
		return new SimpleStep(88, "1.11 block beds", (tree, ctx) -> {
			String old = TEX + "blocks/", block = TEX + "block/";
			int n = 0;
			n += bedFace(tree, old + "bed_head_top.png", block + "red_bed_head_up.png", TextureSteps::antiTranspose);
			n += bedFace(tree, old + "bed_feet_top.png", block + "red_bed_foot_up.png", TextureSteps::antiTranspose);
			n += bedFace(tree, old + "bed_head_side.png", block + "red_bed_head_east.png", img -> img);
			n += bedFace(tree, old + "bed_head_side.png", block + "red_bed_head_west.png", Img::mirrorHorizontally);
			n += bedFace(tree, old + "bed_feet_side.png", block + "red_bed_foot_east.png", img -> img);
			n += bedFace(tree, old + "bed_feet_side.png", block + "red_bed_foot_west.png", Img::mirrorHorizontally);
			n += bedFace(tree, old + "bed_feet_end.png", block + "red_bed_foot_south.png", img -> img);
			n += bedFace(tree, old + "bed_head_end.png", block + "bed_head_north.png", img -> img);
			ctx.count("converted textures", n);
		});
	}

	private static int bedFace(PackTree tree, String from, String to, java.util.function.UnaryOperator<BufferedImage> transform) {
		if (tree.has(to)) return 0;
		BufferedImage img = tree.image(from);
		if (img == null || img.getWidth() != img.getHeight()) return 0;
		tree.putImage(to, transform.apply(img));
		return 1;
	}

	/** new(x, y) = old(size-1-y, size-1-x): mirror along the anti-diagonal. */
	static BufferedImage antiTranspose(BufferedImage src) {
		int n = src.getWidth();
		BufferedImage out = Img.create(n, n);
		int[] s = Img.pixels(Img.toArgb(src)), d = Img.pixels(out);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) d[y * n + x] = s[(n - 1 - x) * n + (n - 1 - y)];
		}
		return out;
	}

	/**
	 * Until 1.20.4 menus were drawn over the tiled options_background texture, darkened to 25% (lists to 12.5%).
	 * 1.20.5 replaced it with menu_background / menu_list_background, drawn as they are. The pack's background
	 * becomes those, with the old darkening baked in, so menus look like they did. (In-world menus never used it.)
	 */
	public static Step menuBackground1205() {
		return new SimpleStep(32, "1.20.5 menu background", (tree, ctx) -> {
			BufferedImage dirt = tree.image(TEX + "gui/options_background.png");
			if (dirt == null) return;
			int n = 0;
			n += darkened(tree, dirt, TEX + "gui/menu_background.png", 0.25f);
			n += darkened(tree, dirt, TEX + "gui/menu_list_background.png", 0.125f);
			ctx.count("converted textures", n);
		});
	}

	private static int darkened(PackTree tree, BufferedImage src, String to, float factor) {
		if (tree.has(to)) return 0;
		BufferedImage out = Img.copy(src);
		int[] px = Img.pixels(out);
		for (int i = 0; i < px.length; i++) {
			int v = px[i];
			int r = (int) ((v >> 16 & 0xFF) * factor), g = (int) ((v >> 8 & 0xFF) * factor), b = (int) ((v & 0xFF) * factor);
			px[i] = (v & 0xFF000000) | r << 16 | g << 8 | b;
		}
		tree.putImage(to, out);
		return 1;
	}

	/**
	 * 1.21.4 removed the "builtin/entity" parent: chests, shields, banners, skulls, tridents and the like are drawn
	 * by special item models that use the item model only for its transforms. Mojang's own models lost the parent
	 * (chests got a template) and gained a particle texture; the pack's take the same from vanilla's model of that
	 * name and keep their transforms (a lowered shield stays lowered).
	 */
	public static Step builtinEntity1214() {
		return new SimpleStep(46, "1.21.4 special item models", (tree, ctx) -> {
			int n = 0;
			for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/models/") && p.endsWith(".json"))) {
				JsonObject model = Json.obj(tree.json(path));
				String parent = Json.str(model, "parent");
				if (parent == null || !(parent.equals("builtin/entity") || parent.equals("minecraft:builtin/entity"))) continue;
				model.remove("parent");
				JsonObject vanilla = Json.obj(ctx.vanilla().json(path));
				if (vanilla != null) {
					for (String key : new String[]{"parent", "gui_light"}) {
						if (vanilla.has(key) && !model.has(key)) model.add(key, vanilla.get(key).deepCopy());
					}
					JsonObject textures = Json.obj(vanilla, "textures");
					if (textures != null) {
						if (!(model.get("textures") instanceof JsonObject)) model.add("textures", new JsonObject());
						JsonObject own = model.getAsJsonObject("textures");
						for (var e : textures.entrySet()) if (!own.has(e.getKey())) own.add(e.getKey(), e.getValue().deepCopy());
					}
				}
				tree.putJson(path, model);
				n++;
			}
			ctx.count("special item models updated", n);
		});
	}

	/**
	 * 1.21.11 replaced the wide and small inventory effect backgrounds with one nine-sliced sprite. Packs from
	 * before 1.18 only drew the wide one; it becomes the new sprite, nine-sliced from its own size so it renders
	 * at both widths like it used to.
	 */
	public static Step effectBackground11211() {
		return new SimpleStep(75, "1.21.11 effect background", (tree, ctx) -> {
			String dir = TEX + "gui/sprites/container/inventory/";
			if (tree.has(dir + "effect_background.png") || !tree.has(dir + "effect_background_large.png")) return;
			tree.copy(dir + "effect_background_large.png", dir + "effect_background.png");
			if (!tree.has(dir + "effect_background.png.mcmeta")) {
				tree.putString(dir + "effect_background.png.mcmeta",
					"{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":120,\"height\":32,\"border\":4}}}");
			}
			ctx.count("converted textures", 1);
		});
	}
}
