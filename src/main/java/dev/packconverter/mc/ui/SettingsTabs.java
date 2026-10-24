package dev.packconverter.mc.ui;

import net.minecraft.util.Util;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.Lunar;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The settings, as three tabs of {@link PackConverterScreen}: Conversion (automatic conversion, conversion details,
 * and what is made from a pack's own items: netherite, copper, the knockback sword, with their colours), Resource
 * packs (folders, favourites, the random pack, previews and which material they show), and Other (fast pack
 * switching and its loading bar, the cache size limit and clearing it, where saved packs go). Lives as long as the
 * screen; its tabs are made again each time the screen sets itself up.
 */
final class SettingsTabs {
	/** What the pack previews can show: every material in turn, or one (see PackPreview). */
	private static final List<String> MATERIALS = List.of("all", "wood", "stone", "copper", "iron", "gold", "diamond", "netherite");
	private final Screen screen;
	private final PackConverterConfig config = PackConverterConfig.get();
	/** The conversion settings packs were last converted with, to see when they change. */
	private String converted = this.config.conversionOptions().key();
	private @Nullable EditBox limit;
	private @Nullable EditBox exportFolder;
	private @Nullable StringWidget usage;

	SettingsTabs(Screen screen) {
		this.screen = screen;
	}

	/** The three tabs, made anew (the screen sets itself up again after a colour screen or a popup). */
	List<Tab> tabs() {
		return List.of(new ConversionTab(), new ResourcePacksTab(), new OtherTab());
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Conversion: on or off, its details, and what is made from the pack's own items. */
	private final class ConversionTab extends GridLayoutTab {
		ConversionTab() {
			super(Component.translatable("packconverter.settings.tab.conversion"));
			PackConverterConfig config = SettingsTabs.this.config;
			this.layout.columnSpacing(6).rowSpacing(4);
			GridLayout.RowHelper rows = this.layout.createRowHelper(2);
			rows.addChild(CycleButton.onOffBuilder(config.autoConvert)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.auto.tooltip")))
				.create(Component.translatable("packconverter.settings.auto"), (button, value) -> config.autoConvert = value));
			rows.addChild(CycleButton.onOffBuilder(config.conversionDetails)
				.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.details.tooltip")))
				.create(Component.translatable("packconverter.settings.details"), (button, value) -> config.conversionDetails = value));
			rows.addChild(new StringWidget(Component.translatable("packconverter.settings.made").withStyle(ChatFormatting.GRAY), font()),
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
	private LinearLayout made(String key, boolean on, Consumer<Boolean> set, IntSupplier rgb, NetheriteColorScreen.Kind kind, IntConsumer pick) {
		LinearLayout row = LinearLayout.horizontal().spacing(4);
		row.addChild(CycleButton.onOffBuilder(on)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings." + key + ".tooltip")))
			.create(0, 0, 124, 20, Component.translatable("packconverter.settings." + key), (button, value) -> set.accept(value)));
		ColorSwatch swatch = row.addChild(new ColorSwatch(22, 20, rgb, Component.translatable("packconverter.settings." + key + "_color"),
			() -> Minecraft.getInstance().setScreen(new NetheriteColorScreen(this.screen, kind, null, rgb.getAsInt(), CommonComponents.GUI_DONE, picked -> {
				pick.accept(picked);
				this.applyOptions();
			}))));
		swatch.setTooltip(Tooltip.create(Component.translatable("packconverter.settings." + key + "_color.tooltip")));
		return row;
	}

	/** The resource pack screen: folders, favourites, the random pack, previews. */
	private final class ResourcePacksTab extends GridLayoutTab {
		ResourcePacksTab() {
			super(Component.translatable("packconverter.settings.tab.pack_screen"));
			PackConverterConfig config = SettingsTabs.this.config;
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
			SettingsTabs tabs = SettingsTabs.this;
			PackConverterConfig config = tabs.config;
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
			limitRow.addChild(new StringWidget(Component.translatable("packconverter.settings.limit"), font()));
			tabs.limit = limitRow.addChild(new EditBox(font(), 0, 0, 50, 20, Component.translatable("packconverter.settings.limit")));
			tabs.limit.setValue(Integer.toString(config.cacheLimitMb));
			tabs.limit.setMaxLength(7);
			limitRow.addChild(Button.builder(Component.translatable("packconverter.settings.clear"), b -> tabs.clearCache())
				.tooltip(Tooltip.create(Component.translatable("packconverter.settings.clear.tooltip"))).width(110).build());
			tabs.usage = limitRow.addChild(new StringWidget(Component.empty(), font()));
			tabs.updateUsage();

			LinearLayout exportRow = rows.addChild(LinearLayout.horizontal().spacing(6), 2, rows.newCellSettings().alignHorizontallyCenter());
			exportRow.defaultCellSetting().alignVerticallyMiddle();
			exportRow.addChild(new StringWidget(Component.translatable("packconverter.settings.export"), font()));
			tabs.exportFolder = exportRow.addChild(new EditBox(font(), 0, 0, 180, 20, Component.translatable("packconverter.settings.export")));
			tabs.exportFolder.setTooltip(Tooltip.create(Component.translatable("packconverter.settings.export.tooltip")));
			tabs.exportFolder.setMaxLength(1024);
			tabs.exportFolder.setValue(config.exportFolder);
			exportRow.addChild(Button.builder(Component.translatable("packconverter.settings.open"), b -> tabs.openExportFolder()).width(50).build());
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

	/** Saves the settings, with the cache limit and export folder as typed. */
	void apply() {
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
	void applyOptions() {
		String now = this.config.conversionOptions().key();
		if (now.equals(this.converted)) return;
		this.converted = now;
		this.config.save();
		LegacyPacks.applyOptions();
	}
}
