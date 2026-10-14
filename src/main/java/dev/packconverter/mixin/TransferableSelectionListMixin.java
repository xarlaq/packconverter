package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.packconverter.mc.folders.FolderEntry;
import dev.packconverter.mc.folders.FolderRows;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

/** Folder rows at the top of the Available list, and the folder's name in its header. */
@Mixin(TransferableSelectionList.class)
public abstract class TransferableSelectionListMixin extends ObjectSelectionList<TransferableSelectionList.Entry> implements FolderRows {
	@Unique
	private @Nullable String packconverter$folder;
	@Unique
	private List<FolderEntry.Row> packconverter$rows = List.of();
	@Unique
	private Consumer<String> packconverter$open = folder -> {
	};

	private TransferableSelectionListMixin(Minecraft minecraft, int width, int height, int y, int itemHeight) {
		super(minecraft, width, height, y, itemHeight);
	}

	@Override
	public void packconverter$showFolder(@Nullable String folder, List<FolderEntry.Row> rows, Consumer<String> open) {
		this.packconverter$folder = folder;
		this.packconverter$rows = rows;
		this.packconverter$open = open;
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
}
