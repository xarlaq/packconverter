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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Pack Converter's screen, in tabs (as vanilla's world creation): Save packs, then the settings ({@link SettingsTabs}).
 * Save packs browses the resource pack folder (subfolders included, like pack manager mods allow) and saves converted
 * copies of old packs as normal 26.3 packs, for clients without this mod or to share; several at once when picked
 * with Ctrl (one more or less) and Shift (a range), or all with Ctrl+A. Pack details and icons load in the background
 * so big folders open instantly; the search box looks through every folder.
 */
public final class PackConverterScreen extends Screen {
	/** The tabs, in order. */
	public static final int SAVE_PACKS = 0, CONVERSION = 1, RESOURCE_PACKS = 2, OTHER = 3;
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
	private final TabManager tabManager = new TabManager(widget -> this.addRenderableWidget(widget), widget -> this.removeWidget(widget));
	private final SettingsTabs settings = new SettingsTabs(this);
	private @Nullable MenuTabBar tabs;
	/** The tab shown, kept while a popup or colour screen is open on top (the tabs are made again after it). */
	private int shownTab;
	private @Nullable PackList list;
	private @Nullable EditBox search;
	private @Nullable Button convertButton;
	private @Nullable Button convertAllButton;
	/** Where the Save packs tab writes its folder line and status line (set as it is laid out). */
	private int locationY, statusY;
	private Component location = Component.empty();
	/** Every pack under resourcepacks, for searches (listed on the first one, again after saving). */
	private @Nullable List<Path> allPacks;
	/** Packs picked with Ctrl and Shift (and the one clicked), and where a Shift range starts. Kept while shown. */
	private final Set<Path> marked = new LinkedHashSet<>();
	private @Nullable Path anchor;
	/** The row last clicked, selected again when the rows are listed again. */
	private @Nullable Path selectedPath;
	/** Icons uploaded for the rows shown, released when other rows replace them and when the screen closes. */
	private final List<Identifier> icons = new ArrayList<>();
	private static int iconCount;
	/** Set while several packs are saved; pressing "Convert folder" again asks it to stop after the current pack. */
	private volatile boolean batch, stopBatch;
	private Component status = Component.empty();
	private volatile boolean busy;

	public PackConverterScreen(Screen parent) {
		this(parent, SAVE_PACKS);
	}

	/** Opened on tab {@code tab} ({@link #SAVE_PACKS}, {@link #CONVERSION}...). */
	public PackConverterScreen(Screen parent, int tab) {
		super(Component.translatable("packconverter.screen.title"));
		this.parent = parent;
		this.shownTab = tab;
		this.root = Minecraft.getInstance().getResourcePackDirectory().toAbsolutePath().normalize();
		this.folder = root;
	}

	@Override
	protected void init() {
		this.shownTab = Math.max(0, this.currentTab());
		List<Tab> all = new ArrayList<>();
		all.add(new SavePacksTab());
		all.addAll(this.settings.tabs());
		this.tabs = MenuTabBar.builder(this.tabManager, this.width).addTabs(all.toArray(Tab[]::new)).build();
		this.addRenderableWidget(this.tabs);
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(200).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.tabs.selectTab(this.shownTab, false);
		this.repositionElements();
		this.refresh();
	}

	/** Shows tab {@code index} ({@link #SAVE_PACKS}, {@link #CONVERSION}, {@link #RESOURCE_PACKS}, {@link #OTHER}). */
	public void showTab(int index) {
		if (this.tabs != null) this.tabs.selectTab(index, false);
	}

	/** The tab shown. */
	public int currentTab() {
		return this.tabs == null ? this.shownTab : this.tabs.getTabs().indexOf(this.tabManager.getCurrentTab());
	}

	@Override
	protected void repositionElements() {
		if (this.tabs == null) return;
		this.tabs.arrangeElements(this.width);
		int top = this.tabs.getRectangle().bottom();
		this.tabManager.setTabArea(new ScreenRectangle(0, top, this.width, this.height - this.layout.getFooterHeight() - top));
		this.layout.setHeaderHeight(top);
		this.layout.arrangeElements();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.tabs != null && this.tabs.keyPressed(event) || super.keyPressed(event)) return true;
		// Ctrl+A picks every pack shown (in the search box, it selects the text)
		if (event.isSelectAll() && this.tabManager.getCurrentTab() instanceof SavePacksTab) {
			this.markAll();
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		this.settings.apply();
		LegacyPacks.cache().enforceLimit();
		this.minecraft.gui.setScreen(this.parent);
		this.settings.applyOptions();
	}

