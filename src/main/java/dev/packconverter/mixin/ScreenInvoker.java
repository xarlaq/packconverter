package dev.packconverter.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Adds and removes our buttons the way screens do it themselves. Fabric's {@code Screens.getButtons} list works out
 * where to insert from the screen's lists, which Lunar Client's pack screen keeps out of step (adding failed there).
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T packconverter$addRenderableWidget(T widget);

	@Invoker("removeWidget")
	void packconverter$removeWidget(GuiEventListener widget);
}
