package dev.packconverter.mc.folders;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import org.jspecify.annotations.Nullable;

/** On PackSelectionScreen: whether it shows the resource pack folder (it also picks data packs), our button, pack tooltips. */
public interface ResourcePackScreen {
	boolean packconverter$isResourcePacks();

	@Nullable SpriteIconButton packconverter$button();

	void packconverter$setButton(SpriteIconButton button);

	/** Sets the pack tooltip for the row under the mouse ({@link dev.packconverter.mc.preview.PackTooltip}). */
	void packconverter$packTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY);
}
