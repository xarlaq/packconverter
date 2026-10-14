// Adapted from Mojang's slicer (https://github.com/Mojang/slicer), 1.20.5 migration:
// Copyright (c) Microsoft Corporation. All rights reserved. Licensed under the MIT license.

package dev.packconverter.core.slicer;

import dev.packconverter.core.slicer.Slicer.Box;
import dev.packconverter.core.slicer.Slicer.Input;
import dev.packconverter.core.slicer.Slicer.Output;
import dev.packconverter.core.slicer.Slicer.SimpleOutput;

import java.util.List;

/** 1.20.5 split the map_icons atlas into individual map decoration textures. */
public final class Slices1205 {
	private Slices1205() {
	}

	public static final String MAP_ICONS = "assets/minecraft/textures/map/map_icons.png";

	private static Output mapDecoration(String path, int index) {
		int x = index % 16, y = index / 16;
		return new SimpleOutput("assets/minecraft/textures/map/decorations/" + path + ".png", new Box(x * 8, y * 8, 8, 8, 128, 128));
	}

	public static final List<Input> INPUTS = List.of(
		new Input(MAP_ICONS).outputs(
			mapDecoration("player", 0),
			mapDecoration("frame", 1),
			mapDecoration("red_marker", 2),
			mapDecoration("blue_marker", 3),
			mapDecoration("target_x", 4),
			mapDecoration("target_point", 5),
			mapDecoration("player_off_map", 6),
			mapDecoration("player_off_limits", 7),
			mapDecoration("woodland_mansion", 8),
			mapDecoration("ocean_monument", 9),
			mapDecoration("white_banner", 10),
			mapDecoration("orange_banner", 11),
			mapDecoration("magenta_banner", 12),
			mapDecoration("light_blue_banner", 13),
			mapDecoration("yellow_banner", 14),
			mapDecoration("lime_banner", 15),
			mapDecoration("pink_banner", 16),
			mapDecoration("gray_banner", 17),
			mapDecoration("light_gray_banner", 18),
			mapDecoration("cyan_banner", 19),
			mapDecoration("purple_banner", 20),
			mapDecoration("blue_banner", 21),
			mapDecoration("brown_banner", 22),
			mapDecoration("green_banner", 23),
			mapDecoration("red_banner", 24),
			mapDecoration("black_banner", 25),
			mapDecoration("red_x", 26),
			mapDecoration("desert_village", 27),
			mapDecoration("plains_village", 28),
			mapDecoration("savanna_village", 29),
			mapDecoration("snowy_village", 30),
			mapDecoration("taiga_village", 31),
			mapDecoration("jungle_temple", 32),
			mapDecoration("swamp_hut", 33)
		)
	);
}
