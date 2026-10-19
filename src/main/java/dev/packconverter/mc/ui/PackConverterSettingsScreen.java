package dev.packconverter.mc.ui;

import net.minecraft.util.Util;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.Lunar;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Settings: automatic conversion, pack folders, previews (and which material they show), netherite from diamond and
 * the knockback sword (and their colours), conversion details, cache size limit (and clearing it), and where saved
 * packs go.
 */
public final class PackConverterSettingsScreen extends Screen {
	/** What the pack previews can show: every material in turn, or one (see PackPreview). */
	private static final List<String> MATERIALS = List.of("all", "wood", "stone", "copper", "iron", "gold", "diamond", "netherite");
	private final Screen parent;
	private final PackConverterConfig config = PackConverterConfig.get();
	/** The conversion settings packs were last converted with, to see when they change. */
	private String converted = this.config.conversionOptions().key();
	// a lower header and footer than usual, so all of it fits at GUI height 240
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 24, 30);
	private @Nullable EditBox limit;
	private @Nullable EditBox exportFolder;
	private @Nullable StringWidget usage;

	public PackConverterSettingsScreen(Screen parent) {
		super(Component.translatable("packconverter.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);
		LinearLayout column = this.layout.addToContents(LinearLayout.vertical().spacing(4));
		column.defaultCellSetting().alignHorizontallyCenter();

		// the switches two by two
		LinearLayout switches = column.addChild(LinearLayout.horizontal().spacing(6), cell -> cell.paddingBottom(2));
		LinearLayout leftSwitches = switches.addChild(LinearLayout.vertical().spacing(2));
		LinearLayout rightSwitches = switches.addChild(LinearLayout.vertical().spacing(2));
		leftSwitches.addChild(CycleButton.onOffBuilder(this.config.autoConvert)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.auto.tooltip")))
			.create(Component.translatable("packconverter.settings.auto"), (button, value) -> this.config.autoConvert = value));
		// Lunar Client has pack folders of its own: off there, and locked
		boolean lunar = Lunar.detected();
		CycleButton<Boolean> folders = CycleButton.onOffBuilder(!lunar && this.config.packFolders)
			.withTooltip(value -> Tooltip.create(Component.translatable(lunar ? "packconverter.settings.folders.lunar" : "packconverter.settings.folders.tooltip")))
			.create(Component.translatable("packconverter.settings.folders"), (button, value) -> {
				if (!lunar) this.config.packFolders = value;
			});
		folders.active = !lunar;
		rightSwitches.addChild(folders);
		leftSwitches.addChild(CycleButton.onOffBuilder(this.config.packPreviews)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.previews.tooltip")))
			.create(Component.translatable("packconverter.settings.previews"), (button, value) -> this.config.packPreviews = value));
		String material = MATERIALS.contains(this.config.previewMaterial) ? this.config.previewMaterial : "all";
		rightSwitches.addChild(CycleButton.builder((String value) -> Component.translatable("packconverter.settings.material." + value), material)
			.withValues(MATERIALS)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.material.tooltip")))
			.create(Component.translatable("packconverter.settings.material"), (button, value) -> this.config.previewMaterial = value));
		// netherite: the switch, and its colour (opens the picker)
		LinearLayout netherite = leftSwitches.addChild(LinearLayout.horizontal().spacing(4));
		netherite.addChild(CycleButton.onOffBuilder(this.config.netheriteFromDiamond)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.netherite.tooltip")))
			.create(0, 0, 124, 20, Component.translatable("packconverter.settings.netherite"), (button, value) -> this.config.netheriteFromDiamond = value));
		ColorSwatch color = netherite.addChild(new ColorSwatch(22, 20, this.config::netheriteRgb, Component.translatable("packconverter.settings.netherite_color"),
			() -> this.minecraft.setScreen(new NetheriteColorScreen(this, null, this.config.netheriteRgb(), CommonComponents.GUI_DONE,
				picked -> {
					this.config.netheriteColor = String.format("#%06x", picked);
					this.applyOptions();
				}))));
		color.setTooltip(Tooltip.create(Component.translatable("packconverter.settings.netherite_color.tooltip")));
		// the knockback sword: the switch, and its colour
		LinearLayout knockback = leftSwitches.addChild(LinearLayout.horizontal().spacing(4));
		knockback.addChild(CycleButton.onOffBuilder(this.config.knockbackSword)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.knockback.tooltip")))
			.create(0, 0, 124, 20, Component.translatable("packconverter.settings.knockback"), (button, value) -> this.config.knockbackSword = value));
		ColorSwatch knockbackColor = knockback.addChild(new ColorSwatch(22, 20, this.config::knockbackRgb, Component.translatable("packconverter.settings.knockback_color"),
			() -> this.minecraft.setScreen(new NetheriteColorScreen(this, NetheriteColorScreen.Kind.KNOCKBACK, null, this.config.knockbackRgb(), CommonComponents.GUI_DONE,
				picked -> {
					this.config.knockbackColor = String.format("#%06x", picked);
					this.applyOptions();
				}))));
		knockbackColor.setTooltip(Tooltip.create(Component.translatable("packconverter.settings.knockback_color.tooltip")));
		rightSwitches.addChild(CycleButton.onOffBuilder(this.config.conversionDetails)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.details.tooltip")))
			.create(Component.translatable("packconverter.settings.details"), (button, value) -> this.config.conversionDetails = value));

		leftSwitches.addChild(CycleButton.onOffBuilder(this.config.fastReload)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.fast_reload.tooltip")))
			.create(Component.translatable("packconverter.settings.fast_reload"), (button, value) -> this.config.fastReload = value));
		leftSwitches.addChild(CycleButton.onOffBuilder(this.config.hideLoadingBar)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.loading_bar.tooltip")))
			.create(Component.translatable("packconverter.settings.loading_bar"), (button, value) -> this.config.hideLoadingBar = value));
		// Lunar Client draws the pack rows itself: no stars there, the switch locked off
		CycleButton<Boolean> favourites = CycleButton.onOffBuilder(!lunar && this.config.favourites)
			.withTooltip(value -> Tooltip.create(Component.translatable(lunar ? "packconverter.settings.favourites.lunar" : "packconverter.settings.favourites.tooltip")))
			.create(Component.translatable("packconverter.settings.favourites"), (button, value) -> {
				if (!lunar) this.config.favourites = value;
			});
		favourites.active = !lunar;
		rightSwitches.addChild(favourites);
		// random pack: from all packs, only favourites (not on Lunar: no stars there), or off
		List<String> randoms = lunar ? List.of("all", "off") : List.of("all", "favourites", "off");
		String random = !this.config.randomPack ? "off" : this.config.randomFavouritesOnly && !lunar ? "favourites" : "all";
		rightSwitches.addChild(CycleButton.builder((String value) -> value.equals("off") ? CommonComponents.OPTION_OFF
				: Component.translatable("packconverter.settings.random." + value), random)
			.withValues(randoms)
			.withTooltip(value -> Tooltip.create(Component.translatable("packconverter.settings.random.tooltip")))
			.create(Component.translatable("packconverter.settings.random"), (button, value) -> {
				this.config.randomPack = !value.equals("off");
				this.config.randomFavouritesOnly = value.equals("favourites");
			}));

		// labels beside their boxes, so all of it fits at GUI height 240
		LinearLayout limitRow = column.addChild(LinearLayout.horizontal().spacing(6), cell -> cell.paddingBottom(4));
		limitRow.defaultCellSetting().alignVerticallyMiddle();
		limitRow.addChild(new StringWidget(Component.translatable("packconverter.settings.limit"), this.font));
		this.limit = limitRow.addChild(new EditBox(this.font, 0, 0, 50, 20, Component.translatable("packconverter.settings.limit")));
		this.limit.setValue(Integer.toString(this.config.cacheLimitMb));
		this.limit.setMaxLength(7);
		limitRow.addChild(Button.builder(Component.translatable("packconverter.settings.clear"), b -> this.clearCache())
			.tooltip(Tooltip.create(Component.translatable("packconverter.settings.clear.tooltip"))).width(110).build());
		this.usage = limitRow.addChild(new StringWidget(Component.empty(), this.font));
		this.updateUsage();

		LinearLayout exportRow = column.addChild(LinearLayout.horizontal().spacing(6));
		exportRow.defaultCellSetting().alignVerticallyMiddle();
		exportRow.addChild(new StringWidget(Component.translatable("packconverter.settings.export"), this.font));
		this.exportFolder = exportRow.addChild(new EditBox(this.font, 0, 0, 180, 20, Component.translatable("packconverter.settings.export")));
		this.exportFolder.setTooltip(Tooltip.create(Component.translatable("packconverter.settings.export.tooltip")));
		this.exportFolder.setMaxLength(1024);
		this.exportFolder.setValue(this.config.exportFolder);
		exportRow.addChild(Button.builder(Component.translatable("packconverter.settings.open"), b -> this.openExportFolder()).width(50).build());

		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(200).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
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

	/** A new colour, or netherite or the knockback sword switched: shown at once in the packs in use. */
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
