package dev.packconverter.mixin;

import dev.packconverter.mc.ui.MenuButtons;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The Pack Converter button joins the pause menu's row of icon buttons (bugs, feedback, friends, reporting). */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
	@ModifyArg(method = "createPauseMenu", index = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;ILnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private LayoutElement packconverter$addButton(LayoutElement element) {
		if (element instanceof LinearLayout iconRow) iconRow.addChild(MenuButtons.create((PauseScreen) (Object) this));
		return element;
	}
}
