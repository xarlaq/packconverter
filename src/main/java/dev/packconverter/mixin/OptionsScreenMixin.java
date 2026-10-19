package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.packconverter.mc.ui.MenuButtons;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In a world, the Pack Converter button goes into the Options screen, on the left of "Resource Packs..."
 * ({@link MenuButtons#besideResourcePacks}): the button is added to the grid in its place, sharing its cell. Without
 * the target (another mod rebuilding the screen) the button is just left out.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {
	@WrapOperation(method = "init", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private LayoutElement packconverter$besideResourcePacks(GridLayout.RowHelper row, LayoutElement element, Operation<LayoutElement> original) {
		original.call(row, MenuButtons.besideResourcePacks((Screen) (Object) this, element));
		return element;
	}
}
