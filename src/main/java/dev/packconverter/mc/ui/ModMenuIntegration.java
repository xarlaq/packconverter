package dev.packconverter.mc.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's config button opens Pack Converter on its settings (Mod Menu is optional). */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new PackConverterScreen(parent, PackConverterScreen.CONVERSION);
	}
}
