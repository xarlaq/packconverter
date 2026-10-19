package dev.packconverter.mc.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import dev.packconverter.mc.Lunar;
import dev.packconverter.mc.folders.ResourcePackScreen;
import dev.packconverter.mixin.ScreenInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.layouts.EqualSpacingLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pack Converter button: on the title screen right of the accessibility button, in a world in the Options screen
 * left of "Resource Packs..." ({@code OptionsScreenMixin}; 1.21.11's pause menu has no resource pack button), and on
 * the resource pack screen: between "Open Pack Folder" and "Done" ({@code PackSelectionScreenMixin}; on Lunar Client
 * placed here), with the random pack button ({@link RandomPack}) next to it.
 */
public final class MenuButtons {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("packconverter", "icon/convert");
	private static final int SIZE = 20, GAP = 4;

	private MenuButtons() {
	}

	public static SpriteIconButton create(Screen parent) {
		return SpriteIconButton.builder(Component.translatable("packconverter.button.open"),
				b -> Minecraft.getInstance().setScreen(new PackConverterScreen(parent)), true)
			.width(SIZE)
			.sprite(ICON, 16, 16)
			.withTootip()
			.build();
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) addToTitleScreen(screen);
			else if (screen instanceof PackSelectionScreen && screen instanceof ResourcePackScreen packs && packs.packconverter$isResourcePacks()) {
				if (Lunar.detected()) addBesideOpenFolder(screen, packs);
				RandomPack.listen(screen, packs);
			}
		});
	}

	/**
	 * Lunar Client lays the pack screen out its own way (search box at the bottom) and sets it up again on resize
	 * without removing the widgets: one pair of buttons per screen, put back each time, placed from the current
	 * buttons every frame ({@link #placeOnLunar}).
	 */
	private static void addBesideOpenFolder(Screen screen, ResourcePackScreen packs) {
		ScreenInvoker widgets = (ScreenInvoker) screen;
		SpriteIconButton convert = packs.packconverter$button() != null ? packs.packconverter$button() : create(screen);
		packs.packconverter$setButton(convert);
		List<SpriteIconButton> ours = new ArrayList<>(List.of(convert));
		if (RandomPack.enabled()) {
			SpriteIconButton random = packs.packconverter$randomButton() != null ? packs.packconverter$randomButton() : RandomPack.button(screen);
			packs.packconverter$setRandomButton(random);
			ours.add(random);
		} else if (packs.packconverter$randomButton() != null) {
			widgets.packconverter$removeWidget(packs.packconverter$randomButton()); // switched off since
		}
		for (SpriteIconButton button : ours) {
			widgets.packconverter$removeWidget(button);
			widgets.packconverter$addRenderableWidget(button);
		}
		placeOnLunar(screen, ours);
		ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> placeOnLunar(s, ours));
	}

	/**
	 * In the gap right of "Open Pack Folder" (Lunar's resourcepacks.gg icon sits on its left, and takes clicks first),
	 * right of "Done" if that gap is too narrow, the top-left corner if neither is there.
	 */
	private static void placeOnLunar(Screen screen, List<SpriteIconButton> buttons) {
		AbstractWidget open = find(screen, buttons, "pack.openFolder"), done = find(screen, buttons, "gui.done");
		int width = buttons.size() * (SIZE + GAP) - GAP, x, y;
		if (open == null) {
			x = 6;
			y = 6;
		} else {
			y = open.getY() + open.getHeight() / 2 - SIZE / 2;
			int right = open.getX() + open.getWidth();
			if (done != null && done.getY() == open.getY() && done.getX() - right >= width + 4) {
				x = right + (done.getX() - right - width) / 2;
			} else if (done != null) {
				x = done.getX() + done.getWidth() + GAP;
				y = done.getY() + done.getHeight() / 2 - SIZE / 2;
			} else {
				x = right + GAP;
			}
		}
		for (SpriteIconButton button : buttons) {
			button.setPosition(x, y);
			x += SIZE + GAP;
		}
	}

	/** The screen's buttons and other widgets, read from the screen itself. */
	private static List<AbstractWidget> widgets(Screen screen) {
		return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
	}

	private static @Nullable AbstractWidget find(Screen screen, List<? extends AbstractWidget> ours, String key) {
		String label = Component.translatable(key).getString();
		for (AbstractWidget w : widgets(screen)) {
			if (ours.contains(w)) continue;
			if (w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key) || w.getMessage().getString().equals(label)) return w;
		}
		return null;
	}

	/** Right of the accessibility button (left of Options sits the language one). */
	private static void addToTitleScreen(Screen screen) {
		if (find(screen, List.of(), "packconverter.button.open") != null) return; // shown again: already there
		AbstractWidget accessibility = find(screen, List.of(), "options.accessibility");
		if (accessibility == null) return;
		SpriteIconButton button = create(screen);
		button.setPosition(accessibility.getX() + accessibility.getWidth() + GAP, accessibility.getY());
		((ScreenInvoker) screen).packconverter$addRenderableWidget(button);
	}

	/**
	 * In a world, the Options screen's "Resource Packs..." button makes room for the Pack Converter button on its left,
	 * the way vanilla fits the difficulty lock beside the difficulty button there. Not on the title screen's Options,
	 * the title screen has its own button. Anything else is passed on as it is.
	 */
	public static LayoutElement besideResourcePacks(Screen options, LayoutElement element) {
		if (Minecraft.getInstance().level == null || !(element instanceof Button packs)
			|| !(packs.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("options.resourcepack"))) return element;
		SpriteIconButton button = create(options);
		EqualSpacingLayout cell = new EqualSpacingLayout(packs.getWidth(), 0, EqualSpacingLayout.Orientation.HORIZONTAL);
		packs.setWidth(packs.getWidth() - button.getWidth());
		cell.addChild(button);
		cell.addChild(packs);
		return cell;
	}
}
