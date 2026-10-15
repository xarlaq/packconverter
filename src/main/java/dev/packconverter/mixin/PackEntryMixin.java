package dev.packconverter.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.packconverter.mc.folders.Favourites;
import dev.packconverter.mc.folders.FolderRows;
import dev.packconverter.mc.folders.ResourcePackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The favourite star at the right end of a pack's name line ({@link Favourites}): an outline while the row is
 * hovered, filled yellow once starred; a click on it stars or unstars the pack. The name is cut a little shorter
 * so it never runs under the star.
 */
@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin {
	@Unique
	private static final Identifier PACKCONVERTER$OUTLINE = Identifier.fromNamespaceAndPath("packconverter", "favourite/star_outline");
	@Unique
	private static final Identifier PACKCONVERTER$OUTLINE_HIGHLIGHTED = Identifier.fromNamespaceAndPath("packconverter", "favourite/star_outline_highlighted");
	@Unique
	private static final Identifier PACKCONVERTER$STAR = Identifier.fromNamespaceAndPath("packconverter", "favourite/star");
	@Unique
	private static final Identifier PACKCONVERTER$STAR_HIGHLIGHTED = Identifier.fromNamespaceAndPath("packconverter", "favourite/star_highlighted");
	@Unique
	private static final int PACKCONVERTER$SIZE = 9;

	@Shadow
	@Final
	private TransferableSelectionList parent;
	@Shadow
	@Final
	private PackSelectionModel.Entry pack;

	@Unique
	private boolean packconverter$stars() {
		return ((FolderRows) this.parent).packconverter$stars();
	}

	@Unique
	private TransferableSelectionList.Entry packconverter$self() {
		return (TransferableSelectionList.Entry) (Object) this;
	}

	@Unique
	private int packconverter$starX() {
		return this.packconverter$self().getContentRight() - PACKCONVERTER$SIZE;
	}

	@Unique
	private int packconverter$starY() {
		return this.packconverter$self().getContentY();
	}

	/** On the star, with a pixel to spare around it. */
	@Unique
	private boolean packconverter$overStar(double mouseX, double mouseY) {
		int x = this.packconverter$starX(), y = this.packconverter$starY();
		return mouseX >= x - 1 && mouseX < x + PACKCONVERTER$SIZE + 1 && mouseY >= y - 1 && mouseY < y + PACKCONVERTER$SIZE + 1;
	}

	@ModifyArg(method = "extractContent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/StringWidget;setMaxWidth(I)Lnet/minecraft/client/gui/components/StringWidget;"))
	private int packconverter$roomForStar(int width) {
		return this.packconverter$stars() ? width - PACKCONVERTER$SIZE - 2 : width;
	}

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void packconverter$drawStar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
		if (!this.packconverter$stars()) return;
		boolean favourite = Favourites.is(this.pack.getId());
		if (!favourite && !hovered) return;
		boolean over = hovered && this.packconverter$overStar(mouseX, mouseY);
		Identifier sprite = favourite ? over ? PACKCONVERTER$STAR_HIGHLIGHTED : PACKCONVERTER$STAR
			: over ? PACKCONVERTER$OUTLINE_HIGHLIGHTED : PACKCONVERTER$OUTLINE;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.packconverter$starX(), this.packconverter$starY(), PACKCONVERTER$SIZE, PACKCONVERTER$SIZE);
		if (over) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void packconverter$clickStar(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !this.packconverter$stars() || !this.packconverter$overStar(event.x(), event.y())) return;
		Favourites.toggle(this.pack.getId());
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		((ResourcePackScreen) ((FolderRows) this.parent).packconverter$screen()).packconverter$refreshLists();
		cir.setReturnValue(true);
	}
}
