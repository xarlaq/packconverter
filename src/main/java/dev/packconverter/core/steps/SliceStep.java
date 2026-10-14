package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.slicer.Slicer;
import dev.packconverter.core.slicer.Slicer.Box;
import dev.packconverter.core.slicer.Slicer.Input;
import dev.packconverter.core.slicer.Slicer.SimpleOutput;
import dev.packconverter.core.slicer.Slices114;
import dev.packconverter.core.slicer.Slices1202;
import dev.packconverter.core.slicer.Slices1205;
import dev.packconverter.core.slicer.Slices262;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Steps that cut texture sheets into individual sprites (Mojang's slicer migrations and similar ones). */
public final class SliceStep implements Step {
	private final int format;
	private final String name;
	private final List<Input> inputs;
	private final BiConsumer<PackTree, Context> before;
	private final String reference;

	private SliceStep(int format, String name, String reference, List<Input> inputs, BiConsumer<PackTree, Context> before) {
		this.format = format;
		this.reference = reference;
		this.name = name;
		this.inputs = inputs;
		this.before = before;
	}

	@Override
	public int format() {
		return format;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		tree.prefetchImages(inputs.stream().map(Input::path).toList());
		prepare(tree, ctx);
		ctx.count("sliced sprites", Slicer.run(inputs, tree, ctx));
	}

	/** Brings older sheet layouts to the one the inputs describe (padding, regridding). */
	public void prepare(PackTree tree, Context ctx) {
		if (before != null) before.accept(tree, ctx);
	}

	/** The last version whose sheets the inputs describe. */
	public String reference() {
		return reference;
	}

	public List<Input> inputs() {
		return inputs;
	}

	/** Every slicing step, for tools. */
	public static List<SliceStep> all() {
		return List.of(v1_14(), v1_20_2(), v1_20_5(), v1_21_11(), v26_2());
	}

	/**
	 * 1.14: particles, paintings, mob effect icons, explosion and sweep frames. 1.13 kept format 4, so this also
	 * runs for format-4 packs that still have the sheets. Before 1.13 particles.png was half the size (a
	 * 128-unit sheet); it is padded to the 1.13 layout first.
	 */
	public static SliceStep v1_14() {
		return new SliceStep(5, "1.14 sprite sheets", "1.13.2", Slices114.INPUTS, (tree, ctx) -> {
			if (ctx.before(4) && tree.has(Slices114.PARTICLES)) {
				BufferedImage old = tree.image(Slices114.PARTICLES);
				if (old != null) {
					BufferedImage padded = Img.create(old.getWidth() * 2, old.getHeight() * 2);
					Img.blit(old, 0, 0, old.getWidth(), old.getHeight(), padded, 0, 0);
					tree.putImage(Slices114.PARTICLES, padded);
				}
			}
		});
	}

	public static SliceStep v1_20_2() {
		return new SliceStep(18, "1.20.2 GUI sprites", "1.20.1", Slices1202.INPUTS, null);
	}

	/** 1.20.5 map decorations; before 1.13 map_icons.png was a 4x4 grid (1.13 moved the same icons to a 16x16 grid). */
	public static SliceStep v1_20_5() {
		return new SliceStep(32, "1.20.5 map decorations", "1.20.4", Slices1205.INPUTS, (tree, ctx) -> {
			if (ctx.before(4) && tree.has(Slices1205.MAP_ICONS)) {
				BufferedImage old = tree.image(Slices1205.MAP_ICONS);
				if (old != null) {
					int cell = old.getWidth() / 4;
					BufferedImage grid = Img.create(cell * 16, cell * 16);
					for (int i = 0; i < 16; i++) {
						Img.blit(old, (i % 4) * cell, (i / 4) * cell, cell, cell, grid, i * cell, 0);
					}
					tree.putImage(Slices1205.MAP_ICONS, grid);
				}
			}
		});
	}

	public static SliceStep v26_2() {
		return new SliceStep(88, "26.2 beds and signs", "26.1.2", Slices262.INPUTS, null);
	}

	private static final String[] MOON_PHASES = {"full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
		"new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"};

	/** 1.21.11 split moon_phases.png (4x2 grid) into one texture per phase. */
	public static SliceStep v1_21_11() {
		List<Slicer.Output> outputs = new ArrayList<>();
		for (int i = 0; i < MOON_PHASES.length; i++) {
			outputs.add(new SimpleOutput("assets/minecraft/textures/environment/celestial/moon/" + MOON_PHASES[i] + ".png",
				new Box((i % 4) * 32, (i / 4) * 32, 32, 32, 128, 64)));
		}
		Input moon = new Input("assets/minecraft/textures/environment/moon_phases.png").outputs(outputs.toArray(Slicer.Output[]::new));
		return new SliceStep(75, "1.21.11 moon phases", "1.21.10", List.of(moon), null);
	}
}