	@Override
	public void removed() {
		this.releaseIcons();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.height - this.layout.getFooterHeight() - 2, 0.0F, 0.0F, this.width, 2, 32, 2);
		if (this.tabManager.getCurrentTab() instanceof SavePacksTab) {
			graphics.centeredText(this.font, this.location, this.width / 2, this.locationY, 0xFFFFFFFF);
			graphics.centeredText(this.font, this.status, this.width / 2, this.statusY, 0xFFE0E0E0);
		}
	}

	/**
	 * The pack list with the search box above and the buttons below. The folder line goes above the search box and
	 * the status line above the buttons, written by the screen.
	 */
	private final class SavePacksTab implements Tab {
		/** Room for the folder line, and for the status line. */
		private static final int LINE = 13;
		private final LinearLayout layout = LinearLayout.vertical();
		private final EditBox search;
		private final PackList list;
		private final LinearLayout buttons = LinearLayout.horizontal().spacing(6);

		SavePacksTab() {
			PackConverterScreen screen = PackConverterScreen.this;
			String query = screen.search != null ? screen.search.getValue() : "";
			this.search = new EditBox(screen.font, 200, 18, Component.translatable("packconverter.screen.search"));
			this.search.setHint(Component.translatable("packconverter.screen.search").withStyle(ChatFormatting.DARK_GRAY));
			this.search.setValue(query);
			this.search.setResponder(text -> screen.refresh());
			this.list = new PackList(screen.minecraft);
			this.layout.defaultCellSetting().alignHorizontallyCenter();
			this.layout.addChild(this.search, cell -> cell.paddingTop(LINE));
			this.layout.addChild(this.list, cell -> cell.paddingTop(4));
			this.layout.addChild(this.buttons, cell -> cell.paddingTop(LINE).paddingBottom(3));
			screen.convertButton = this.buttons.addChild(Button.builder(Component.translatable("packconverter.screen.convert"), b -> screen.convertSelected())
				.width(120).tooltip(Tooltip.create(Component.translatable("packconverter.screen.convert.tooltip"))).build());
			screen.convertAllButton = this.buttons.addChild(Button.builder(Component.translatable("packconverter.screen.convert_all"), b -> screen.convertAll())
				.width(120).tooltip(Tooltip.create(Component.translatable("packconverter.screen.convert_all.tooltip"))).build());
			this.buttons.addChild(Button.builder(Component.translatable("packconverter.screen.open_export"), b -> screen.openExportFolder()).width(120).build());
			screen.search = this.search;
			screen.list = this.list;
		}

		@Override
		public Component getTabTitle() {
			return Component.translatable("packconverter.screen.tab");
		}

		@Override
		public Component getTabExtraNarration() {
			return Component.empty();
		}

		@Override
		public void visitChildren(Consumer<AbstractWidget> children) {
			this.layout.visitWidgets(children);
		}

		@Override
		public Layout getLayout() {
			return this.layout;
		}

		/** The list takes the height the rest leaves. */
		@Override
		public void doLayout(ScreenRectangle area) {
			int rest = LINE + this.search.getHeight() + 4 + LINE + 20 + 3;
			this.list.setSize(area.width(), Math.max(24, area.height() - rest));
			this.layout.arrangeElements();
			this.layout.setPosition(area.left(), area.top());
			this.list.updateSizeAndPosition(this.list.getWidth(), this.list.getHeight(), this.list.getX(), this.list.getY());
			PackConverterScreen.this.locationY = this.search.getY() - LINE + 3;
			PackConverterScreen.this.statusY = this.buttons.getY() - LINE + 2;
		}
	}

	/** The folder shown, or the search's results from every folder. */
	private void refresh() {
		String query = this.search != null ? this.search.getValue().strip().toLowerCase(Locale.ROOT) : "";
		if (query.isEmpty()) this.showFolder(this.folder);
		else this.showSearch(query);
	}

	private void showFolder(Path dir) {
		this.folder = dir;
		String rel = this.root.relativize(dir).toString().replace('\\', '/');
		this.location = Component.literal("resourcepacks/" + rel).withStyle(ChatFormatting.GRAY);
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
		this.location = Component.translatable("packconverter.screen.search_all").withStyle(ChatFormatting.GRAY);
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

	/** Lists the rows; picks made among them stay picked, the rest go. */
	private void show(List<Item> items) {
		this.releaseIcons();
		Set<Path> shown = new HashSet<>();
		for (Item item : items) shown.add(item.path);
		this.marked.retainAll(shown);
		if (this.anchor != null && !shown.contains(this.anchor)) this.anchor = null;
		if (this.list != null) {
			this.list.show(items);
			for (PackList.Row row : this.list.children()) {
				if (row.item.kind != Kind.UP && row.item.path.equals(this.selectedPath)) this.list.select(row);
			}
		}
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

	/**
	 * A row clicked, or reached with the arrow keys: alone picks just that pack, with Ctrl (Cmd on a Mac) adds it or
	 * takes it out, with Shift picks the packs from the row last clicked to it (added to the others with Ctrl too).
	 * Folders are never picked.
	 */
	private void pick(PackList.Row row, boolean ctrl, boolean shift) {
		if (this.list == null) return;
		Item item = row.item;
		this.selectedPath = item.kind == Kind.UP ? null : item.path;
		if (shift && this.anchor != null) {
			List<PackList.Row> rows = this.list.children();
			int from = -1, to = rows.indexOf(row);
			for (int i = 0; i < rows.size(); i++) if (rows.get(i).item.path.equals(this.anchor)) from = i;
			if (from < 0) from = to;
			if (!ctrl) this.marked.clear();
			for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
				if (rows.get(i).item.kind == Kind.PACK) this.marked.add(rows.get(i).item.path);
			}
		} else if (ctrl) {
			if (item.kind == Kind.PACK && !this.marked.remove(item.path)) this.marked.add(item.path);
			this.anchor = item.path;
		} else {
			this.marked.clear();
			if (item.kind == Kind.PACK) this.marked.add(item.path);
			this.anchor = item.path;
		}
		this.updateButtons();
	}

	private void click(PackList.Row row, boolean ctrl, boolean shift) {
		if (this.list == null) return;
		this.list.select(row);
		this.pick(row, ctrl, shift);
	}

	/** Every pack shown picked (Ctrl+A). */
	private void markAll() {
		if (this.list == null) return;
		for (PackList.Row row : this.list.children()) if (row.item.kind == Kind.PACK) this.marked.add(row.item.path);
		this.updateButtons();
	}

	/** Clicks the row of a pack shown, by file name, as {@link #click} (for tests and keyboard-less automation). */
	public boolean clickPack(String fileName, boolean ctrl, boolean shift) {
		if (this.list == null) return false;
		for (PackList.Row row : this.list.children()) {
			if (row.item.kind != Kind.UP && row.item.path.getFileName().toString().equals(fileName)) {
				this.click(row, ctrl, shift);
				return true;
			}
		}
		return false;
	}

	/** Selects the row of a pack shown, by file name, as a plain click does. */
	public boolean select(String fileName) {
		return this.clickPack(fileName, false, false);
	}

	/** How many packs are picked. */
	public int markedCount() {
		return this.marked.size();
	}

	/** True while nothing is being saved and every pack's details are read. */
	public boolean idle() {
		if (this.busy || this.list == null) return false;
		for (PackList.Row row : this.list.children()) if (row.item.kind == Kind.PACK && !row.item.inspected) return false;
		return true;
	}

	/** What "Convert and save" saves: the old packs picked, in the list's order. */
	private List<Item> targets() {
		if (this.list == null) return List.of();
		return this.list.children().stream().map(row -> row.item).filter(item -> this.marked.contains(item.path) && item.convertible()).toList();
	}

	private void updateButtons() {
		if (this.convertButton != null) {
			int n = this.targets().size();
			this.convertButton.active = !this.busy && n > 0;
			this.convertButton.setMessage(n > 1 ? Component.translatable("packconverter.screen.convert_count", n) : Component.translatable("packconverter.screen.convert"));
		}
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
				then.accept(config.conversionOptions().withNetherite(true, color));
			})
			.addButton(Component.translatable("packconverter.netherite.ask.pick"), popup -> this.minecraft.gui.setScreen(new NetheriteColorScreen(this,
				asking.getFirst().path, color, Component.translatable("packconverter.netherite.save"), picked -> then.accept(config.conversionOptions().withNetherite(true, picked)))))
			.addButton(Component.translatable("packconverter.netherite.ask.no"), popup -> {
				popup.onClose();
				then.accept(config.conversionOptions().withNetherite(false, color));
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

	/** "Convert and save": the pack selected, or every pack picked (as "Convert folder" saves several). */
	private void convertSelected() {
		List<Item> packs = this.targets();
		if (packs.isEmpty() || this.busy) return;
		if (packs.size() > 1) {
			this.askNetherite(packs, options -> this.convertAll(packs, options));
			return;
		}
		Item item = packs.getFirst();
		this.askNetherite(packs, options -> this.convertSelected(item, options));
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

		/** Selects a row without changing which packs are picked (a click picks them itself). */
		void select(Row row) {
			super.setSelected(row);
		}

		/** The arrow keys pick the row they reach, a range with Shift. */
		@Override
		public void setSelected(@Nullable Row row) {
			boolean moved = row != null && row != this.getSelected();
			super.setSelected(row);
			if (moved) PackConverterScreen.this.pick(row, false, PackConverterScreen.this.minecraft.hasShiftDown());
		}

		@Override
		public int getRowWidth() {
			return Math.min(360, PackConverterScreen.this.width - 40);
		}

		/**
		 * Every picked pack outlined as vanilla's selection (white while the list has focus), and a pack taken out with
		 * Ctrl only dimly, so it doesn't look picked.
		 */
		@Override
		protected void extractItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Row entry) {
			boolean selected = this.getSelected() == entry;
			if (PackConverterScreen.this.marked.contains(entry.item.path) || selected && entry.item.kind != Kind.PACK) {
				this.extractSelection(graphics, entry, this.isFocused() ? 0xFFFFFFFF : 0xFF808080);
			} else if (selected) {
				this.extractSelection(graphics, entry, 0xFF404040);
			}
			entry.extractContent(graphics, mouseX, mouseY, Objects.equals(this.getHovered(), entry), a);
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
				PackConverterScreen.this.click(this, event.hasControlDownWithQuirk(), event.hasShiftDown());
				if (doubleClick && this.item.kind != Kind.PACK) {
					if (PackConverterScreen.this.search != null) PackConverterScreen.this.search.setValue("");
					PackConverterScreen.this.showFolder(this.item.path);
				}
				return true;
			}
		}
	}
}
