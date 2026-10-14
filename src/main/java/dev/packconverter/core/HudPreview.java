package dev.packconverter.core;

import dev.packconverter.core.io.Img;
import dev.packconverter.core.source.FilteredPackSource;
import dev.packconverter.core.source.PackSource;
import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The bits of a pack the pack screen previews (HUD, crosshair, hit particles, PvP items, blocks, armor), as 26.3 will
 * show them: old packs go through the converter, limited to the files these come from.
 */
public final class HudPreview {
	/** A preview image, its size in GUI pixels (texture pixels for armor), and where 26.3 reads it. */
	public enum Piece {
		HOTBAR("gui/sprites/hud/hotbar", 182, 22),
		SELECTION("gui/sprites/hud/hotbar_selection", 24, 23),
		OFFHAND("gui/sprites/hud/hotbar_offhand_left", 29, 24),
		CROSSHAIR("gui/sprites/hud/crosshair", 15, 15),
		ARMOR("gui/sprites/hud/armor_full", 9, 9),
		HEART_CONTAINER("gui/sprites/hud/heart/container", 9, 9),
		HEART("gui/sprites/hud/heart/full", 9, 9),
		HEART_HALF("gui/sprites/hud/heart/half", 9, 9),
		GOLD_HEART("gui/sprites/hud/heart/absorbing_full", 9, 9),
		FOOD_EMPTY("gui/sprites/hud/food_empty", 9, 9),
		FOOD("gui/sprites/hud/food_full", 9, 9),
		FOOD_HALF("gui/sprites/hud/food_half", 9, 9),
		XP_BACKGROUND("gui/sprites/hud/experience_bar_background", 182, 5),
		XP("gui/sprites/hud/experience_bar_progress", 182, 5),
		CRIT("particle/critical_hit", 8, 8),
		SHARPNESS("particle/enchanted_hit", 8, 8),

		WOODEN_SWORD("item/wooden_sword", 16, 16),
		STONE_SWORD("item/stone_sword", 16, 16),
		COPPER_SWORD("item/copper_sword", 16, 16),
		IRON_SWORD("item/iron_sword", 16, 16),
		GOLDEN_SWORD("item/golden_sword", 16, 16),
		DIAMOND_SWORD("item/diamond_sword", 16, 16),
		NETHERITE_SWORD("item/netherite_sword", 16, 16),
		WOODEN_PICKAXE("item/wooden_pickaxe", 16, 16),
		STONE_PICKAXE("item/stone_pickaxe", 16, 16),
		COPPER_PICKAXE("item/copper_pickaxe", 16, 16),
		IRON_PICKAXE("item/iron_pickaxe", 16, 16),
		GOLDEN_PICKAXE("item/golden_pickaxe", 16, 16),
		DIAMOND_PICKAXE("item/diamond_pickaxe", 16, 16),
		NETHERITE_PICKAXE("item/netherite_pickaxe", 16, 16),
		WOODEN_AXE("item/wooden_axe", 16, 16),
		STONE_AXE("item/stone_axe", 16, 16),
		COPPER_AXE("item/copper_axe", 16, 16),
		IRON_AXE("item/iron_axe", 16, 16),
		GOLDEN_AXE("item/golden_axe", 16, 16),
		DIAMOND_AXE("item/diamond_axe", 16, 16),
		NETHERITE_AXE("item/netherite_axe", 16, 16),
		PEARL("item/ender_pearl", 16, 16),
		GOLDEN_APPLE("item/golden_apple", 16, 16),
		STEAK("item/cooked_beef", 16, 16),
		BOW("item/bow", 16, 16),
		BOW_PULLING_0("item/bow_pulling_0", 16, 16),
		BOW_PULLING_1("item/bow_pulling_1", 16, 16),
		BOW_PULLING_2("item/bow_pulling_2", 16, 16),
		POTION_OVERLAY("item/potion_overlay", 16, 16),
		POTION("item/splash_potion", 16, 16),
		TOTEM("item/totem_of_undying", 16, 16),
		WATER_BUCKET("item/water_bucket", 16, 16),
		LAVA_BUCKET("item/lava_bucket", 16, 16),
		FISHING_ROD("item/fishing_rod", 16, 16),
		FLINT_AND_STEEL("item/flint_and_steel", 16, 16),

