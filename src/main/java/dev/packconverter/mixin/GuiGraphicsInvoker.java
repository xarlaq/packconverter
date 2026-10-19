package dev.packconverter.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** A tooltip made of any components (the pack preview image above text lines). */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsInvoker {
	@Invoker("setTooltipForNextFrameInternal")
	void packconverter$setTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner,
		@Nullable Identifier style, boolean replaceExisting);
}
