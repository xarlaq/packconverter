package dev.packconverter.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.packconverter.mc.folders.Favourites;
import dev.packconverter.mc.folders.FolderRows;
import dev.packconverter.mc.folders.Pins;
import dev.packconverter.mc.folders.ResourcePackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
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

import java.util.List;

/**
 * The favourite star at the right end of a pack's name line ({@link Favourites}), and in the Selected list the pin
 * left of it ({@link Pins}): an outline while the row is hovered, filled once starred or pinned; a click on one
 * toggles it. The name is cut a little shorter so it never runs under them.
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
	private static final Identifier PACKCONVERTER$PIN_OUTLINE = Identifier.fromNamespaceAndPath("packconverter", "pin/pin_outline");
	@Unique
	private static final Identifier PACKCONVERTER$PIN_OUTLINE_HIGHLIGHTED = Identifier.fromNamespaceAndPath("packconverter", "pin/pin_outline_highlighted");
	@Unique
	private static final Identifier PACKCONVERTER$PIN = Identifier.fromNamespaceAndPath("packconverter", "pin/pin");
	@Unique
	private static final Identifier PACKCONVERTER$PIN_HIGHLIGHTED = Identifier.fromNamespaceAndPath("packconverter", "pin/pin_highlighted");
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
	private boolean packconverter$pins() {
		return ((FolderRows) this.parent).packconverter$pins();
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

	/** Left of the star, or where it would be. */
	@Unique
	private int packconverter$pinX() {
		return this.packconverter$starX() - (this.packconverter$stars() ? PACKCONVERTER$SIZE + 2 : 0);
	}

	/** On the icon at {@code x}, with a pixel to spare around it. */
	@Unique
	private boolean packconverter$over(int x, double mouseX, double mouseY) {
		int y = this.packconverter$starY();
		return mouseX >= x - 1 && mouseX < x + PACKCONVERTER$SIZE + 1 && mouseY >= y - 1 && mouseY < y + PACKCONVERTER$SIZE + 1;
	}

	@ModifyArg(method = "extractContent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/StringWidget;setMaxWidth(I)Lnet/minecraft/client/gui/components/StringWidget;"))
	private int packconverter$roomForIcons(int width) {
		if (this.packconverter$stars()) width -= PACKCONVERTER$SIZE + 2;
		if (this.packconverter$pins()) width -= PACKCONVERTER$SIZE + 2;
		return width;
	}

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void packconverter$drawIcons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
		String id = this.pack.getId();
		if (this.packconverter$stars()) {
			boolean favourite = Favourites.is(id);
			boolean over = hovered && this.packconverter$over(this.packconverter$starX(), mouseX, mouseY);
			Identifier sprite = favourite ? over ? PACKCONVERTER$STAR_HIGHLIGHTED : PACKCONVERTER$STAR
				: over ? PACKCONVERTER$OUTLINE_HIGHLIGHTED : PACKCONVERTER$OUTLINE;
			if (favourite || hovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.packconverter$starX(), this.packconverter$starY(), PACKCONVERTER$SIZE, PACKCONVERTER$SIZE);
			if (over) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
		if (this.packconverter$pins()) {
			boolean pinned = Pins.is(id);
			boolean over = hovered && this.packconverter$over(this.packconverter$pinX(), mouseX, mouseY);
			Identifier sprite = pinned ? over ? PACKCONVERTER$PIN_HIGHLIGHTED : PACKCONVERTER$PIN
				: over ? PACKCONVERTER$PIN_OUTLINE_HIGHLIGHTED : PACKCONVERTER$PIN_OUTLINE;
			if (pinned || hovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.packconverter$pinX(), this.packconverter$starY(), PACKCONVERTER$SIZE, PACKCONVERTER$SIZE);
			if (over) {
				graphics.requestCursor(CursorTypes.POINTING_HAND);
				// what the pin does (the pack's own tooltip stays away from these icons)
				Minecraft minecraft = Minecraft.getInstance();
				List<ClientTooltipComponent> lines = minecraft.font.split(Component.translatable(pinned ? "packconverter.pin.pinned" : "packconverter.pin.pin"), 200)
					.stream().map(ClientTooltipComponent::create).toList();
				((GuiGraphicsExtractorInvoker) graphics).packconverter$setTooltip(minecraft.font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, true);
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void packconverter$clickIcons(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
		if (this.packconverter$stars() && this.packconverter$over(this.packconverter$starX(), event.x(), event.y())) {
			Favourites.toggle(this.pack.getId());
		} else if (this.packconverter$pins() && this.packconverter$over(this.packconverter$pinX(), event.x(), event.y())) {
			Pins.toggle(this.pack.getId());
		} else {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		((ResourcePackScreen) ((FolderRows) this.parent).packconverter$screen()).packconverter$refreshLists();
		cir.setReturnValue(true);
	}
}
