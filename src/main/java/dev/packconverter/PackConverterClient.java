package dev.packconverter;

import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.preview.PackPreviews;
import dev.packconverter.mc.ui.MenuButtons;
import dev.packconverter.mc.ui.RandomPack;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PackConverterClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Pack Converter");

	@Override
	public void onInitializeClient() {
		PackConverterConfig.get();
		MenuButtons.register();
		RandomPack.register();
		PackPreviews.register();
		// tidy the cache (conversions of deleted packs, size limit) without holding up startup
		Thread cleanup = new Thread(() -> {
			try {
				LegacyPacks.cache().cleanUp();
			} catch (RuntimeException e) {
				LOGGER.warn("Cache cleanup failed", e);
			}
		}, "Pack Converter cache cleanup");
		cleanup.setDaemon(true);
		cleanup.setPriority(Thread.MIN_PRIORITY);
		cleanup.start();
	}
}
