package dev.packconverter.mc.folders;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Set on the Available list before it is filled: the folder shown (null: none, as when searching) and its rows. */
public interface FolderRows {
	void packconverter$showFolder(@Nullable String folder, List<FolderEntry.Row> rows, Consumer<String> open);
}
