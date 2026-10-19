package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.packconverter.mc.Lunar;
import dev.packconverter.mc.folders.Favourites;
import dev.packconverter.mc.folders.FolderEntry;
import dev.packconverter.mc.folders.FolderRows;
import dev.packconverter.mc.folders.PackFolders;
import dev.packconverter.mc.folders.ResourcePackScreen;
import dev.packconverter.mc.preview.PackTooltip;
import dev.packconverter.mc.ui.MenuButtons;
import dev.packconverter.mc.ui.RandomPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Folders in the resource pack screen ({@link PackFolders}): the Available list shows the current folder's packs
 * under rows for its subfolders (and one going back); a search looks through every folder. "Open Pack Folder" and
 * packs dropped on the screen use the folder shown. Favourite packs go first ({@link Favourites}). The Pack Converter
 * and random pack buttons sit between "Open Pack Folder" and "Done". Not for data packs (same screen, other directory).
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen implements ResourcePackScreen {
	@Shadow
	@Final
	private PackSelectionModel model;
	@Shadow
	@Final
	private Path packDir;
	@Shadow
	private @Nullable TransferableSelectionList availablePackList;
	@Shadow
	private @Nullable TransferableSelectionList selectedPackList;
	@Shadow
	private @Nullable EditBox search;
	@Unique
	private @Nullable SpriteIconButton packconverter$convert;
	@Unique
	private @Nullable SpriteIconButton packconverter$random;

	@Shadow
	protected abstract void populateLists(PackSelectionModel.@Nullable EntryBase transferredEntry);

	private PackSelectionScreenMixin(Component title) {
		super(title);
	}

	@Override
	public @Nullable SpriteIconButton packconverter$button() {
		return this.packconverter$convert;
	}

	@Override
	public void packconverter$setButton(SpriteIconButton button) {
		this.packconverter$convert = button;
	}

	@Override
	public @Nullable SpriteIconButton packconverter$randomButton() {
		return this.packconverter$random;
	}

	@Override
	public void packconverter$setRandomButton(SpriteIconButton button) {
		this.packconverter$random = button;
	}

	@Override
	public PackSelectionModel packconverter$model() {
		return this.model;
	}

	@Override
	public String packconverter$search() {
		return this.search == null ? "" : this.search.getValue();
	}

	@Override
	public void packconverter$refreshLists() {
		this.populateLists(null);
	}

	@Override
	public void packconverter$packTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		if (this.packconverter$isResourcePacks()) PackTooltip.extract(graphics, mouseX, mouseY, this.model, this.availablePackList, this.selectedPackList);
	}

	@Override
	public boolean packconverter$isResourcePacks() {
		return this.packDir.toAbsolutePath().normalize().equals(Minecraft.getInstance().getResourcePackDirectory().toAbsolutePath().normalize());
	}

	@Unique
	private boolean packconverter$folders() {
		return PackFolders.enabled() && this.packconverter$isResourcePacks();
	}

	@Unique
	private boolean packconverter$favourites() {
		return Favourites.enabled() && this.packconverter$isResourcePacks();
	}

	/**
	 * The Pack Converter and random pack buttons join the footer row, between "Open Pack Folder" and "Done": the
	 * screen's layout places them (and again on resize). Lunar builds that row its own way ({@link MenuButtons}).
	 */
	@WrapOperation(method = "init", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private LayoutElement packconverter$addConvertButton(LinearLayout row, LayoutElement child, Operation<LayoutElement> original) {
		if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("pack.openFolder")
			&& this.packconverter$isResourcePacks()) {
			// opens the folder shown (1.21.11 builds the button with a lambda we can't reach by name)
			return original.call(row, Button.builder(button.getMessage(), b -> Util.getPlatform().openPath(this.packconverter$shownDirectory(this.packDir)))
				.tooltip(Tooltip.create(Component.translatable("pack.folderInfo"))).width(button.getWidth()).build());
		}
		if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("gui.done")
			&& this.packconverter$isResourcePacks() && !Lunar.detected()) {
			this.packconverter$convert = row.addChild(MenuButtons.create(this));
			if (RandomPack.enabled()) this.packconverter$random = row.addChild(RandomPack.button(this));
		}
		return original.call(row, child);
	}

	@WrapOperation(method = "filterEntries", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/packs/TransferableSelectionList;updateList(Ljava/util/stream/Stream;Lnet/minecraft/client/gui/screens/packs/PackSelectionModel$EntryBase;)V"))
	private void packconverter$folderView(TransferableSelectionList list, Stream<PackSelectionModel.Entry> entries,
		PackSelectionModel.@Nullable EntryBase transferred, Operation<Void> original, @Local(argsOnly = true) String search) {
		FolderRows rows = (FolderRows) list;
		boolean favourites = this.packconverter$favourites();
		rows.packconverter$setFavourites(favourites, favourites && list == this.availablePackList);
		if (list != this.availablePackList || !this.packconverter$folders()) {
			original.call(list, entries, transferred);
			return;
		}
		if (!search.isBlank()) {
			// a search looks in every folder (the id it matches includes the folder)
			rows.packconverter$showFolder(null, List.of(), this::packconverter$open);
			original.call(list, entries, transferred);
			return;
		}
		List<String> ids = Stream.concat(this.model.getSelected(), this.model.getUnselected()).map(PackSelectionModel.Entry::getId).toList();
		String folder = PackFolders.current();
		if (!PackFolders.exists(folder, ids.stream())) {
			folder = "";
			PackFolders.setCurrent(folder);
		}
		List<FolderEntry.Row> folderRows = new ArrayList<>();
		if (!folder.isEmpty()) folderRows.add(new FolderEntry.Row(PackFolders.parent(folder), true, 0));
		PackFolders.subfolders(folder, ids.stream()).forEach((sub, count) -> folderRows.add(new FolderEntry.Row(sub, false, count)));
		rows.packconverter$showFolder(folder, folderRows, this::packconverter$open);
		String shown = folder;
		// at the top, favourites from every folder too
		original.call(list, entries.filter(e -> PackFolders.folderOf(e.getId()).equals(shown) || favourites && shown.isEmpty() && Favourites.is(e.getId())),
			transferred);
	}

	@Unique
	private void packconverter$open(String folder) {
		PackFolders.setCurrent(folder);
		this.populateLists(null);
		if (this.availablePackList != null) this.availablePackList.setScrollAmount(0);
	}

	/** Packs dropped on the screen go into the folder shown. */
	@ModifyVariable(method = "copyPacks", at = @At("HEAD"), argsOnly = true)
	private static Path packconverter$dropIntoShownFolder(Path dir) {
		boolean resourcePacks = dir.toAbsolutePath().normalize().equals(Minecraft.getInstance().getResourcePackDirectory().toAbsolutePath().normalize());
		if (!resourcePacks || !PackFolders.enabled() || PackFolders.current().isEmpty()) return dir;
		Path folder = dir.resolve(PackFolders.current());
		return Files.isDirectory(folder) ? folder : dir;
	}

	@Unique
	private Path packconverter$shownDirectory(Path dir) {
		if (!this.packconverter$folders() || PackFolders.current().isEmpty()) return dir;
		Path folder = dir.resolve(PackFolders.current());
		return Files.isDirectory(folder) ? folder : dir;
	}
}
