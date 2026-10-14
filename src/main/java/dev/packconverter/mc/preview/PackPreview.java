package dev.packconverter.mc.preview;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.core.HudPreview.Piece;
import dev.packconverter.core.HudPreview.Region;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One pack's preview, uploaded: the player in the pack's armor ({@link ArmorPreviewRenderer}), a row of blocks and
 * items (blocks and tools cycling like Lunar's preview), the crosshair with a crit and a sharpness
 * particle, and the HUD (armor, hearts with absorption, food, experience, hotbar with a totem in the off hand) laid
 * out like the game's. Drawn in the pack's tooltip; nothing runs when it isn't shown.
 */
public final class PackPreview {
	private static final int MODEL_WIDTH = 48;
	private static final int WIDTH = MODEL_WIDTH + 4 + 213;
	private static final int HEIGHT = 103;
	/** Below the preview when text follows. */
	private static final int GAP = 4;
	/** Splash Potion of Healing's colour, and the sharpness particle's (as the game tints it). */
	private static final int HEALING = 0xFFF82423;
	private static final int SHARPNESS = 0xFF45B8E6;
	/** Leather armor's undyed colour (DyedItemColor.LEATHER_COLOR). */
	private static final int LEATHER = 0xFFA06540;
	/** How long each block of a cycling slot shows, each material, each bow frame. */
	private static final long CYCLE_MS = 1200;
	private static final long MATERIAL_MS = 2000;
	private static final long BOW_FRAME_MS = 350;

	/** One armor set on the model: helmet, chestplate and boots texture, the leggings', leather's undyed overlays, tint. */
	private record ArmorSet(Piece armor, Piece leggings, @Nullable Piece armorOverlay, @Nullable Piece leggingsOverlay, int tint) {
		ArmorSet(Piece armor, Piece leggings) {
			this(armor, leggings, null, null, -1);
		}
	}

	/** A tier shown everywhere at once: armor on the model, sword, axe and pickaxe, its ore. */
	private record Material(String name, ArmorSet armor, Piece sword, Piece axe, Piece pickaxe, Piece ore) {
		boolean ownedBy(Set<Piece> fromPack) {
			return Stream.of(this.armor.armor(), this.armor.leggings(), this.sword, this.axe, this.pickaxe, this.ore).anyMatch(fromPack::contains);
		}
	}

	/** Wood goes with leather and stone with chainmail; the two share coal. */
	private static final Material[] MATERIALS = {
		new Material("wood", new ArmorSet(Piece.LEATHER_ARMOR, Piece.LEATHER_LEGGINGS, Piece.LEATHER_ARMOR_OVERLAY, Piece.LEATHER_LEGGINGS_OVERLAY, LEATHER),
			Piece.WOODEN_SWORD, Piece.WOODEN_AXE, Piece.WOODEN_PICKAXE, Piece.COAL_ORE),
		new Material("stone", new ArmorSet(Piece.CHAINMAIL_ARMOR, Piece.CHAINMAIL_LEGGINGS), Piece.STONE_SWORD, Piece.STONE_AXE, Piece.STONE_PICKAXE, Piece.COAL_ORE),
		new Material("copper", new ArmorSet(Piece.COPPER_ARMOR, Piece.COPPER_LEGGINGS), Piece.COPPER_SWORD, Piece.COPPER_AXE, Piece.COPPER_PICKAXE, Piece.COPPER_ORE),
		new Material("iron", new ArmorSet(Piece.IRON_ARMOR, Piece.IRON_LEGGINGS), Piece.IRON_SWORD, Piece.IRON_AXE, Piece.IRON_PICKAXE, Piece.IRON_ORE),
		new Material("gold", new ArmorSet(Piece.GOLD_ARMOR, Piece.GOLD_LEGGINGS), Piece.GOLDEN_SWORD, Piece.GOLDEN_AXE, Piece.GOLDEN_PICKAXE, Piece.GOLD_ORE),
		new Material("diamond", new ArmorSet(Piece.DIAMOND_ARMOR, Piece.DIAMOND_LEGGINGS), Piece.DIAMOND_SWORD, Piece.DIAMOND_AXE, Piece.DIAMOND_PICKAXE, Piece.DIAMOND_ORE),
		new Material("netherite", new ArmorSet(Piece.NETHERITE_ARMOR, Piece.NETHERITE_LEGGINGS), Piece.NETHERITE_SWORD, Piece.NETHERITE_AXE, Piece.NETHERITE_PICKAXE,
			Piece.ANCIENT_DEBRIS)};
	private static final int DIAMOND = 5;
	/**
	 * The row above the HUD, one column per slot (off hand first); slots with several cycle through them. null: the
	 * material's ore.
	 */
	private static final Piece[][] STRIP = {
		{Piece.STONE, Piece.COBBLESTONE},
		null,
		{Piece.LAPIS_ORE, Piece.REDSTONE_ORE, Piece.EMERALD_ORE},
		{Piece.OAK_LOG, Piece.SPRUCE_LOG, Piece.BIRCH_LOG, Piece.JUNGLE_LOG, Piece.ACACIA_LOG, Piece.DARK_OAK_LOG},
		{Piece.OAK_PLANKS, Piece.SPRUCE_PLANKS, Piece.BIRCH_PLANKS, Piece.JUNGLE_PLANKS, Piece.ACACIA_PLANKS, Piece.DARK_OAK_PLANKS},
		{Piece.WHITE_WOOL, Piece.RED_WOOL, Piece.LIME_WOOL, Piece.LIGHT_BLUE_WOOL, Piece.YELLOW_WOOL},
		{Piece.END_STONE, Piece.OBSIDIAN},
		{Piece.WATER_BUCKET, Piece.LAVA_BUCKET},
		{Piece.FISHING_ROD},
		{Piece.FLINT_AND_STEEL}};
	private static final Piece[] BOW = {Piece.BOW, Piece.BOW, Piece.BOW_PULLING_0, Piece.BOW_PULLING_1, Piece.BOW_PULLING_2, Piece.BOW_PULLING_2};
	/** The hotbar after the material's sword, axe and pickaxe; the bow (being drawn) goes between the steak and the potions. */
	private static final Piece[] HOTBAR = {Piece.PEARL, Piece.GOLDEN_APPLE, Piece.STEAK, Piece.POTION, Piece.POTION};
	/** Logs and ancient debris have their own top. */
	private static final Map<Piece, Piece> TOPS = new EnumMap<>(Piece.class);

	static {
		for (Piece piece : Piece.values()) {
			if (piece.name().endsWith("_TOP")) TOPS.put(Piece.valueOf(piece.name().substring(0, piece.name().length() - 4)), piece);
		}
	}

	private final Identifier texture;
	private final int textureWidth;
	private final int textureHeight;
	private final Map<Piece, Region> regions;
	/** The cycles cut down to what the pack has its own of (a 1.7 pack shows no copper or netherite). */
	private final Piece[][] strip;
	private final Material[] materials;

	PackPreview(Identifier texture, int textureWidth, int textureHeight, Map<Piece, Region> regions, Set<Piece> fromPack) {
		this.texture = texture;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.regions = regions;
		this.strip = Arrays.stream(STRIP).map(cycle -> cycle == null ? null : own(cycle, fromPack)).toArray(Piece[][]::new);
		// one material if the settings say so, else every one the pack has
		String chosen = PackConverterConfig.get().previewMaterial;
		Material[] materials = Arrays.stream(MATERIALS).filter(m -> m.name().equals(chosen)).toArray(Material[]::new);
		if (materials.length == 0) materials = Arrays.stream(MATERIALS).filter(m -> m.ownedBy(fromPack)).toArray(Material[]::new);
		this.materials = materials.length > 0 ? materials : new Material[]{MATERIALS[DIAMOND]};
	}

	/** The pieces of a cycle the pack has its own of; if none, vanilla's first. */
	private static Piece[] own(Piece[] cycle, Set<Piece> fromPack) {
		Piece[] own = Arrays.stream(cycle).filter(fromPack::contains).toArray(Piece[]::new);
		return own.length > 0 ? own : new Piece[]{cycle[0]};
	}

	void release() {
		Minecraft.getInstance().getTextureManager().release(this.texture);
	}

	/** For the tooltip: the preview, with a gap below if text follows. */
	public ClientTooltipComponent component(boolean textBelow) {
		return new ClientTooltipComponent() {
			@Override
			public int getHeight(Font font) {
				return textBelow ? HEIGHT + GAP : HEIGHT + 2; // the tooltip takes 2 off its last line
			}

			@Override
			public int getWidth(Font font) {
				return WIDTH;
			}

			@Override
			public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
				PackPreview.this.draw(graphics, x, y);
			}
		};
	}

	private void draw(GuiGraphicsExtractor graphics, int x, int y) {
		long now = Util.getMillis();
		// one material after another, on the player (turning once every 10 seconds), the ore and the tools
		Material material = this.materials[(int) (now / MATERIAL_MS % this.materials.length)];
		ArmorSet set = material.armor();
		ArmorPreviewRenderer.Uv armor = this.uv(set.armor()), leggings = this.uv(set.leggings());
		if (armor != null && leggings != null) {
			graphics.guiRenderState.addPicturesInPictureState(new ArmorPreviewRenderer.State(this.texture, armor, leggings,
				this.uv(set.armorOverlay()), this.uv(set.leggingsOverlay()), set.tint(),
				25 + (now % 10_000) * 0.036F, 0, x, y + 2, x + MODEL_WIDTH, y + HEIGHT, 46));
		}

		// the game's HUD offsets, from the hotbar's top left corner (the off hand slot sticks out left of it)
		int left = x + MODEL_WIDTH + 4 + 30;
		int top = y + 79;
		for (int i = 0; i < this.strip.length; i++) {
			Piece shown = this.strip[i] == null ? material.ore() : this.strip[i][(int) (now / CYCLE_MS % this.strip[i].length)];
			int ix = i == 0 ? left - 26 : left + 3 + (i - 1) * 20;
			if (shown.path.contains("/block/")) this.block(graphics, shown, TOPS.getOrDefault(shown, shown), ix, y + 1);
			else this.draw(graphics, shown, ix, y + 1, 16, 16);
		}

		int cx = left + 91, cy = y + 29;
		this.draw(graphics, RenderPipelines.CROSSHAIR, Piece.CROSSHAIR, cx - 8, cy - 7, 15, 15, -1);
		this.draw(graphics, RenderPipelines.GUI_TEXTURED, Piece.CRIT, cx - 34, cy - 8, 16, 16, -1);
		this.draw(graphics, RenderPipelines.GUI_TEXTURED, Piece.SHARPNESS, cx + 18, cy - 8, 16, 16, SHARPNESS);

		// 15 of 20 health, 2 absorption hearts above, 17 of 20 food, full armor
		for (int i = 0; i < 10; i++) {
			int hx = left + i * 8, fx = left + 182 - i * 8 - 9;
			this.draw(graphics, Piece.ARMOR, hx, top - 37, 9, 9);
			if (i < 2) {
				this.draw(graphics, Piece.HEART_CONTAINER, hx, top - 27, 9, 9);
				this.draw(graphics, Piece.GOLD_HEART, hx, top - 27, 9, 9);
			}
			this.draw(graphics, Piece.HEART_CONTAINER, hx, top - 17, 9, 9);
			if (i < 7) this.draw(graphics, Piece.HEART, hx, top - 17, 9, 9);
			else if (i == 7) this.draw(graphics, Piece.HEART_HALF, hx, top - 17, 9, 9);
			this.draw(graphics, Piece.FOOD_EMPTY, fx, top - 17, 9, 9);
			if (i < 8) this.draw(graphics, Piece.FOOD, fx, top - 17, 9, 9);
			else if (i == 8) this.draw(graphics, Piece.FOOD_HALF, fx, top - 17, 9, 9);
		}
		this.draw(graphics, Piece.XP_BACKGROUND, left, top - 7, 182, 5);
		this.drawPart(graphics, Piece.XP, left, top - 7, 110, 182, 5);

		this.draw(graphics, Piece.HOTBAR, left, top, 182, 22);
		this.draw(graphics, Piece.SELECTION, left - 1, top - 1, 24, 23);
		this.draw(graphics, Piece.OFFHAND, left - 29, top - 1, 29, 24);
		this.draw(graphics, Piece.TOTEM, left - 26, top + 3, 16, 16);
		Piece[] hotbar = {material.sword(), material.axe(), material.pickaxe(), HOTBAR[0], HOTBAR[1], HOTBAR[2],
			BOW[(int) (now / BOW_FRAME_MS % BOW.length)], HOTBAR[3], HOTBAR[4]};
		for (int i = 0; i < hotbar.length; i++) {
			int ix = left + 3 + i * 20, iy = top + 3;
			Piece item = hotbar[i];
			if (item == Piece.POTION) this.draw(graphics, RenderPipelines.GUI_TEXTURED, Piece.POTION_OVERLAY, ix, iy, 16, 16, HEALING);
			this.draw(graphics, item, ix, iy, 16, 16);
		}
	}

	/** Where an armor texture sits in the sheet, for the model. */
	private ArmorPreviewRenderer.@Nullable Uv uv(@Nullable Piece piece) {
		Region r = piece == null ? null : this.regions.get(piece);
		if (r == null) return null;
		return new ArmorPreviewRenderer.Uv((float) r.x() / this.textureWidth, (float) r.y() / this.textureHeight,
			(float) (r.x() + r.w()) / this.textureWidth, (float) (r.y() + r.h()) / this.textureHeight);
	}

	/** A full block as the game shows it in a slot: three faces of a cube seen from above, the sides shaded. */
	private void block(GuiGraphicsExtractor graphics, Piece side, Piece top, int x, int y) {
		Region s = this.regions.get(side), t = this.regions.get(top);
		if (s == null || t == null) return;
		// a 10 px cube (the GUI's 0.625 scale) turned 225° and tilted 30°: corners at ±7.07 across, ±3.54 / 4.33 / 7.87 down
		float cx = x + 8, cy = y + 8;
		this.face(graphics, t, cx, cy - 7.87f, 7.07f, 3.54f, -7.07f, 3.54f, 0xFFFFFFFF);
		this.face(graphics, s, cx - 7.07f, cy - 4.33f, 7.07f, 3.54f, 0, 8.66f, 0xFFCCCCCC);
		this.face(graphics, s, cx, cy - 0.79f, 7.07f, -3.54f, 0, 8.66f, 0xFF999999);
	}

	/** The texture on the parallelogram from (ox, oy) along (ax, ay) for its width and (bx, by) for its height. */
	private void face(GuiGraphicsExtractor graphics, Region r, float ox, float oy, float ax, float ay, float bx, float by, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(ox, oy);
		graphics.pose().mul(new Matrix3x2f(ax / 16, ay / 16, bx / 16, by / 16, 0, 0));
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, 0, 0, r.x(), r.y(), 16, 16, r.w(), r.h(), this.textureWidth, this.textureHeight, color);
		graphics.pose().popMatrix();
	}

	private void draw(GuiGraphicsExtractor graphics, Piece piece, int x, int y, int w, int h) {
		this.draw(graphics, RenderPipelines.GUI_TEXTURED, piece, x, y, w, h, -1);
	}

	private void draw(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Piece piece, int x, int y, int w, int h, int color) {
		Region r = this.regions.get(piece);
		if (r == null) return;
		graphics.blit(pipeline, this.texture, x, y, r.x(), r.y(), w, h, r.w(), r.h(), this.textureWidth, this.textureHeight, color);
	}

	/** The left {@code shown} of {@code full} GUI pixels (a bar's progress). */
	private void drawPart(GuiGraphicsExtractor graphics, Piece piece, int x, int y, int shown, int full, int h) {
		Region r = this.regions.get(piece);
		if (r == null) return;
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, x, y, r.x(), r.y(), shown, h, r.w() * shown / full, r.h(),
			this.textureWidth, this.textureHeight, -1);
	}
}
