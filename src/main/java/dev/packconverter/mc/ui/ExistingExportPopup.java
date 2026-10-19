package dev.packconverter.mc.ui;

import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A pop-up box over the converter screen: "this pack is already in the export folder". Replace it, save next to it
 * as "... (2)" (the first free number), or cancel (Esc too). The choice gets the path to write.
 */
public final class ExistingExportPopup {
	private ExistingExportPopup() {
	}

	public static PopupScreen create(Screen behind, Path existing, Consumer<Path> choice) {
		Path copy = freeCopy(existing);
		return new PopupScreen.Builder(behind, Component.translatable("packconverter.screen.exists.title"))
			.setWidth(300)
			.setMessage(Component.translatable("packconverter.screen.exists", existing.getFileName().toString()))
			.addButton(Component.translatable("packconverter.screen.exists.replace"), popup -> {
				popup.onClose();
				choice.accept(existing);
			})
			.addButton(Component.translatable("packconverter.screen.exists.copy", number(copy)), popup -> {
				popup.onClose();
				choice.accept(copy);
			})
			.addButton(CommonComponents.GUI_CANCEL, PopupScreen::onClose)
			.build();
	}

	/** "Name (26.3).zip" → the first free "Name (26.3) (2).zip", "(3)"... */
	static Path freeCopy(Path existing) {
		String file = existing.getFileName().toString();
		String base = file.endsWith(".zip") ? file.substring(0, file.length() - 4) : file;
		for (int i = 2; ; i++) {
			Path candidate = existing.resolveSibling(base + " (" + i + ").zip");
			if (!Files.exists(candidate)) return candidate;
		}
	}

	private static String number(Path copy) {
		String file = copy.getFileName().toString();
		return file.substring(file.lastIndexOf(" (") + 1, file.length() - ".zip".length());
	}
}
