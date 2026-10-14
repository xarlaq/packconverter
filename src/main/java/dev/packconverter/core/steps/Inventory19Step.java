package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 1.9 moved the inventory's crafting grid, arrow and result slot 10 px right and 8 px up, made the player box 3 px
 * narrower and added an off-hand slot below its corner (vanilla 1.8.9 → 1.9.4, the same ever since). Packs draw
 * this area many ways, so:
 * <ul>
 * <li>flat or see-through panels: the shapes in the crafting area move (frames drawn a pixel bigger than vanilla's
 * too, not logos that only touch it) and their old place takes the panel around it;</li>
 * <li>picture panels stay as drawn: moving part of a picture tears it, which looks worse than slots a little off;</li>
 * <li>the player box's right part moves 3 px left, the panel beside it following;</li>
 * <li>the off-hand slot is a copy of one of the pack's slots, drawn behind anything already there.</li>
 * </ul>
 * Coordinates are vanilla's 256 px layout, times the pack's scale.
 */
public final class Inventory19Step implements Step {
	private static final String PATH = "assets/minecraft/textures/gui/container/inventory.png";
	/** Where vanilla 1.8's panel is empty: the colour found there is the pack's panel colour. {x0, y0, x1, y1} */
	private static final int[][] FREE = {{79, 4, 172, 25}, {79, 25, 87, 79}, {161, 25, 172, 79}, {87, 61, 161, 79}, {4, 79, 172, 83}};
	private static final int DX = 10, DY = -8;

	@Override
	public int format() {
		return 2;
	}

	@Override
	public String name() {
		return "1.9 inventory layout";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		BufferedImage src = tree.image(PATH);
		if (src == null || src.getWidth() < 256 || src.getWidth() % 256 != 0 || src.getHeight() < src.getWidth() * 166 / 256) return;
		int u = src.getWidth() / 256, w = src.getWidth();
		BufferedImage out = Img.copy(src);
		int[] in = Img.pixels(Img.toArgb(src)), px = Img.pixels(out);
		Integer bg = flatBackground(in, w, u, FREE);
		if (bg == null) {
			ctx.count("inventories left as drawn (picture panel)", 1);
			return;
		}
		// player box: its right part 3 px left (border 78 → 75), the panel to its right following
		for (int y = 7 * u; y < 79 * u; y++) {
			for (int x = 61 * u; x < 79 * u; x++) px[y * w + x] = in[y * w + x + 3 * u];
		}
		moveShapes(in, px, w, u, bg);
		// off-hand slot: a copy of one of the pack's slots. A main inventory slot if they look alike, else the boots
		// slot if the armor slots do (no armor pictures drawn in). Packs whose rows are bars, not slots, get none.
		int[] slot = drawn(in, w, u, bg, 7, 83) && alike(in, w, u, 7, 83, 25, 83) ? new int[]{7, 83}
			: drawn(in, w, u, bg, 7, 61) && alike(in, w, u, 7, 61, 7, 43) ? new int[]{7, 61} : null;
		if (slot != null) copySlot(in, px, w, u, bg, slot[0], slot[1], 76, 61);
		tree.putImage(PATH, out);
		ctx.count("converted textures", 1);
	}

	/**
	 * The panel colour if most of the empty panel ({@code free}: {x0, y0, x1, y1} where vanilla 1.8's is empty) has it
	 * (a flat or see-through panel), else null (a picture).
	 */
	static Integer flatBackground(int[] in, int w, int u, int[][] free) {
		Map<Integer, Integer> counts = new HashMap<>();
		int total = 0;
		for (int[] r : free) {
			for (int y = r[1] * u; y < r[3] * u; y++) {
				for (int x = r[0] * u; x < r[2] * u; x++) {
					counts.merge(key(in[y * w + x]), 1, Integer::sum);
					total++;
				}
			}
		}
		int mode = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey(), matching = 0;
		for (int[] r : free) {
			for (int y = r[1] * u; y < r[3] * u; y++) {
				for (int x = r[0] * u; x < r[2] * u; x++) if (same(in[y * w + x], mode)) matching++;
			}
		}
		return matching >= total * 0.6 ? mode : null;
	}

