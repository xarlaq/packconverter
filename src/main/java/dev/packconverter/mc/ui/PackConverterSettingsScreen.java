package dev.packconverter.mc.ui;

import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.Lunar;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Settings, in three tabs (as vanilla's world creation): Conversion (automatic conversion, conversion details, and
 * what is made from a pack's own items: netherite, copper, the knockback sword, with their colours), Pack screen
 * (folders, favourites, the random pack, previews and which material they show), and Other (fast pack switching and
 * its loading bar, the cache size limit and clearing it, where saved packs go).
 */
public final class PackConverterSettingsScreen extends Screen {
	/** What the pack previews can show: every material in turn, or one (see PackPreview). */
	private static final List<String> MATERIALS = List.of("all", "wood", "stone", "copper", "iron", "gold", "diamond", "netherite");
	private final Screen parent;
	private final PackConverterConfig config = PackConverterConfig.get();
	/** The conversion settings packs were last converted with, to see when they change. */
	private String converted = this.config.conversionOptions().key();
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final TabManager tabManager = new TabManager(widget -> this.addRenderableWidget(widget), widget -> this.removeWidget(widget));
	private @Nullable TabNavigationBar tabs;
	/** The tab shown, kept while a colour screen is open on top (the tabs are made again when it closes). */
	private int shownTab;
	private @Nullable EditBox limit;
	private @Nullable EditBox exportFolder;
	private @Nullable StringWidget usage;

	public PackConverterSettingsScreen(Screen parent) {
		super(Component.translatable("packconverter.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.tabs != null) this.shownTab = Math.max(0, this.tabs.getTabs().indexOf(this.tabManager.getCurrentTab()));
		this.tabs = TabNavigationBar.builder(this.tabManager, this.width).addTabs(new ConversionTab(), new PackScreenTab(), new OtherTab()).build();
		this.addRenderableWidget(this.tabs);
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(200).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.tabs.selectTab(this.shownTab, false);
		this.repositionElements();
	}

	/** Shows tab {@code index} (0 Conversion, 1 Pack screen, 2 Other). */
	public void showTab(int index) {
		if (this.tabs != null) this.tabs.selectTab(index, false);
	}

	@Override
	protected void repositionElements() {
		if (this.tabs == null) return;
		this.tabs.setWidth(this.width);
		this.tabs.arrangeElements();
		int top = this.tabs.getRectangle().bottom();
		this.tabManager.setTabArea(new ScreenRectangle(0, top, this.width, this.height - this.layout.getFooterHeight() - top));
		this.layout.setHeaderHeight(top);
		this.layout.arrangeElements();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return this.tabs != null && this.tabs.keyPressed(event) || super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		super.render(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.height - this.layout.getFooterHeight() - 2, 0.0F, 0.0F, this.width, 2, 32, 2);
	}

	/** Conversion: on or off, its details, and what is made from the pack's own items. */
	private final class ConversionTab extends GridLayoutTab {
		ConversionTab() {
			super(Component.translatable("packconverter.settings.tab.conversion"));
			PackConverterConfig config = PackConverterSettingsScreen.this.config;
			this.layout.columnSpacing(6).rowSpacing(4);
			GridLayout.RowHelper rows = this.layout.createRowHelper(2);
			rows.addChild(CycleButton.onOffBuilder(config.autoConvert)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.auto.tooltip")))
				.create(Component.translatable("packconverter.settings.auto"), (button, value) -> config.autoConvert = value));
			rows.addChild(CycleButton.onOffBuilder(config.conversionDetails)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.details.tooltip")))
				.create(Component.translatable("packconverter.settings.details"), (button, value) -> config.conversionDetails = value));
			rows.addChild(new StringWidget(Component.translatable("packconverter.settings.made").withStyle(ChatFormatting.GRAY), PackConverterSettingsScreen.this.font),
				2, rows.newCellSettings().alignHorizontallyCenter().paddingTop(6));
			rows.addChild(made("netherite", config.netheriteFromDiamond, value -> config.netheriteFromDiamond = value, config::netheriteRgb,
				NetheriteColorScreen.Kind.NETHERITE, picked -> config.netheriteColor = String.format("#%06x", picked)));
			rows.addChild(made("copper", config.copperFromIron, value -> config.copperFromIron = value, config::copperRgb,
				NetheriteColorScreen.Kind.COPPER, picked -> config.copperColor = String.format("#%06x", picked)));
			rows.addChild(made("knockback", config.knockbackSword, value -> config.knockbackSword = value, config::knockbackRgb,
				NetheriteColorScreen.Kind.KNOCKBACK, picked -> config.knockbackColor = String.format("#%06x", picked)));
		}
	}

	/**
	 * A switch for something made from the pack's own items, and the swatch of its colour (opens the picker; the new
	 * colour is shown at once).
	 */
	private LinearLayout made(String key, boolean on, java.util.function.Consumer<Boolean> set, IntSupplier rgb,
		NetheriteColorScreen.Kind kind, IntConsumer pick) {
		LinearLayout row = LinearLayout.horizontal().spacing(4);
		row.addChild(CycleButton.onOffBuilder(on)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings." + key + ".tooltip")))
			.create(0, 0, 124, 20, Component.translatable("packconverter.settings." + key), (button, value) -> set.accept(value)));
		ColorSwatch swatch = row.addChild(new ColorSwatch(22, 20, rgb, Component.translatable("packconverter.settings." + key + "_color"),
			() -> this.minecraft.setScreen(new NetheriteColorScreen(this, kind, null, rgb.getAsInt(), CommonComponents.GUI_DONE, picked -> {
				pick.accept(picked);
				this.applyOptions();
			}))));
		swatch.setTooltip(Tooltip.create(Component.translatable("packconverter.settings." + key + "_color.tooltip")));
		return row;
	}

	/** The resource pack screen: folders, favourites, the random pack, previews. */
	private final class PackScreenTab extends GridLayoutTab {
		PackScreenTab() {
			super(Component.translatable("packconverter.settings.tab.pack_screen"));
			PackConverterConfig config = PackConverterSettingsScreen.this.config;
			this.layout.columnSpacing(6).rowSpacing(4);
			GridLayout.RowHelper rows = this.layout.createRowHelper(2);
			// Lunar Client has pack folders of its own and draws the pack rows itself: folders and stars off there, locked
			boolean lunar = Lunar.detected();
			CycleButton<Boolean> folders = CycleButton.onOffBuilder(!lunar && config.packFolders)
				.withTooltip(value -> Tooltip.create(Component.translatable(lunar ? "packconverter.settings.folders.lunar" : "packconverter.settings.folders.tooltip")))
				.create(Component.translatable("packconverter.settings.folders"), (button, value) -> {
					if (!lunar) config.packFolders = value;
				});
			folders.active = !lunar;
			rows.addChild(folders);
			CycleButton<Boolean> favourites = CycleButton.onOffBuilder(!lunar && config.favourites)
				.withTooltip(value -> Tooltip.create(Component.translatable(lunar ? "packconverter.settings.favourites.lunar" : "packconverter.settings.favourites.tooltip")))
				.create(Component.translatable("packconverter.settings.favourites"), (button, value) -> {
					if (!lunar) config.favourites = value;
				});
			favourites.active = !lunar;
			rows.addChild(favourites);
			rows.addChild(CycleButton.onOffBuilder(config.packPreviews)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.previews.tooltip")))
				.create(Component.translatable("packconverter.settings.previews"), (button, value) -> config.packPreviews = value));
			String material = MATERIALS.contains(config.previewMaterial) ? config.previewMaterial : "all";
			rows.addChild(CycleButton.builder((String value) -> Component.translatable("packconverter.settings.material." + value), material)
				.withValues(MATERIALS)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.material.tooltip")))
				.create(Component.translatable("packconverter.settings.material"), (button, value) -> config.previewMaterial = value));
			// random pack: from all packs, only favourites (not on Lunar: no stars there), or off
			List<String> randoms = lunar ? List.of("all", "off") : List.of("all", "favourites", "off");
			String random = !config.randomPack ? "off" : config.randomFavouritesOnly && !lunar ? "favourites" : "all";
			rows.addChild(CycleButton.builder((String value) -> value.equals("off") ? CommonComponents.OPTION_OFF
					: Component.translatable("packconverter.settings.random." + value), random)
				.withValues(randoms)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.random.tooltip")))
				.create(Component.translatable("packconverter.settings.random"), (button, value) -> {
					config.randomPack = !value.equals("off");
					config.randomFavouritesOnly = value.equals("favourites");
				}));
		}
	}

	/** Pack switching, the cache, where saved packs go. */
	private final class OtherTab extends GridLayoutTab {
		OtherTab() {
			super(Component.translatable("packconverter.settings.tab.other"));
			PackConverterSettingsScreen screen = PackConverterSettingsScreen.this;
			PackConverterConfig config = screen.config;
			this.layout.columnSpacing(6).rowSpacing(4);
			GridLayout.RowHelper rows = this.layout.createRowHelper(2);
			rows.addChild(CycleButton.onOffBuilder(config.fastReload)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.fast_reload.tooltip")))
				.create(Component.translatable("packconverter.settings.fast_reload"), (button, value) -> config.fastReload = value));
			rows.addChild(CycleButton.onOffBuilder(config.hideLoadingBar)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.loading_bar.tooltip")))
				.create(Component.translatable("packconverter.settings.loading_bar"), (button, value) -> config.hideLoadingBar = value));

			LinearLayout limitRow = rows.addChild(LinearLayout.horizontal().spacing(6), 2, rows.newCellSettings().alignHorizontallyCenter().paddingTop(8));
			limitRow.defaultCellSetting().alignVerticallyMiddle();
			limitRow.addChild(new StringWidget(Component.translatable("packconverter.settings.limit"), screen.font));
			screen.limit = limitRow.addChild(new EditBox(screen.font, 0, 0, 50, 20, Component.translatable("packconverter.settings.limit")));
			screen.limit.setValue(Integer.toString(config.cacheLimitMb));
			screen.limit.setMaxLength(7);
			limitRow.addChild(Button.builder(Component.translatable("packconverter.settings.clear"), b -> screen.clearCache())
				.tooltip(Tooltip.create(Component.translatable("packconverter.settings.clear.tooltip"))).width(110).build());
			screen.usage = limitRow.addChild(new StringWidget(Component.empty(), screen.font));
			screen.updateUsage();

			LinearLayout exportRow = rows.addChild(LinearLayout.horizontal().spacing(6), 2, rows.newCellSettings().alignHorizontallyCenter());
			exportRow.defaultCellSetting().alignVerticallyMiddle();
			exportRow.addChild(new StringWidget(Component.translatable("packconverter.settings.export"), screen.font));
			screen.exportFolder = exportRow.addChild(new EditBox(screen.font, 0, 0, 180, 20, Component.translatable("packconverter.settings.export")));
			screen.exportFolder.setTooltip(Tooltip.create(Component.translatable("packconverter.settings.export.tooltip")));
			screen.exportFolder.setMaxLength(1024);
			screen.exportFolder.setValue(config.exportFolder);
			exportRow.addChild(Button.builder(Component.translatable("packconverter.settings.open"), b -> screen.openExportFolder()).width(50).build());
		}
	}

	private void updateUsage() {
		if (this.usage == null) return;
		long bytes = LegacyPacks.cache().size();
		this.usage.setMessage(Component.translatable("packconverter.settings.usage", String.format("%.1f", bytes / 1048576.0))
			.withStyle(ChatFormatting.GRAY));
	}

	private void clearCache() {
		LegacyPacks.cache().clear();
		this.updateUsage();
	}

	private void openExportFolder() {
		this.apply();
		Path dir = this.config.exportPath();
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		Util.getPlatform().openPath(dir);
	}

	private void apply() {
		if (this.limit != null) {
			try {
				this.config.cacheLimitMb = Math.max(16, Integer.parseInt(this.limit.getValue().trim()));
			} catch (NumberFormatException ignored) {
				// keep the previous limit
			}
		}
		if (this.exportFolder != null && !this.exportFolder.getValue().isBlank()) this.config.exportFolder = this.exportFolder.getValue().trim();
		this.config.save();
	}

	/** A new colour, or netherite, copper or the knockback sword switched: shown at once in the packs in use. */
	private void applyOptions() {
		String now = this.config.conversionOptions().key();
		if (now.equals(this.converted)) return;
		this.converted = now;
		this.config.save();
		LegacyPacks.applyOptions();
	}

	@Override
	public void onClose() {
		this.apply();
		LegacyPacks.cache().enforceLimit();
		this.minecraft.setScreen(this.parent);
		this.applyOptions();
	}
}
