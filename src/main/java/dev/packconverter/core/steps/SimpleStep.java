package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;

import java.util.function.BiConsumer;

/** A step defined by a function. */
public record SimpleStep(int format, String name, BiConsumer<PackTree, Context> action) implements Step {
	@Override
	public void apply(PackTree tree, Context ctx) {
		action.accept(tree, ctx);
	}
}