	/**
	 * Moves the shapes (8-connected non-panel pixels) that are in the crafting area: any with a pixel within 1 px of
	 * vanilla's area and at least half within 3 px of it.
	 */
	private static void moveShapes(int[] in, int[] px, int w, int u, int bg) {
		int sx0 = 79 * u, sy0 = 4 * u, sx1 = 172 * u, sy1 = 79 * u, sw = sx1 - sx0, sh = sy1 - sy0;
		int[] component = new int[sw * sh];
		java.util.Arrays.fill(component, -1);
		int[] queue = new int[sw * sh];
		java.util.List<int[]> moved = new java.util.ArrayList<>();
		int next = 0;
		for (int start = 0; start < sw * sh; start++) {
			if (component[start] != -1 || same(in[(sy0 + start / sw) * w + sx0 + start % sw], bg)) continue;
			int head = 0, tail = 0, near = 0, close = 0;
			queue[tail++] = start;
			component[start] = next;
			while (head < tail) {
				int i = queue[head++], x = i % sw, y = i / sw, ax = sx0 + x, ay = sy0 + y;
				if (ax >= 86 * u && ax < 162 * u && ay >= 24 * u && ay < 62 * u) near++;
				if (ax >= 84 * u && ax < 164 * u && ay >= 22 * u && ay < 64 * u) close++;
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nx = x + dx, ny = y + dy;
						if (nx < 0 || ny < 0 || nx >= sw || ny >= sh) continue;
						int j = ny * sw + nx;
						if (component[j] != -1 || same(in[(sy0 + ny) * w + sx0 + nx], bg)) continue;
						component[j] = next;
						queue[tail++] = j;
					}
				}
			}
			if (near > 0 && close * 2 >= tail) moved.add(java.util.Arrays.copyOf(queue, tail));
			next++;
		}
		boolean[] gone = new boolean[sw * sh];
		for (int[] pixels : moved) {
			for (int i : pixels) gone[i] = true;
		}
		// a margin of panel-coloured pixels goes along: soft shadows around slots are within the colour tolerance
		int r = 2 * u;
		java.util.List<Integer> margin = new java.util.ArrayList<>();
		boolean[] taken = gone.clone();
		for (int[] pixels : moved) {
			for (int i : pixels) {
				int x = i % sw, y = i / sw;
				for (int ny = Math.max(0, y - r); ny <= Math.min(sh - 1, y + r); ny++) {
					for (int nx = Math.max(0, x - r); nx <= Math.min(sw - 1, x + r); nx++) {
						int j = ny * sw + nx;
						if (taken[j] || !same(in[(sy0 + ny) * w + sx0 + nx], bg)) continue;
						taken[j] = true;
						margin.add(j);
					}
				}
			}
		}
		// where they were: the nearest panel pixel in the same row or column (follows gradients), else the panel colour
		for (int[] pixels : moved) {
			for (int i : pixels) px[(sy0 + i / sw) * w + sx0 + i % sw] = nearestPanel(in, taken, i, sw, sh, sx0, sy0, w, bg);
		}
		for (int i : margin) px[(sy0 + i / sw) * w + sx0 + i % sw] = nearestPanel(in, taken, i, sw, sh, sx0, sy0, w, bg);
		for (int[] pixels : moved) {
			for (int i : pixels) {
				int x = sx0 + i % sw + DX * u, y = sy0 + i / sw + DY * u;
				if (x < sx1 && y >= sy0) px[y * w + x] = in[(sy0 + i / sw) * w + sx0 + i % sw];
			}
		}
		// the margin only over panel, never over something else that is there
		for (int i : margin) {
			int x = sx0 + i % sw + DX * u, y = sy0 + i / sw + DY * u;
			if (x < sx1 && y >= sy0 && same(px[y * w + x], bg)) px[y * w + x] = in[(sy0 + i / sw) * w + sx0 + i % sw];
		}
	}

	private static int nearestPanel(int[] in, boolean[] gone, int i, int sw, int sh, int sx0, int sy0, int w, int bg) {
		int x = i % sw, y = i / sw;
		for (int d = 1; d < Math.max(sw, sh); d++) {
			for (int[] step : new int[][]{{-d, 0}, {d, 0}, {0, d}, {0, -d}}) {
				int nx = x + step[0], ny = y + step[1];
				if (nx < 0 || ny < 0 || nx >= sw || ny >= sh || gone[ny * sw + nx]) continue;
				int c = in[(sy0 + ny) * w + sx0 + nx];
				if (same(c, bg)) return c;
			}
		}
		return bg;
	}

	/**
	 * Copies a slot: only its biggest shape (frame and inside), so bits of neighbouring frames that a pack's grid
	 * puts in the same 18 px stay behind; drawn behind anything already there (a logo stays whole).
	 */
	static void copySlot(int[] in, int[] px, int w, int u, int bg, int fromX, int fromY, int toX, int toY) {
		int size = 18 * u;
		int[] component = new int[size * size], queue = new int[size * size], best = new int[0];
		java.util.Arrays.fill(component, -1);
		for (int start = 0; start < size * size; start++) {
			if (component[start] != -1 || same(in[(fromY * u + start / size) * w + fromX * u + start % size], bg)) continue;
			int head = 0, tail = 0;
			queue[tail++] = start;
			component[start] = start;
			while (head < tail) {
				int i = queue[head++], x = i % size, y = i / size;
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nx = x + dx, ny = y + dy, j = ny * size + nx;
						if (nx < 0 || ny < 0 || nx >= size || ny >= size || component[j] != -1) continue;
						if (same(in[(fromY * u + ny) * w + fromX * u + nx], bg)) continue;
						component[j] = start;
						queue[tail++] = j;
					}
				}
			}
			if (tail > best.length) best = java.util.Arrays.copyOf(queue, tail);
		}
		for (int i : best) {
			int to = (toY * u + i / size) * w + toX * u + i % size;
			if (same(px[to], bg)) px[to] = in[(fromY * u + i / size) * w + fromX * u + i % size];
		}
	}

	/** True if an 18 px slot has something drawn: at least 4% of it isn't panel (two empty spots look alike too). */
	static boolean drawn(int[] in, int w, int u, int bg, int x0, int y0) {
		int size = 18 * u, shape = 0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) if (!same(in[(y0 * u + y) * w + x0 * u + x], bg)) shape++;
		}
		return shape * 25 >= size * size;
	}

	/** True if two 18 px slots look the same (at most 2% of pixels differ by more than a little). */
	static boolean alike(int[] in, int w, int u, int x1, int y1, int x2, int y2) {
		int differ = 0, size = 18 * u;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int a = in[(y1 * u + y) * w + x1 * u + x], b = in[(y2 * u + y) * w + x2 * u + x];
				if (!same(a, key(b) == 0 ? 0 : b)) differ++;
			}
		}
		return differ <= size * size / 50;
	}

	/** Colours compared with see-through pixels all alike. */
	static int key(int argb) {
		return (argb >>> 24) <= 10 ? 0 : argb;
	}

	static boolean same(int argb, int bg) {
		if (bg == 0) return (argb >>> 24) <= 10;
		for (int shift = 0; shift <= 24; shift += 8) {
			if (Math.abs((argb >>> shift & 0xFF) - (bg >>> shift & 0xFF)) > 12) return false;
		}
		return true;
	}
}