		STONE("block/stone", 16, 16),
		COBBLESTONE("block/cobblestone", 16, 16),
		COAL_ORE("block/coal_ore", 16, 16),
		IRON_ORE("block/iron_ore", 16, 16),
		GOLD_ORE("block/gold_ore", 16, 16),
		DIAMOND_ORE("block/diamond_ore", 16, 16),
		EMERALD_ORE("block/emerald_ore", 16, 16),
		LAPIS_ORE("block/lapis_ore", 16, 16),
		REDSTONE_ORE("block/redstone_ore", 16, 16),
		COPPER_ORE("block/copper_ore", 16, 16),
		ANCIENT_DEBRIS("block/ancient_debris_side", 16, 16),
		ANCIENT_DEBRIS_TOP("block/ancient_debris_top", 16, 16),
		OAK_LOG("block/oak_log", 16, 16),
		OAK_LOG_TOP("block/oak_log_top", 16, 16),
		SPRUCE_LOG("block/spruce_log", 16, 16),
		SPRUCE_LOG_TOP("block/spruce_log_top", 16, 16),
		BIRCH_LOG("block/birch_log", 16, 16),
		BIRCH_LOG_TOP("block/birch_log_top", 16, 16),
		JUNGLE_LOG("block/jungle_log", 16, 16),
		JUNGLE_LOG_TOP("block/jungle_log_top", 16, 16),
		ACACIA_LOG("block/acacia_log", 16, 16),
		ACACIA_LOG_TOP("block/acacia_log_top", 16, 16),
		DARK_OAK_LOG("block/dark_oak_log", 16, 16),
		DARK_OAK_LOG_TOP("block/dark_oak_log_top", 16, 16),
		OAK_PLANKS("block/oak_planks", 16, 16),
		SPRUCE_PLANKS("block/spruce_planks", 16, 16),
		BIRCH_PLANKS("block/birch_planks", 16, 16),
		JUNGLE_PLANKS("block/jungle_planks", 16, 16),
		ACACIA_PLANKS("block/acacia_planks", 16, 16),
		DARK_OAK_PLANKS("block/dark_oak_planks", 16, 16),
		WHITE_WOOL("block/white_wool", 16, 16),
		RED_WOOL("block/red_wool", 16, 16),
		LIME_WOOL("block/lime_wool", 16, 16),
		LIGHT_BLUE_WOOL("block/light_blue_wool", 16, 16),
		YELLOW_WOOL("block/yellow_wool", 16, 16),
		END_STONE("block/end_stone", 16, 16),
		OBSIDIAN("block/obsidian", 16, 16),

		LEATHER_ARMOR("entity/equipment/humanoid/leather", 64, 32),
		LEATHER_LEGGINGS("entity/equipment/humanoid_leggings/leather", 64, 32),
		CHAINMAIL_ARMOR("entity/equipment/humanoid/chainmail", 64, 32),
		CHAINMAIL_LEGGINGS("entity/equipment/humanoid_leggings/chainmail", 64, 32),
		COPPER_ARMOR("entity/equipment/humanoid/copper", 64, 32),
		COPPER_LEGGINGS("entity/equipment/humanoid_leggings/copper", 64, 32),
		IRON_ARMOR("entity/equipment/humanoid/iron", 64, 32),
		IRON_LEGGINGS("entity/equipment/humanoid_leggings/iron", 64, 32),
		GOLD_ARMOR("entity/equipment/humanoid/gold", 64, 32),
		GOLD_LEGGINGS("entity/equipment/humanoid_leggings/gold", 64, 32),
		DIAMOND_ARMOR("entity/equipment/humanoid/diamond", 64, 32),
		DIAMOND_LEGGINGS("entity/equipment/humanoid_leggings/diamond", 64, 32),
		NETHERITE_ARMOR("entity/equipment/humanoid/netherite", 64, 32),
		NETHERITE_LEGGINGS("entity/equipment/humanoid_leggings/netherite", 64, 32),
		LEATHER_ARMOR_OVERLAY("entity/equipment/humanoid/leather_overlay", 64, 32),
		LEATHER_LEGGINGS_OVERLAY("entity/equipment/humanoid_leggings/leather_overlay", 64, 32);

		public final String path;
		public final int width;
		public final int height;

		Piece(String name, int width, int height) {
			this.path = "assets/minecraft/textures/" + name + ".png";
			this.width = width;
			this.height = height;
		}
	}

	/** Where a piece sits in the {@link Sheet}, in texture pixels. */
	public record Region(int x, int y, int w, int h) {
	}

	/** All pieces of one pack in one image; {@code fromPack} are the pack's own (the rest are vanilla's). */
	public record Sheet(BufferedImage image, Map<Piece, Region> regions, Set<Piece> fromPack) {
	}

