package dev.packconverter.mc.folders;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.jspecify.annotations.Nullable;

/**
 * On PackSelectionScreen: whether it shows the resource pack folder (it also picks data packs), our buttons, pack
 * tooltips, what the random pack is picked from.
 */
public interface ResourcePackScreen {
	boolean packconverter$isResourcePacks();

	@Nullable SpriteIconButton packconverter$button();

	void packconverter$setButton(SpriteIconButton button);

	@Nullable SpriteIconButton packconverter$randomButton();

	void packconverter$setRandomButton(SpriteIconButton button);

	/** Sets the pack tooltip for the row under the mouse ({@link dev.packconverter.mc.preview.PackTooltip}). */
	void packconverter$packTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

	PackSelectionModel packconverter$model();

	/** What is typed in the search box ("" if nothing). */
	String packconverter$search();

	/** Fills both lists again (after a pack was starred). */
	void packconverter$refreshLists();
}
