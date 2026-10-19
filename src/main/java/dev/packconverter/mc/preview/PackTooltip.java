package dev.packconverter.mc.preview;

import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mixin.GuiGraphicsInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The tooltip of a pack in the resource pack screen: its preview ({@link PackPreviews}) and, if switched on in the
 * settings, for old packs what they were made for and what was converted. Set from the screen after it is drawn, for
 * the pack row under the mouse ({@code ScreenMixin}), not from the row's own drawing: Lunar Client draws pack rows its
 * own way.
 */
public final class PackTooltip {
	private static final int TOOLTIP_WIDTH = 260;
	/** The name's room in the list (vanilla shows the whole name in a tooltip when it is cut short). */
	private static final int NAME_WIDTH = 151;
	/** The pack whose tooltip was last shown (for tests). */
	private static volatile @Nullable String lastShown;

	private PackTooltip() {
	}

	/** For the pack row under the mouse in either list, if over its name and description (not the icon's arrows). */
	public static void extract(GuiGraphics graphics, int mouseX, int mouseY, PackSelectionModel model,
		@Nullable TransferableSelectionList... lists) {
		for (TransferableSelectionList list : lists) {
			if (list == null || !list.isMouseOver(mouseX, mouseY)) continue;
			for (TransferableSelectionList.Entry row : list.children()) {
				if (!(row instanceof TransferableSelectionList.PackEntry) || !row.isMouseOver(mouseX, mouseY)) continue;
				if (mouseX < row.getContentX() + 32) return;
				String id = row.getPackId();
				Stream.concat(model.getSelected(), model.getUnselected()).filter(e -> e.getId().equals(id)).findFirst()
					.ifPresent(pack -> show(graphics, pack, mouseX, mouseY));
				return;
			}
		}
	}

	/** The pack whose tooltip was shown last (for tests). */
	public static @Nullable String lastShown() {
		return lastShown;
	}

	private static void show(GuiGraphics graphics, PackSelectionModel.Entry pack, int mouseX, int mouseY) {
		String id = pack.getId();
		PackPreview preview = PackPreviews.get(id);
		List<Component> lines = PackConverterConfig.get().conversionDetails ? LegacyPacks.describe(id) : null;
		boolean text = lines != null && !lines.isEmpty();
		if (preview == null && !text) return;
		Minecraft minecraft = Minecraft.getInstance();
		List<ClientTooltipComponent> components = new ArrayList<>();
		// ours replaces vanilla's full-name tooltip, so the name comes along
		if (minecraft.font.width(pack.getTitle()) > NAME_WIDTH) {
			minecraft.font.split(pack.getTitle(), TOOLTIP_WIDTH).forEach(l -> components.add(ClientTooltipComponent.create(l)));
		}
		if (preview != null) components.add(preview.component(text));
		if (text) {
			for (Component line : lines) minecraft.font.split(line, TOOLTIP_WIDTH).forEach(l -> components.add(ClientTooltipComponent.create(l)));
		}
		((GuiGraphicsInvoker) graphics).packconverter$setTooltip(minecraft.font, components, mouseX, mouseY,
			DefaultTooltipPositioner.INSTANCE, null, true);
		lastShown = id;
	}
}
