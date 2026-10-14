package dev.packconverter.mc.folders;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/** A folder (or "back") row in the pack screen's Available list, drawn like a pack: click it to go in. */
public final class FolderEntry extends TransferableSelectionList.Entry {
	private static final Identifier FOLDER = Identifier.fromNamespaceAndPath("packconverter", "folder");
	private static final Identifier FOLDER_UP = Identifier.fromNamespaceAndPath("packconverter", "folder_up");
	private static final Identifier OPEN = Identifier.withDefaultNamespace("transferable_list/select_highlighted");
	private static final Identifier BACK = Identifier.withDefaultNamespace("transferable_list/unselect_highlighted");
	private static final int TEXT_WIDTH = 151;

	/** {@code folder}: where the row leads; {@code back}: the row going up; {@code packs}: packs inside. */
	public record Row(String folder, boolean back, int packs) {
	}

	private final TransferableSelectionList list;
	private final Row row;
	private final Consumer<String> open;
	private final StringWidget name;
	private final StringWidget detail;

	public FolderEntry(TransferableSelectionList list, Row row, Consumer<String> open) {
		list.super();
		this.list = list;
		this.row = row;
		this.open = open;
		Minecraft minecraft = Minecraft.getInstance();
		this.name = new StringWidget(row.back ? Component.literal("..") : Component.literal(PackFolders.name(row.folder)), minecraft.font);
		Component detail = row.back
			? row.folder.isEmpty() ? Component.translatable("packconverter.folders.back_top") : Component.translatable("packconverter.folders.back", PackFolders.name(row.folder))
			: Component.translatable(row.packs == 1 ? "packconverter.folders.pack" : "packconverter.folders.packs", row.packs);
		this.detail = new StringWidget(detail.copy().withStyle(ChatFormatting.GRAY), minecraft.font);
	}

	@Override
	public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
		int x = this.getContentX(), y = this.getContentY();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.row.back ? FOLDER_UP : FOLDER, x, y, 32, 32);
		if (hovered || this.list.getSelected() == this && this.list.isFocused()) {
			graphics.fill(x, y, x + 32, y + 32, -1601138544);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.row.back ? BACK : OPEN, x, y, 32, 32);
		}
		this.name.setMaxWidth(TEXT_WIDTH);
		this.name.setPosition(x + 32 + 2, y + 1);
		this.name.extractRenderState(graphics, mouseX, mouseY, a);
		this.detail.setMaxWidth(TEXT_WIDTH);
		this.detail.setPosition(x + 32 + 2, y + 12);
		this.detail.extractRenderState(graphics, mouseX, mouseY, a);
	}

	public Row row() {
		return this.row;
	}

	/** Goes into the folder (or back up). */
	public void open() {
		this.open.accept(this.row.folder);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.open();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			this.open();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public Component getNarration() {
		return Component.translatable("packconverter.folders.narration", this.name.getMessage());
	}

	@Override
	public String getPackId() {
		return "";
	}
}
