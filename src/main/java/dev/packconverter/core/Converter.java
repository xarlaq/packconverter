package dev.packconverter.core;

import dev.packconverter.core.source.PackSource;

import java.io.IOException;
import java.util.function.Predicate;

/** Runs the steps a pack needs, oldest first, on a virtual copy of the pack. */
public final class Converter {
	/** Bump when conversion output changes, so cached conversions are redone. */
	public static final int VERSION = 10;

	/** The format converted packs are made for (Minecraft 1.21.11). Steps for later versions don't run. */
	public static final PackMeta.Format TARGET = new PackMeta.Format(75, 0);
	/** The target version, for messages. */
	public static final String TARGET_NAME = "1.21.11";

	public record Result(PackTree tree, Context context, long nanos) {
	}

	private Converter() {
	}

	/** Reads pack.mcmeta; null if the pack has none or declares no format. */
	public static PackMeta readMeta(PackSource source) throws IOException {
		for (String path : source.paths()) {
			if (PackTree.normalize(path).equals("pack.mcmeta")) return PackMeta.parse(source.read(path));
		}
		return null;
	}

	public static boolean needsConversion(PackMeta meta) {
		return meta != null && meta.maxFormat().compareTo(TARGET) < 0;
	}

	/** Converts with nothing beyond what the pack has ({@link Options#PLAIN}). */
	public static Result convert(PackSource source, PackMeta meta, VanillaAssets vanilla) {
		return convert(source, meta, vanilla, Options.PLAIN);
	}

	public static Result convert(PackSource source, PackMeta meta, VanillaAssets vanilla, Options options) {
		long start = System.nanoTime();
		PackTree tree = new PackTree(source);
		Context ctx = new Context(meta, vanilla, options);
		for (Step step : Steps.all()) {
			if (ctx.before(step.format()) && forTarget(step)) run(step, tree, ctx);
		}
		for (Step step : Steps.finish()) run(step, tree, ctx);
		return new Result(tree, ctx, System.nanoTime() - start);
	}

	/** The pack as the first step matching {@code stop} would see it (for tools that study a single step). */
	public static Result convertUntil(PackSource source, PackMeta meta, VanillaAssets vanilla, Predicate<Step> stop) {
		long start = System.nanoTime();
		PackTree tree = new PackTree(source);
		Context ctx = new Context(meta, vanilla);
		for (Step step : Steps.all()) {
			if (stop.test(step)) break;
			if (ctx.before(step.format()) && forTarget(step)) run(step, tree, ctx);
		}
		return new Result(tree, ctx, System.nanoTime() - start);
	}

	/** A step for a change up to the target version (the 26.x ones are left out on 1.21.11). */
	private static boolean forTarget(Step step) {
		return step.format() <= TARGET.major();
	}

	private static void run(Step step, PackTree tree, Context ctx) {
		long t = System.nanoTime();
		try {
			step.apply(tree, ctx);
		} catch (RuntimeException e) {
			// one broken file must not cost the whole pack: report and carry on with the next step
			ctx.warn(step.name() + " failed: " + e);
		}
		ctx.time(step.name(), System.nanoTime() - t);
	}
}
