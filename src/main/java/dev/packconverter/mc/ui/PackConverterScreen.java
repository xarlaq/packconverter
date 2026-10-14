package dev.packconverter.mc.ui;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.NativeImage;
import dev.packconverter.PackConverterClient;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.core.Converter;
import dev.packconverter.core.Exporter;
import dev.packconverter.core.Options;
import dev.packconverter.core.PackFormats;
import dev.packconverter.core.PackMeta;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.source.PackSource;
import dev.packconverter.mc.LegacyPacks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Browses the resource pack folder (subfolders included, like pack manager mods allow) and saves converted copies of
 * old packs as normal 26.3 packs, for clients without this mod or to share. Pack details and icons load in the
 * background so big folders open instantly; the search box looks through every folder.
 */
public final class PackConverterScreen extends Screen {
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Pack Converter export");
		t.setDaemon(true);
		return t;
	});
	private static final Identifier FOLDER = Identifier.fromNamespaceAndPath("packconverter", "folder");
	private static final Identifier FOLDER_UP = Identifier.fromNamespaceAndPath("packconverter", "folder_up");
	private static final Identifier UNKNOWN_PACK = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");
	/** Pack icons are shown at 20 px: anything bigger than this is shrunk on reading. */
	private static final int ICON_SIZE = 64;
	/** Files that mean a pack has diamond items or armor, and netherite ones (any version's names). */
	private static final Pattern DIAMOND = Pattern.compile("assets/minecraft/textures/(items?/diamond_(sword|pickaxe|axe|shovel|hoe|helmet"
		+ "|chestplate|leggings|boots)|models/armor/diamond_layer_[12]|entity/equipment/humanoid(_leggings)?/diamond)\\.png");
	private static final Pattern NETHERITE = Pattern.compile("assets/minecraft/textures/(item/netherite_\\w+|models/armor/netherite_layer_[12]"
		+ "|entity/equipment/humanoid(_leggings)?/netherite)\\.png");

	private final Screen parent;
	private final Path root;
	private Path folder;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private @Nullable PackList list;
	private @Nullable StringWidget location;
	private @Nullable EditBox search;
	private @Nullable Button convertButton;
	private @Nullable Button convertAllButton;
	/** Every pack under resourcepacks, for searches (listed on the first one, again after saving). */
	private @Nullable List<Path> allPacks;
	/** Icons uploaded for the rows shown, released when other rows replace them and when the screen closes. */
	private final List<Identifier> icons = new ArrayList<>();
	private static int iconCount;
	/** Set while "Convert folder" runs; pressing the button again asks it to stop after the current pack. */
	private volatile boolean batch, stopBatch;
	private Component status = Component.empty();
	private volatile boolean busy;

	public PackConverterScreen(Screen parent) {
		super(Component.translatable("packconverter.screen.title"));
		this.parent = parent;
		this.root = Minecraft.getInstance().getResourcePackDirectory().toAbsolutePath().normalize();
		this.folder = root;
	}

	@Override
	protected void init() {
		this.layout.setHeaderHeight(58);
		this.layout.setFooterHeight(56);
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		this.location = header.addChild(new StringWidget(Component.empty(), this.font));
		String query = this.search != null ? this.search.getValue() : "";
		this.search = header.addChild(new EditBox(this.font, 200, 18, Component.translatable("packconverter.screen.search")));
		this.search.setHint(Component.translatable("packconverter.screen.search").withStyle(ChatFormatting.DARK_GRAY));
		this.search.setValue(query);
		this.search.setResponder(text -> this.refresh());
		this.list = this.layout.addToContents(new PackList(this.minecraft));
		LinearLayout footer = this.layout.addToFooter(LinearLayout.vertical().spacing(6));
		footer.defaultCellSetting().alignHorizontallyCenter();
		LinearLayout buttons = footer.addChild(LinearLayout.horizontal().spacing(6));
		this.convertButton = buttons.addChild(Button.builder(Component.translatable("packconverter.screen.convert"), b -> this.convertSelected())
			.width(120).tooltip(Tooltip.create(Component.translatable("packconverter.screen.convert.tooltip"))).build());
		this.convertAllButton = buttons.addChild(Button.builder(Component.translatable("packconverter.screen.convert_all"), b -> this.convertAll())
			.width(120).tooltip(Tooltip.create(Component.translatable("packconverter.screen.convert_all.tooltip"))).build());
		buttons.addChild(Button.builder(Component.translatable("packconverter.screen.open_export"), b -> this.openExportFolder()).width(120).build());
		LinearLayout bottom = footer.addChild(LinearLayout.horizontal().spacing(6));
		bottom.addChild(Button.builder(Component.translatable("packconverter.screen.settings"),
			b -> this.minecraft.gui.setScreen(new PackConverterSettingsScreen(this))).width(100).build());
		bottom.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(150).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.refresh();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.list != null) this.list.updateSize(this.width, this.layout);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.releaseIcons();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// status line just above the footer buttons
		graphics.centeredText(this.font, this.status, this.width / 2, this.height - this.layout.getFooterHeight() - 2, 0xFFE0E0E0);
	}

	/** The folder shown, or the search's results from every folder. */
	private void refresh() {
		String query = this.search != null ? this.search.getValue().strip().toLowerCase(Locale.ROOT) : "";
		if (query.isEmpty()) this.showFolder(this.folder);
		else this.showSearch(query);
	}

	private void showFolder(Path dir) {
		this.folder = dir;
		if (this.location != null) {
			String rel = this.root.relativize(dir).toString().replace('\\', '/');
			this.location.setMessage(Component.literal("resourcepacks/" + rel).withStyle(ChatFormatting.GRAY));
		}
		List<Item> items = new ArrayList<>();
		if (!dir.equals(this.root)) items.add(new Item(dir.getParent(), Kind.UP, ".."));
		List<Path> children = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path p : stream) children.add(p);
		} catch (IOException e) {
			PackConverterClient.LOGGER.warn("Could not list {}", dir, e);
		}
		children.sort(Comparator.comparing((Path p) -> !Files.isDirectory(p) || Files.exists(p.resolve("pack.mcmeta")))
			.thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
		for (Path p : children) {
			String name = p.getFileName().toString();
			if (Files.isDirectory(p)) {
				boolean pack = Files.exists(p.resolve("pack.mcmeta"));
				items.add(new Item(p, pack ? Kind.PACK : Kind.FOLDER, pack ? name : name + "/"));
			} else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
				items.add(new Item(p, Kind.PACK, name));
			}
		}
		this.show(items);
	}

	/** Packs anywhere under resourcepacks whose path (folder and name) contains the query. */
	private void showSearch(String query) {
		if (this.location != null) this.location.setMessage(Component.translatable("packconverter.screen.search_all").withStyle(ChatFormatting.GRAY));
		if (this.allPacks == null) this.allPacks = this.listAllPacks();
		List<Item> items = new ArrayList<>();
		for (Path p : this.allPacks) {
			String rel = this.root.relativize(p).toString().replace('\\', '/');
			if (rel.toLowerCase(Locale.ROOT).contains(query)) items.add(new Item(p, Kind.PACK, rel));
		}
		this.show(items);
	}

	private List<Path> listAllPacks() {
		List<Path> packs = new ArrayList<>();
		this.collectPacks(this.root, 0, packs);
		packs.sort(Comparator.comparing(p -> this.root.relativize(p).toString().toLowerCase(Locale.ROOT)));
		return packs;
	}

	private void collectPacks(Path dir, int depth, List<Path> out) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path p : stream) {
				if (Files.isDirectory(p)) {
					if (Files.exists(p.resolve("pack.mcmeta"))) out.add(p);
					else if (depth < 8 && !Files.isSymbolicLink(p)) this.collectPacks(p, depth + 1, out);
				} else if (p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
					out.add(p);
				}
			}
		} catch (IOException e) {
			PackConverterClient.LOGGER.debug("Could not list {}", dir, e);
		}
	}

	private void show(List<Item> items) {
		this.releaseIcons();
		if (this.list != null) this.list.show(items);
		this.updateButtons();
		// read every pack's format and icon in the background
		for (Item item : items) {
			if (item.kind == Kind.PACK) WORKER.execute(item::inspect);
		}
	}

	private void releaseIcons() {
		for (Identifier id : this.icons) this.minecraft.getTextureManager().release(id);
		this.icons.clear();
	}

	/** Selects the row of a pack shown, by file name (for tests and keyboard-less automation). */
	public boolean select(String fileName) {
		if (this.list == null) return false;
		for (PackList.Row row : this.list.children()) {
			if (row.item.kind != Kind.UP && row.item.path.getFileName().toString().equals(fileName)) {
				this.list.setSelected(row);
				this.updateButtons();
				return true;
			}
		}
		return false;
	}

	/** True while nothing is being saved and every pack's details are read. */
	public boolean idle() {
		if (this.busy || this.list == null) return false;
		for (PackList.Row row : this.list.children()) if (row.item.kind == Kind.PACK && !row.item.inspected) return false;
		return true;
	}

	private void updateButtons() {
		Item selected = this.list != null ? this.list.selectedItem() : null;
		if (this.convertButton != null) this.convertButton.active = !this.busy && selected != null && selected.convertible();
		if (this.convertAllButton != null) {
			this.convertAllButton.setMessage(Component.translatable(this.batch ? "packconverter.screen.stop" : "packconverter.screen.convert_all"));
			this.convertAllButton.active = this.batch || !this.busy && this.list != null
				&& this.list.children().stream().anyMatch(row -> row.item.convertible());
		}
	}

	@Override
	public void tick() {
		this.updateButtons();
	}

	private void openExportFolder() {
		Path dir = PackConverterConfig.get().exportPath();
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		Blaze3D.openPath(dir);
	}

	/** Where a pack's converted copy goes: straight into the export folder, whatever folder the pack is in. */
	private static Path output(Item item) {
		String name = item.path.getFileName().toString();
		String base = name.toLowerCase(Locale.ROOT).endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
		return PackConverterConfig.get().exportPath().resolve(base + " (26.3).zip");
	}

	/**
	 * Asks, if any of the packs has diamond items but no netherite ones, whether to make netherite from diamond (in
	 * the colour from the settings, or one picked now with a preview of the first such pack), then goes on.
	 */
	private void askNetherite(List<Item> packs, Consumer<Options> then) {
		PackConverterConfig config = PackConverterConfig.get();
		List<Item> asking = packs.stream().filter(item -> item.netheriteChoice).toList();
		if (asking.isEmpty()) {
			then.accept(config.conversionOptions());
			return;
		}
		int color = config.netheriteRgb();
		Component message = asking.size() == 1 && packs.size() == 1
			? Component.translatable("packconverter.netherite.ask", asking.getFirst().label)
			: Component.translatable("packconverter.netherite.ask_all", asking.size(), packs.size());
		this.minecraft.gui.setScreen(new PopupScreen.Builder(this, Component.translatable("packconverter.netherite.ask.title"))
			.setWidth(360)
			.addMessage(message)
			.addButton(Component.translatable("packconverter.netherite.ask.yes"), popup -> {
				popup.onClose();
				then.accept(new Options(true, color));
			})
			.addButton(Component.translatable("packconverter.netherite.ask.pick"), popup -> this.minecraft.gui.setScreen(new NetheriteColorScreen(this,
				asking.getFirst().path, color, Component.translatable("packconverter.netherite.save"), picked -> then.accept(new Options(true, picked)))))
			.addButton(Component.translatable("packconverter.netherite.ask.no"), popup -> {
				popup.onClose();
				then.accept(new Options(false, color));
			})
			.addButton(CommonComponents.GUI_CANCEL, PopupScreen::onClose)
			.build());
	}

	/** Every old pack shown, one after the other. Pressed again while running: stop after the current one. */
	private void convertAll() {
		if (this.batch) {
			this.stopBatch = true;
			return;
		}
		if (this.busy || this.list == null) return;
		List<Item> packs = this.list.children().stream().map(row -> row.item).filter(Item::convertible).toList();
		if (packs.isEmpty()) return;
		this.askNetherite(packs, options -> this.convertAll(packs, options));
	}

	private void convertAll(List<Item> packs, Options options) {
		long existing = packs.stream().filter(item -> Files.exists(output(item))).count();
		if (existing == 0) {
			this.exportAll(packs, true, options);
			return;
		}
		this.minecraft.gui.setScreen(new PopupScreen.Builder(this, Component.translatable("packconverter.screen.exists_all.title"))
			.setWidth(300)
			.addMessage(Component.translatable("packconverter.screen.exists_all", existing, packs.size()))
			.addButton(Component.translatable("packconverter.screen.exists_all.replace"), popup -> {
				popup.onClose();
				this.exportAll(packs, true, options);
			})
			.addButton(Component.translatable("packconverter.screen.exists_all.skip"), popup -> {
				popup.onClose();
				this.exportAll(packs, false, options);
			})
			.addButton(CommonComponents.GUI_CANCEL, PopupScreen::onClose)
			.build());
	}

	private void exportAll(List<Item> packs, boolean replace, Options options) {
		this.busy = true;
		this.batch = true;
		this.stopBatch = false;
		WORKER.execute(() -> {
			int saved = 0, skipped = 0, failed = 0, n = 0;
			long start = System.nanoTime();
			for (Item item : packs) {
				if (this.stopBatch) break;
				n++;
				String name = item.path.getFileName().toString();
				Path out = output(item);
				if (!replace && Files.exists(out)) {
					skipped++;
					continue;
				}
				Component progress = Component.translatable("packconverter.screen.converting_n", n, packs.size(), name).withStyle(ChatFormatting.YELLOW);
				Minecraft.getInstance().execute(() -> this.status = progress);
				try (PackSource source = PackSource.open(item.path)) {
					Exporter.export(LegacyPacks.cache(), name, item.path, source, item.meta, LegacyPacks.VANILLA, options, out);
					saved++;
				} catch (Exception e) {
					PackConverterClient.LOGGER.error("Could not export {}", item.path, e);
					failed++;
				}
			}
			long seconds = (System.nanoTime() - start) / 1_000_000_000L;
			MutableComponent summary = Component.translatable("packconverter.screen.saved_all", saved, skipped, failed, seconds)
				.withStyle(failed > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN);
			if (this.stopBatch) summary.append(Component.translatable("packconverter.screen.stopped"));
			int savedCount = saved;
			Minecraft.getInstance().execute(() -> {
				this.status = summary;
				this.busy = false;
				this.batch = false;
				this.allPacks = null;
				this.refresh();
				if (savedCount > 0) announceAll(PackConverterConfig.get().exportPath(), savedCount);
			});
		});
	}

	/** One chat line for a folder's conversion, with a link to the export folder. */
	private static void announceAll(Path dir, int count) {
		Component link = Component.translatable("packconverter.chat.open_folder").withStyle(style -> style
			.withColor(ChatFormatting.AQUA).withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenFile(dir))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(dir.toString()))));
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.translatable("packconverter.chat.saved_all", count, link));
	}

	private void convertSelected() {
		Item item = this.list != null ? this.list.selectedItem() : null;
		if (item == null || !item.convertible() || this.busy) return;
		this.askNetherite(List.of(item), options -> this.convertSelected(item, options));
	}

	private void convertSelected(Item item, Options options) {
		String name = item.path.getFileName().toString();
		Path out = output(item);
		if (!Files.exists(out)) {
			this.export(item, name, out, options);
			return;
		}
		// already converted (or a same-named pack from another folder): replace it, save a copy, or cancel
		this.minecraft.gui.setScreen(ExistingExportPopup.create(this, out, target -> this.export(item, name, target, options)));
	}

	private void export(Item item, String name, Path out, Options options) {
		this.busy = true;
		this.status = Component.translatable("packconverter.screen.converting", name).withStyle(ChatFormatting.YELLOW);
		PackMeta meta = item.meta;
		WORKER.execute(() -> {
			Component result;
			try (PackSource source = PackSource.open(item.path)) {
				long t = System.nanoTime();
				Exporter.export(LegacyPacks.cache(), name, item.path, source, meta, LegacyPacks.VANILLA, options, out);
				long ms = (System.nanoTime() - t) / 1_000_000;
				result = Component.translatable("packconverter.screen.saved", out.getFileName().toString(), ms).withStyle(ChatFormatting.GREEN);
				Minecraft.getInstance().execute(() -> announce(out));
			} catch (Exception e) {
				PackConverterClient.LOGGER.error("Could not export {}", item.path, e);
				result = Component.translatable("packconverter.screen.failed", name, String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED);
			}
			Component done = result;
			Minecraft.getInstance().execute(() -> {
				this.status = done;
				this.busy = false;
				// the export may have created a folder here (resourcepacks/converted by default)
				this.allPacks = null;
				this.refresh();
				this.select(name);
			});
		});
	}

	/** A chat line with a clickable link to the folder holding the saved pack. */
	private static void announce(Path out) {
		Path dir = out.getParent();
		Component link = Component.translatable("packconverter.chat.open_folder").withStyle(style -> style
			.withColor(ChatFormatting.AQUA).withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenFile(dir))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(dir.toString()))));
		Component message = Component.translatable("packconverter.chat.saved", out.getFileName().toString(), link);
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
	}

	private enum Kind {UP, FOLDER, PACK}

	/** A row: parent folder, folder, or pack (zip or folder with pack.mcmeta). */
	private static final class Item {
		final Path path;
		final Kind kind;
		final String label;
		volatile @Nullable PackMeta meta;
		volatile Component detail = Component.empty();
		volatile boolean inspected;
		/** Has diamond items or armor but no netherite (asked about before saving). */
		volatile boolean netheriteChoice;
		/** pack.png, read with the details; uploaded when first drawn. */
		volatile @Nullable BufferedImage iconImage;
		@Nullable Identifier icon;

		Item(Path path, Kind kind, String label) {
			this.path = path;
			this.kind = kind;
			this.label = label;
			if (kind == Kind.PACK) this.detail = Component.translatable("packconverter.screen.reading").withStyle(ChatFormatting.DARK_GRAY);
		}

		boolean convertible() {
			return this.kind == Kind.PACK && this.inspected && Converter.needsConversion(this.meta);
		}

		void inspect() {
			PackMeta m = null;
			try (PackSource source = PackSource.open(this.path)) {
				m = Converter.readMeta(source);
				boolean diamond = false, netherite = false;
				String iconPath = null;
				for (String p : source.paths()) {
					String n = PackTree.normalize(p);
					if (n.equals("pack.png")) iconPath = p;
					else if (DIAMOND.matcher(n).matches()) diamond = true;
					else if (NETHERITE.matcher(n).matches()) netherite = true;
				}
				this.netheriteChoice = diamond && !netherite;
				if (iconPath != null) {
					BufferedImage icon = Img.decode(source.read(iconPath));
					if (icon != null && icon.getWidth() > ICON_SIZE) icon = Img.resize(icon, ICON_SIZE, Math.max(1, icon.getHeight() * ICON_SIZE / icon.getWidth()));
					this.iconImage = icon;
				}
			} catch (Exception ignored) {
			}
			this.meta = m;
			if (m == null) {
				this.detail = Component.translatable("packconverter.screen.not_a_pack").withStyle(ChatFormatting.RED);
			} else if (Converter.needsConversion(m)) {
				this.detail = Component.translatable("packconverter.screen.old", PackFormats.describe(m.format())).withStyle(ChatFormatting.GOLD);
			} else {
				this.detail = Component.translatable("packconverter.screen.current", PackFormats.describe(m.maxFormat())).withStyle(ChatFormatting.GRAY);
			}
			this.inspected = true;
		}
	}

	private final class PackList extends ObjectSelectionList<PackList.Row> {
		PackList(Minecraft minecraft) {
			super(minecraft, PackConverterScreen.this.width, PackConverterScreen.this.height - 114, 58, 24);
		}

		void show(List<Item> items) {
			this.replaceEntries(items.stream().map(Row::new).toList());
			this.setScrollAmount(0);
		}

		@Nullable Item selectedItem() {
			Row row = this.getSelected();
			return row == null ? null : row.item;
		}

		@Override
		public int getRowWidth() {
			return Math.min(360, PackConverterScreen.this.width - 40);
		}

		final class Row extends ObjectSelectionList.Entry<Row> {
			final Item item;

			Row(Item item) {
				this.item = item;
			}

			@Override
			public Component getNarration() {
				return Component.literal(this.item.label);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = this.getContentX() + 2, y = this.getContentY();
				switch (this.item.kind) {
					case UP -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOLDER_UP, x, y, 20, 20);
					case FOLDER -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOLDER, x, y, 20, 20);
					case PACK -> graphics.blit(RenderPipelines.GUI_TEXTURED, this.icon(), x, y, 0, 0, 20, 20, 20, 20);
				}
				int tx = x + 24;
				int color = this.item.kind == Kind.PACK ? 0xFFFFFFFF : 0xFFFFE080;
				graphics.text(PackConverterScreen.this.font, this.item.label, tx, y + 1, color);
				Component detail = switch (this.item.kind) {
					case UP -> Component.translatable("packconverter.screen.up").withStyle(ChatFormatting.DARK_GRAY);
					case FOLDER -> Component.translatable("packconverter.screen.folder").withStyle(ChatFormatting.DARK_GRAY);
					case PACK -> this.item.detail;
				};
				graphics.text(PackConverterScreen.this.font, detail, tx, y + 12, 0xFFA0A0A0);
			}

			/** The pack's icon (uploaded on first use), or the unknown pack one. */
			private Identifier icon() {
				if (this.item.icon == null && this.item.iconImage != null) {
					BufferedImage img = this.item.iconImage;
					this.item.iconImage = null;
					NativeImage pixels = new NativeImage(img.getWidth(), img.getHeight(), false);
					int[] px = Img.pixels(Img.toArgb(img));
					for (int py = 0; py < img.getHeight(); py++) {
						for (int px0 = 0; px0 < img.getWidth(); px0++) pixels.setPixel(px0, py, px[py * img.getWidth() + px0]);
					}
					Identifier id = Identifier.fromNamespaceAndPath("packconverter", "pack_icon/" + iconCount++);
					PackConverterScreen.this.minecraft.getTextureManager().register(id, new DynamicTexture(() -> "Pack icon", pixels));
					PackConverterScreen.this.icons.add(id);
					this.item.icon = id;
				}
				return this.item.icon != null ? this.item.icon : UNKNOWN_PACK;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				PackList.this.setSelected(this);
				if (doubleClick && this.item.kind != Kind.PACK) {
					if (PackConverterScreen.this.search != null) PackConverterScreen.this.search.setValue("");
					PackConverterScreen.this.showFolder(this.item.path);
				}
				PackConverterScreen.this.updateButtons();
				return true;
			}
		}
	}
}