	/** The files the pieces come from, before conversion (old names and sheets) and after. */
	private static final Pattern SOURCES = Pattern.compile("pack\\.mcmeta|assets/minecraft/textures/(gui/(widgets|icons)\\.png"
		+ "|gui/sprites/hud/.+\\.png|particle/(particles|critical_hit|enchanted_hit)\\.png"
		+ "|items?/((wood|wooden|stone|copper|iron|gold|golden|diamond|netherite)_(sword|pickaxe|axe)|ender_pearl|apple_golden|golden_apple|beef_cooked|cooked_beef|bow_standby|bow|bow_pulling_[0-2]"
		+ "|potion_overlay|potion_bottle_splash|splash_potion|totem|totem_of_undying|bucket_water|water_bucket|bucket_lava|lava_bucket"
		+ "|fishing_rod_uncast|fishing_rod|flint_and_steel)\\.png"
		+ "|blocks?/(stone|cobblestone|obsidian|end_stone|(coal|copper|iron|gold|diamond|emerald|lapis|redstone)_ore|ancient_debris_(side|top)"
		+ "|log_(oak|spruce|birch|jungle|acacia|big_oak)(_top)?|(oak|spruce|birch|jungle|acacia|dark_oak)_log(_top)?"
		+ "|planks_(oak|spruce|birch|jungle|acacia|big_oak)|(oak|spruce|birch|jungle|acacia|dark_oak)_planks"
		+ "|wool_colored_(white|red|lime|light_blue|yellow)|(white|red|lime|light_blue|yellow)_wool)\\.png"
		+ "|models/armor/(leather|chainmail|iron|gold|diamond|netherite)_layer_[12](_overlay)?\\.png"
		+ "|entity/equipment/humanoid(_leggings)?/(leather|leather_overlay|chainmail|copper|iron|gold|diamond|netherite)\\.png)");
	/** HD packs are kept at up to 4x (GUI scale 4 shows that much); beyond, only memory would grow. */
	private static final int MAX_SCALE = 4;

	private HudPreview() {
	}

	/** The pack's pieces in one sheet, vanilla's where the pack has none; null if the pack changes none of them. */
	public static @Nullable Sheet read(PackSource pack, VanillaAssets vanilla, Options options) throws IOException {
		PackSource only = new FilteredPackSource(pack, p -> SOURCES.matcher(PackTree.normalize(p)).matches());
		PackMeta meta = Converter.readMeta(only);
		if (meta == null) return null;
		PackTree tree = Converter.needsConversion(meta) ? Converter.convert(only, meta, vanilla, options).tree() : new PackTree(only);
		Map<Piece, BufferedImage> images = new EnumMap<>(Piece.class);
		Set<Piece> fromPack = EnumSet.noneOf(Piece.class);
		for (Piece piece : Piece.values()) {
			BufferedImage img = tree.image(piece.path);
			if (img != null) fromPack.add(piece);
			else img = Img.decode(vanilla.read(piece.path));
			if (img != null) images.put(piece, fit(piece, img));
		}
		return fromPack.isEmpty() ? null : sheet(images, fromPack);
	}

	/** The first frame of an animation strip, at most {@link #MAX_SCALE}x. */
	static BufferedImage fit(Piece piece, BufferedImage img) {
		int w = img.getWidth();
		int frame = Math.max(1, Math.round((float) piece.height * w / piece.width));
		if (img.getHeight() >= 2 * frame && img.getHeight() % frame == 0) img = Img.crop(img, 0, 0, w, frame);
		if (w > piece.width * MAX_SCALE) {
			int nw = piece.width * MAX_SCALE;
			img = Img.resize(img, nw, Math.max(1, Math.round((float) img.getHeight() * nw / w)));
		}
		return img;
	}

	/** Rows of pieces, tallest first, as wide as the widest piece. */
	static Sheet sheet(Map<Piece, BufferedImage> images, Set<Piece> fromPack) {
		List<Piece> order = new ArrayList<>(images.keySet());
		order.sort(Comparator.comparingInt((Piece p) -> images.get(p).getHeight()).reversed());
		int width = images.values().stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
		Map<Piece, Region> regions = new EnumMap<>(Piece.class);
		int x = 0, y = 0, row = 0;
		for (Piece piece : order) {
			BufferedImage img = images.get(piece);
			if (x + img.getWidth() > width) {
				x = 0;
				y += row;
				row = 0;
			}
			regions.put(piece, new Region(x, y, img.getWidth(), img.getHeight()));
			x += img.getWidth();
			row = Math.max(row, img.getHeight());
		}
		BufferedImage sheet = Img.create(width, y + row);
		regions.forEach((piece, r) -> Img.blit(images.get(piece), 0, 0, r.w(), r.h(), sheet, r.x(), r.y()));
		return new Sheet(sheet, regions, fromPack);
	}
}
