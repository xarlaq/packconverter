package dev.packconverter.mc;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * A pack switch applies what it loaded over a few frames instead of one. The game applies every listener one after
 * the other on the render thread, all in the same frame: the hitch at the end of a switch. Here the textures, sounds,
 * shaders, the atlases and the fonts each start a frame of their own. Everything from the atlases on (models,
 * particles, entity and block entity renderers, other mods' listeners) stays in the atlases' frame, so nothing is drawn
 * with new atlases and old models; fonts go last (they read the atlases). Only pack switches ({@link FastReload}).
 */
public final class FrameSplit {
	/** Work put off to the next frame: at most one piece a frame. */
	private static final Queue<Runnable> NEXT = new ConcurrentLinkedQueue<>();

	private FrameSplit() {
	}

	/** At the start of each frame (render thread). */
	public static void frame() {
		Runnable task = NEXT.poll();
		if (task != null) task.run();
	}

	/** The listeners in the order applied, those starting a frame wrapped to put their render thread work off. */
	public static List<PreparableReloadListener> split(List<PreparableReloadListener> listeners) {
		List<PreparableReloadListener> out = new ArrayList<>(listeners.size());
		List<PreparableReloadListener> last = new ArrayList<>();
		for (PreparableReloadListener listener : listeners) {
			if (listener instanceof FontManager) last.add(new StartsFrame(listener));
			else if (startsFrame(listener)) out.add(new StartsFrame(listener));
			else out.add(listener);
		}
		out.addAll(last);
		return out;
	}

	private static boolean startsFrame(PreparableReloadListener listener) {
		return listener instanceof TextureManager || listener instanceof SoundManager || listener instanceof ShaderManager
			|| listener instanceof AtlasManager;
	}

	/** A listener whose work on the render thread waits for the next frame. */
	private record StartsFrame(PreparableReloadListener listener) implements PreparableReloadListener {
		@Override
		public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor, PreparationBarrier preparationBarrier,
			Executor reloadExecutor) {
			return this.listener.reload(currentReload, taskExecutor, preparationBarrier, task -> NEXT.add(() -> reloadExecutor.execute(task)));
		}

		@Override
		public void prepareSharedState(SharedState currentReload) {
			this.listener.prepareSharedState(currentReload);
		}

		@Override
		public String getName() {
			return this.listener.getName();
		}
	}
}
