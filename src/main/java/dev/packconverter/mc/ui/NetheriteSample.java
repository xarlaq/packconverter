package dev.packconverter.mc.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.packconverter.core.Converter;
import dev.packconverter.core.PackMeta;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.source.FilteredPackSource;
import dev.packconverter.core.source.PackSource;
import dev.packconverter.core.steps.NetheriteStep;
import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.preview.ArmorPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The diamond items and armor a netherite colour is tried on in {@link NetheriteColorScreen} (a pack's own, as
 * converted, or vanilla's), recoloured with {@link NetheriteStep#recolor} whenever the colour changes. Items the pack
 * has no diamond version of show vanilla netherite (what the game would show). One small texture, released with
 * the screen.
 */
final class NetheriteSample implements AutoCloseable {
	static final List<String> ITEMS = NetheriteStep.ITEMS;
	private static final String ITEM = "assets/minecraft/textures/item/";
	private static final String EQUIPMENT = "assets/minecraft/textures/entity/equipment/";
	private static final Pattern SOURCES = Pattern.compile("pack\\.mcmeta|assets/minecraft/textures/(items?/diamond_(sword|pickaxe|axe|shovel"
		+ "|hoe|helmet|chestplate|leggings|boots)\\.png|models/armor/diamond_layer_[12]\\.png|entity/equipment/humanoid(_leggings)?/diamond\\.png)");
	private static final int CELL = 32;
	private static final int ARMOR_WIDTH = 128, ARMOR_HEIGHT = 64;
	private static int textures;

	/** Diamond images by item name or armor layer (missing: the pack has none), and vanilla netherite's. */
	record Images(Map<String, BufferedImage> diamond, Map<String, BufferedImage> netherite) {
	}

	/** Reads the diamond items and armor of a pack (or vanilla's, for null). Not on the render thread. */
	static Images read(@Nullable Path pack) throws IOException {
		Map<String, BufferedImage> diamond = new HashMap<>(), netherite = new HashMap<>();
		PackTree tree = null;
		if (pack != null) {
			try (PackSource source = PackSource.open(pack)) {
				PackSource only = new FilteredPackSource(source, p -> SOURCES.matcher(PackTree.normalize(p)).matches());
				PackMeta meta = Converter.readMeta(only);
				tree = meta != null && Converter.needsConversion(meta) ? Converter.convert(only, meta, LegacyPacks.VANILLA).tree() : new PackTree(only);
				for (String key : keys()) {
					BufferedImage img = tree.image(diamondPath(key));
					if (img != null) diamond.put(key, img);
				}
			}
		} else {
			for (String key : keys()) {
				BufferedImage img = Img.decode(LegacyPacks.VANILLA.read(diamondPath(key)));
				if (img != null) diamond.put(key, img);
			}
		}
		for (String key : keys()) {
			BufferedImage img = Img.decode(LegacyPacks.VANILLA.read(diamondPath(key).replace("diamond", "netherite")));
			if (img != null) netherite.put(key, img);
		}
		return new Images(diamond, netherite);
	}

	private static List<String> keys() {
		return java.util.stream.Stream.concat(ITEMS.stream(), NetheriteStep.ARMOR.stream()).toList();
	}

	private static String diamondPath(String key) {
		return NetheriteStep.ARMOR.contains(key) ? EQUIPMENT + key + "/diamond.png" : ITEM + "diamond_" + key + ".png";
	}

	private final Images images;
	/** The main hue of all the diamond art, as the conversion finds it. */
	private final float hue;
	private final BufferedImage sheet;
	private final NativeImage pixels;
	private final DynamicTexture texture;
	private final Identifier id;

	/** Uploads the sample (render thread). Rows: diamond items, netherite items, then the two netherite armor textures. */
	NetheriteSample(Images images) {
		this.images = images;
		this.hue = NetheriteStep.mainHue(images.diamond().values());
		this.sheet = Img.create(Math.max(ITEMS.size() * CELL, 2 * ARMOR_WIDTH), 2 * CELL + ARMOR_HEIGHT);
		for (int i = 0; i < ITEMS.size(); i++) {
			BufferedImage img = images.diamond().getOrDefault(ITEMS.get(i), images.netherite().get(ITEMS.get(i)));
			if (img != null) put(img, i * CELL, 0, CELL, CELL);
		}
		this.pixels = new NativeImage(this.sheet.getWidth(), this.sheet.getHeight(), true);
		this.id = Identifier.fromNamespaceAndPath("packconverter", "netherite_sample/" + textures++);
		this.texture = new DynamicTexture(() -> "Netherite sample", this.pixels);
		Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
	}

	/** Recolours the netherite row and armor to {@code rgb} and uploads them. */
	void recolor(int rgb) {
		for (int i = 0; i < ITEMS.size(); i++) this.put(this.netherite(ITEMS.get(i), rgb), i * CELL, CELL, CELL, CELL);
		for (int i = 0; i < NetheriteStep.ARMOR.size(); i++) {
			this.put(this.netherite(NetheriteStep.ARMOR.get(i), rgb), i * ARMOR_WIDTH, 2 * CELL, ARMOR_WIDTH, ARMOR_HEIGHT);
		}
		int w = this.sheet.getWidth();
		int[] px = Img.pixels(this.sheet);
		for (int y = 0; y < this.sheet.getHeight(); y++) {
			for (int x = 0; x < w; x++) this.pixels.setPixel(x, y, px[y * w + x]);
		}
		this.texture.upload();
	}

	/** The pack's diamond version recoloured, or vanilla netherite if the pack has none (left as it is, like the game). */
	private @Nullable BufferedImage netherite(String key, int rgb) {
		BufferedImage diamond = this.images.diamond().get(key);
		return diamond != null ? NetheriteStep.recolor(diamond, rgb, this.hue) : this.images.netherite().get(key);
	}

	private void put(@Nullable BufferedImage img, int x, int y, int w, int h) {
		int[] px = Img.pixels(this.sheet);
		for (int row = y; row < y + h; row++) java.util.Arrays.fill(px, row * this.sheet.getWidth() + x, row * this.sheet.getWidth() + x + w, 0);
		if (img == null) return;
		BufferedImage fitted = Img.resize(img.getHeight() > img.getWidth() * h / w ? Img.crop(img, 0, 0, img.getWidth(), img.getWidth() * h / w) : img, w, h);
		Img.blit(fitted, 0, 0, w, h, this.sheet, x, y);
	}

	/** Draws item {@code index} (diamond or netherite row) at x, y, size by size. */
	void drawItem(GuiGraphicsExtractor graphics, int index, boolean netherite, int x, int y, int size) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.id, x, y, index * CELL, netherite ? CELL : 0, size, size, CELL, CELL,
			this.sheet.getWidth(), this.sheet.getHeight());
	}

	Identifier texture() {
		return this.id;
	}

	/** Where netherite armor layer {@code index} (0 worn, 1 leggings) sits, for the model. */
	ArmorPreviewRenderer.Uv armor(int index) {
		float w = this.sheet.getWidth(), h = this.sheet.getHeight();
		return new ArmorPreviewRenderer.Uv(index * ARMOR_WIDTH / w, 2 * CELL / h, (index + 1) * ARMOR_WIDTH / w, (2 * CELL + ARMOR_HEIGHT) / h);
	}

	@Override
	public void close() {
		Minecraft.getInstance().getTextureManager().release(this.id);
	}
}
