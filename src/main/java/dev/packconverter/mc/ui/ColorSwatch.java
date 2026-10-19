package dev.packconverter.mc.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/** A button showing a colour (0xRRGGBB): a preset to pick, or the current colour that opens the picker. */
final class ColorSwatch extends AbstractButton {
	private final IntSupplier color;
	private final Runnable press;

	ColorSwatch(int width, int height, IntSupplier color, Component name, Runnable press) {
		super(0, 0, width, height, name);
		this.color = color;
		this.press = press;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.press.run();
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF808080);
		graphics.fill(this.getX() + 1, this.getY() + 1, this.getRight() - 1, this.getBottom() - 1, 0xFF000000 | this.color.getAsInt());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
