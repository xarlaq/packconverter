package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.packconverter.mc.folders.Favourites;
import dev.packconverter.mc.folders.FolderEntry;
import dev.packconverter.mc.folders.FolderRows;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Folder rows at the top of the Available list, and the folder's name in its header. Favourite packs go to the top
 * of that list: under the header and the row going back, above the folders.
 */
@Mixin(TransferableSelectionList.class)
public abstract class TransferableSelectionListMixin extends ObjectSelectionList<TransferableSelectionList.Entry> implements FolderRows {
	@Shadow
	@Final
	private PackSelectionScreen screen;
	@Unique
	private @Nullable String packconverter$folder;
	@Unique
	private List<FolderEntry.Row> packconverter$rows = List.of();
	@Unique
	private Consumer<String> packconverter$open = folder -> {
	};
	@Unique
	private boolean packconverter$stars;
	@Unique
	private boolean packconverter$favouritesFirst;

	private TransferableSelectionListMixin(Minecraft minecraft, int width, int height, int y, int itemHeight) {
		super(minecraft, width, height, y, itemHeight);
	}

	@Override
	public void packconverter$showFolder(@Nullable String folder, List<FolderEntry.Row> rows, Consumer<String> open) {
		this.packconverter$folder = folder;
		this.packconverter$rows = rows;
		this.packconverter$open = open;
	}

	@Override
	public void packconverter$setFavourites(boolean stars, boolean first) {
		this.packconverter$stars = stars;
		this.packconverter$favouritesFirst = first;
	}

	@Override
	public boolean packconverter$stars() {
		return this.packconverter$stars;
	}

	@Override
	public PackSelectionScreen packconverter$screen() {
		return this.screen;
	}

	@ModifyExpressionValue(method = "updateList", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/gui/screens/packs/TransferableSelectionList;title:Lnet/minecraft/network/chat/Component;"))
	private Component packconverter$folderInHeader(Component title) {
		String folder = this.packconverter$folder;
		if (folder == null || folder.isEmpty()) return title;
		String shown = folder.length() <= 24 ? folder.replace("/", " / ") : ".. / " + folder.substring(folder.lastIndexOf('/') + 1);
		return Component.empty().append(title).append(Component.literal("  " + shown)
			.withStyle(style -> style.withUnderlined(false).withBold(false).withColor(ChatFormatting.GRAY)));
	}

	@Inject(method = "updateList", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/packs/TransferableSelectionList;setSelected(Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;)V"))
	private void packconverter$addFolderRows(CallbackInfo ci) {
		TransferableSelectionList self = (TransferableSelectionList) (Object) this;
		for (FolderEntry.Row row : this.packconverter$rows) this.addEntry(new FolderEntry(self, row, this.packconverter$open));
	}

	@Inject(method = "updateList", at = @At("TAIL"))
	private void packconverter$favouritesFirst(CallbackInfo ci) {
		if (!this.packconverter$favouritesFirst || this.children().stream().noneMatch(e -> Favourites.is(e.getPackId()))) return;
		// a stable sort: each group keeps its order
		this.sort(Comparator.comparingInt(TransferableSelectionListMixin::packconverter$group));
	}

	@Unique
	private static int packconverter$group(TransferableSelectionList.Entry entry) {
		if (entry instanceof TransferableSelectionList.HeaderEntry) return 0;
		if (entry instanceof FolderEntry folder) return folder.row().back() ? 1 : 3;
		return Favourites.is(entry.getPackId()) ? 2 : 4;
	}
}
