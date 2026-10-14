package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;

import java.awt.image.BufferedImage;

/**
 * 1.9 moved the creative inventory tab's armor slots and player box 45 px right and put an off-hand slot where they
 * were (vanilla 1.8.9 → 1.9.4, the same ever since). As in {@link Inventory19Step}: flat or see-through
 * panels get the block moved, the panel filled in behind it, and an off-hand slot copied from one of the pack's
 * slots; picture panels stay as drawn. Coordinates are vanilla's 256 px layout, times the pack's scale.
 */
public final class CreativeInventory19Step implements Step {
	private static final String PATH = "assets/minecraft/textures/gui/container/creative_inventory/tab_inventory.png";
	/** Where vanilla 1.8's panel is empty, right of the armor and player box. {x0, y0, x1, y1} */
	private static final int[][] FREE = {{82, 4, 188, 51}};
	/** The armor slots and player box: from x0 to x1, y0 to y1; they move DX right. */
	private static final int X0 = 8, X1 = 81, Y0 = 5, Y1 = 51, DX = 45;

	@Override
	public int format() {
		return 2;
	}

	@Override
	public String name() {
		return "1.9 creative inventory layout";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		BufferedImage src = tree.image(PATH);
		if (src == null || src.getWidth() < 256 || src.getWidth() % 256 != 0 || src.getHeight() < src.getWidth() * 136 / 256) return;
		int u = src.getWidth() / 256, w = src.getWidth();
		BufferedImage out = Img.copy(src);
		int[] in = Img.pixels(Img.toArgb(src)), px = Img.pixels(out);
		Integer bg = Inventory19Step.flatBackground(in, w, u, FREE);
		if (bg == null) {
			ctx.count("inventories left as drawn (picture panel)", 1);
			return;
		}
		for (int y = Y0 * u; y < Y1 * u; y++) {
			// where the block was: the panel colour found left of it in this row (follows vertical gradients)
			int panel = bg;
			for (int x = X0 * u - 1; x >= 0; x--) {
				if (Inventory19Step.same(in[y * w + x], bg)) {
					panel = in[y * w + x];
					break;
				}
			}
			for (int x = X0 * u; x < (X0 + DX) * u; x++) px[y * w + x] = panel;
			for (int x = X0 * u; x < X1 * u; x++) px[y * w + x + DX * u] = in[y * w + x];
		}
		// off-hand slot: a copy of a main inventory slot if they look alike, else of an armor slot if those do
		int[] slot = Inventory19Step.drawn(in, w, u, bg, 8, 53) && Inventory19Step.alike(in, w, u, 8, 53, 26, 53) ? new int[]{8, 53}
			: Inventory19Step.drawn(in, w, u, bg, 8, 5) && Inventory19Step.alike(in, w, u, 8, 5, 8, 32) ? new int[]{8, 5} : null;
		if (slot != null) Inventory19Step.copySlot(in, px, w, u, bg, slot[0], slot[1], 34, 19);
		tree.putImage(PATH, out);
		ctx.count("converted textures", 1);
	}
}
