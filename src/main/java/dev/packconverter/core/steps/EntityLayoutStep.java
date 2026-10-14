package dev.packconverter.core.steps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.io.Json;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mobs whose model changed (cow, pig, horses, magma cube...) need their texture rearranged, not just renamed.
 * The moves come from resources/packconverter/entity-layouts.json (EntityLayoutGenerator: 26.3 Programmer Art
 * is old art in the new layouts, so comparing it with the old textures shows where Mojang moved each part).
 * The pack's own texture is rearranged the same way at its own resolution. The few pixels a new model part has
 * no old counterpart for are taken from the game's texture, so the mob has no holes.
 */
public final class EntityLayoutStep implements Step {
	private record Copy(int dx, int dy, int w, int h, int sx, int sy, int t) {
	}

	private record Layout(String target, String source, int since, int fromWidth, int fromHeight, int width, int height, List<Copy> copies) {
	}

	private static List<Layout> layouts;

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "entity texture layouts";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		List<Layout> todo = new ArrayList<>();
		for (Layout layout : layouts()) {
			if (!ctx.before(layout.since())) continue;
			if (!layout.source().equals(layout.target()) && tree.has(layout.target())) continue; // pack has the new one
			if (tree.has(layout.source())) todo.add(layout);
		}
		tree.prefetchImages(todo.stream().map(Layout::source).toList());
		// rearranging is independent per texture: do it on all cores, then store the results in order
		List<BufferedImage> results = todo.parallelStream().map(layout -> {
			BufferedImage src = tree.image(layout.source());
			// only textures still in the old layout's proportions
			if (src == null || (long) src.getWidth() * layout.fromHeight() != (long) src.getHeight() * layout.fromWidth()) return null;
			return remap(layout, src, src.getWidth() / (double) layout.fromWidth(), ctx);
		}).toList();
		int converted = 0;
		for (int i = 0; i < todo.size(); i++) {
			Layout layout = todo.get(i);
			if (results.get(i) == null) continue;
			tree.putImage(layout.target(), results.get(i));
			if (!layout.source().equals(layout.target())) tree.remove(layout.source());
			converted++;
		}
		ctx.count("mob textures rearranged", converted);
	}

	private static BufferedImage remap(Layout layout, BufferedImage src, double k, Context ctx) {
		int ow = (int) Math.round(layout.width() * k), oh = (int) Math.round(layout.height() * k);
		BufferedImage out = Img.create(ow, oh);
		int[] o = Img.pixels(out), s = Img.pixels(Img.toArgb(src));
		int sw = src.getWidth(), sh = src.getHeight();
		// which copy explains each pixel of the new layout (first one wins, like the generator)
		int[] owner = new int[layout.width() * layout.height()];
		java.util.Arrays.fill(owner, -1);
		for (int i = 0; i < layout.copies().size(); i++) {
			Copy c = layout.copies().get(i);
			for (int y = c.dy(); y < c.dy() + c.h(); y++) {
				for (int x = c.dx(); x < c.dx() + c.w(); x++) {
					int idx = y * layout.width() + x;
					if (owner[idx] < 0) owner[idx] = i;
				}
			}
		}
		BufferedImage fill = null;
		for (int y = 0; y < oh; y++) {
			for (int x = 0; x < ow; x++) {
				double bx = (x + 0.5) / k, by = (y + 0.5) / k;
				int px = Math.min(layout.width() - 1, (int) bx), py = Math.min(layout.height() - 1, (int) by);
				int i = owner[py * layout.width() + px];
				if (i < 0) {
					if (fill == null) fill = vanillaFill(layout, ow, oh, ctx);
					if (fill != null) o[y * ow + x] = Img.pixels(fill)[y * ow + x];
					continue;
				}
				Copy c = layout.copies().get(i);
				double u = bx - (c.dx() + 0.5), v = by - (c.dy() + 0.5);
				if (c.t() >= 4) u = -u;
				for (int r = 0; r < (c.t() & 3); r++) {
					double nu = -v, nv = u;
					u = nu;
					v = nv;
				}
				int sx = (int) Math.floor((c.sx() + 0.5 + u) * k), sy = (int) Math.floor((c.sy() + 0.5 + v) * k);
				sx = Math.max(0, Math.min(sw - 1, sx));
				sy = Math.max(0, Math.min(sh - 1, sy));
				o[y * ow + x] = s[sy * sw + sx];
			}
		}
		return out;
	}

	/** The game's own texture at the output size, for parts the old texture has nothing for. */
	private static BufferedImage vanillaFill(Layout layout, int w, int h, Context ctx) {
		BufferedImage vanilla = Img.decode(ctx.vanilla().read(layout.target()));
		return vanilla == null ? Img.create(w, h) : Img.resize(vanilla, w, h);
	}

	private static synchronized List<Layout> layouts() {
		if (layouts == null) {
			List<Layout> list = new ArrayList<>();
			try (InputStream in = EntityLayoutStep.class.getResourceAsStream("/packconverter/entity-layouts.json")) {
				if (in == null) throw new IllegalStateException("entity-layouts.json missing from the mod jar");
				JsonObject textures = Json.obj(Json.obj(Json.parse(in.readAllBytes())), "textures");
				for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
					JsonObject l = e.getValue().getAsJsonObject();
					List<Copy> copies = new ArrayList<>();
					for (JsonElement c : l.getAsJsonArray("copies")) {
						JsonArray a = c.getAsJsonArray();
						copies.add(new Copy(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt(),
							a.get(4).getAsInt(), a.get(5).getAsInt(), a.get(6).getAsInt()));
					}
					list.add(new Layout(e.getKey(), l.get("source").getAsString(), l.get("since").getAsInt(), l.get("from_width").getAsInt(),
						l.get("from_height").getAsInt(), l.get("width").getAsInt(), l.get("height").getAsInt(), copies));
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			layouts = list;
		}
		return layouts;
	}
}
