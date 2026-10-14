// Adapted from Mojang's slicer (https://github.com/Mojang/slicer), 26.2 migration:
// Copyright (c) Microsoft Corporation. All rights reserved. Licensed under the MIT license.

package dev.packconverter.core.slicer;

import dev.packconverter.core.slicer.Slicer.Box;
import dev.packconverter.core.slicer.Slicer.Input;
import dev.packconverter.core.slicer.Slicer.MultipartOutput;
import dev.packconverter.core.slicer.Slicer.SimpleOutput;
import dev.packconverter.core.slicer.Slicer.TransformedBox;

import java.util.ArrayList;
import java.util.List;

/** 26.2 turned beds, signs and hanging signs from entity models into block models with block textures. */
public final class Slices262 {
	private Slices262() {
	}

	public static final String[] COLORS = {"black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
		"lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow"};
	public static final String[] WOODS = {"acacia", "birch", "crimson", "mangrove", "pale_oak", "warped", "bamboo", "cherry",
		"dark_oak", "jungle", "oak", "spruce"};

	private static final String ROOT = "assets/minecraft/textures/";

	public static final List<Input> INPUTS = inputs();

	private static List<Input> inputs() {
		List<Input> list = new ArrayList<>();
		list.add(makeStaticBedPartProcessor());
		for (String color : COLORS) list.add(makeBedProcessor(color));
		for (String wood : WOODS) list.add(makeStandingSignProcessor(wood));
		for (String wood : WOODS) list.add(makeHangingSignProcessor(wood));
		return List.copyOf(list);
	}

	private static Box b(int x, int y, int w, int h) {
		return new Box(x, y, w, h, 64, 64);
	}

	private static Box s(int x, int y, int w, int h) {
		return new Box(x, y, w, h, 64, 32);
	}

	private static TransformedBox tb(Box box, int ox, int oy) {
		return new TransformedBox(box, List.of(), ox, oy);
	}

	private static Input makeStaticBedPartProcessor() {
		return new Input(ROOT + "entity/bed/black.png").outputs(
			new SimpleOutput(ROOT + "block/bed_down.png", b(28, 6, 16, 16)).apply(TransformedBox::rotate180),
			new MultipartOutput(ROOT + "block/bed_head_north.png", 16, 16,
				new TransformedBox(b(6, 0, 16, 6), List.of(TransformedBox::rotate180), 0, 7),
				tb(b(53, 21, 3, 3), 0, 13),
				tb(b(56, 21, 3, 3), 3, 13),
				tb(b(59, 9, 3, 3), 10, 13),
				tb(b(50, 9, 3, 3), 13, 13)
			)
		);
	}

	private static Input makeBedProcessor(String color) {
		return new Input(ROOT + "entity/bed/" + color + ".png").outputs(
			new SimpleOutput(ROOT + "block/" + color + "_bed_head_up.png", b(6, 6, 16, 16)),
			new SimpleOutput(ROOT + "block/" + color + "_bed_foot_up.png", b(6, 28, 16, 16)),
			new MultipartOutput(ROOT + "block/" + color + "_bed_foot_south.png", 16, 16,
				new TransformedBox(b(22, 22, 16, 6), List.of(TransformedBox::mirrorVertically), 0, 7),
				tb(b(53, 3, 3, 3), 0, 13),
				tb(b(56, 3, 3, 3), 3, 13),
				tb(b(59, 15, 3, 3), 10, 13),
				tb(b(50, 15, 3, 3), 13, 13)
			),
			new MultipartOutput(ROOT + "block/" + color + "_bed_foot_west.png", 16, 16,
				new TransformedBox(b(0, 28, 6, 16), List.of(TransformedBox::rotate270), 0, 7),
				new TransformedBox(b(56, 0, 3, 3), List.of(TransformedBox::mirrorVertically), 7, 13),
				tb(b(59, 3, 3, 3), 10, 13),
				tb(b(50, 3, 3, 3), 13, 13)
			),
			new MultipartOutput(ROOT + "block/" + color + "_bed_foot_east.png", 16, 16,
				new TransformedBox(b(22, 28, 6, 16), List.of(TransformedBox::rotate90), 0, 7),
				tb(b(53, 15, 3, 3), 0, 13),
				tb(b(56, 15, 3, 3), 3, 13),
				new TransformedBox(b(56, 12, 3, 3), List.of(TransformedBox::rotate270, TransformedBox::mirrorVertically), 6, 13)
			),
			new MultipartOutput(ROOT + "block/" + color + "_bed_head_east.png", 16, 16,
				new TransformedBox(b(22, 6, 6, 16), List.of(TransformedBox::rotate90), 0, 7),
				new TransformedBox(b(56, 18, 3, 3), List.of(TransformedBox::rotate180, TransformedBox::mirrorVertically), 7, 13),
				tb(b(59, 21, 3, 3), 10, 13),
				tb(b(50, 21, 3, 3), 13, 13)
			),
			new MultipartOutput(ROOT + "block/" + color + "_bed_head_west.png", 16, 16,
				new TransformedBox(b(0, 6, 6, 16), List.of(TransformedBox::rotate270), 0, 7),
				tb(b(53, 9, 3, 3), 0, 13),
				tb(b(56, 9, 3, 3), 3, 13),
				new TransformedBox(b(56, 6, 3, 3), List.of(TransformedBox::rotate90, TransformedBox::mirrorVertically), 6, 13)
			)
		);
	}

	private static Input makeStandingSignProcessor(String woodType) {
		return new Input(ROOT + "entity/signs/" + woodType + ".png").outputs(
			new MultipartOutput(ROOT + "gui/signs/" + woodType + ".png", 24, 26,
				tb(s(2, 2, 24, 12), 0, 0),
				tb(s(2, 16, 2, 14), 11, 12)
			),
			new MultipartOutput(ROOT + "block/" + woodType + "_sign.png", 32, 32,
				tb(s(2, 0, 24, 14), 0, 0),
				tb(s(26, 2, 2, 12), 24, 2),
				tb(s(28, 2, 24, 12), 0, 16),
				tb(s(0, 2, 2, 12), 24, 16),
				new TransformedBox(s(26, 0, 24, 2), List.of(TransformedBox::mirrorVertically), 0, 28),
				tb(s(2, 16, 4, 14), 28, 0),
				tb(s(6, 16, 2, 14), 28, 16),
				tb(s(0, 16, 2, 14), 30, 16),
				new TransformedBox(s(4, 14, 2, 2), List.of(TransformedBox::mirrorVertically), 28, 30)
			)
		);
	}

	private static Input makeHangingSignProcessor(String woodType) {
		return new Input(ROOT + "entity/signs/hanging/" + woodType + ".png").outputs(
			new MultipartOutput(ROOT + "block/" + woodType + "_hanging_sign.png", 32, 32,
				tb(s(0, 14, 32, 10), 0, 16), // west - front - east - back
				tb(s(2, 12, 14, 2), 2, 14), // up
				new TransformedBox(s(16, 12, 14, 2), List.of(TransformedBox::mirrorVertically), 2, 26), // down
				tb(s(4, 0, 16, 6), 0, 0), // bar: up - front
				new TransformedBox(s(20, 0, 16, 4), List.of(TransformedBox::rotate180), 0, 9), // bar: down
				tb(s(20, 4, 4, 2), 16, 4), // bar: east
				tb(s(24, 4, 16, 2), 0, 7), // bar: back
				tb(s(0, 4, 4, 2), 16, 7), // bar: west
				tb(s(14, 6, 12, 6), 20, 0), // chain angled
				tb(s(0, 6, 9, 6), 22, 7) // chain straight
			)
		);
	}
}
