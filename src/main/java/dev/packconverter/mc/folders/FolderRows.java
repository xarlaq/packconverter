package dev.packconverter.mc.folders;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** On TransferableSelectionList (the pack screen's lists): folder rows, favourite stars, the screen. */
public interface FolderRows {
	/** Set on the Available list before it is filled: the folder shown (null: none, as when searching) and its rows. */
	void packconverter$showFolder(@Nullable String folder, List<FolderEntry.Row> rows, Consumer<String> open);

	/** Stars on this list's pack rows ({@link Favourites}); {@code first}: favourites sorted to the top. */
	void packconverter$setFavourites(boolean stars, boolean first);

	boolean packconverter$stars();

	PackSelectionScreen packconverter$screen();
}
