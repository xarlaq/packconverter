// Adapted from Mojang's slicer (https://github.com/Mojang/slicer):
// Copyright (c) Microsoft Corporation. All rights reserved. Licensed under the MIT license.
// Changes: works on the in-memory PackTree instead of folders; never overwrites a file the pack already has;
// skips regions that are fully transparent (the element didn't exist in the pack's version).

package dev.packconverter.core.slicer;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.io.Img;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/** Cuts sprites out of texture sheets, the way Mojang's slicer tool migrates packs. */
public final class Slicer {
	private Slicer() {
	}

	/** Region of a sheet in the sheet's own units; scaled to the actual image size (packs may be 16x...512x). */
	public record Box(int x, int y, int w, int h, int totalW, int totalH) {
		public int scaleX(int imgWidth) {
			return x * imgWidth / totalW;
		}

		public int scaleY(int imgHeight) {
			return y * imgHeight / totalH;
		}

		public int scaleW(int imgWidth) {
			return w * imgWidth / totalW;
		}

		public int scaleH(int imgHeight) {
			return h * imgHeight / totalH;
		}
	}

	public static final class Input {
		final String path;
		final List<Output> outputs = new ArrayList<>();

		public Input(String path) {
			this.path = path;
		}

		public String path() {
			return path;
		}

		public List<Output> outputs() {
			return outputs;
		}

		public Input outputs(Output... files) {
			Collections.addAll(outputs, files);
			return this;
		}

		/** Returns the number of sprites written. */
		public int process(PackTree tree, Context ctx) {
			if (!tree.has(path)) return 0;
			BufferedImage image = tree.image(path);
			if (image == null) {
				ctx.warn("Can't read " + path + ", not sliced");
				return 0;
			}
			int written = 0;
			for (Output output : outputs) {
				if (tree.has(output.path)) continue; // the pack already has its own
				Integer since = SliceSince.format(output.path);
				if (since != null && ctx.before(since)) continue; // this part of the sheet meant something else back then
				BufferedImage result = output.render(image);
				if (result == null) continue;
				for (UnaryOperator<BufferedImage> op : output.transformers) result = op.apply(result);
				tree.putImage(output.path, result);
				String inputMeta = path + ".mcmeta";
				if (tree.has(inputMeta)) tree.copy(inputMeta, output.path + ".mcmeta");
				else if (output.metadata != null) tree.putString(output.path + ".mcmeta", output.metadata);
				written++;
			}
			return written;
		}
	}

	public abstract static class Output {
		final String path;
		final List<UnaryOperator<BufferedImage>> transformers = new ArrayList<>();
		String metadata;

		protected Output(String path) {
			this.path = path;
		}

		public String path() {
			return path;
		}

		/** The sheet regions this output is cut from. */
		public abstract java.util.List<Box> regions();

		/** The output image, or null to skip it (its region holds nothing). */
		abstract BufferedImage render(BufferedImage input);

		public Output apply(UnaryOperator<BufferedImage> transform) {
			transformers.add(transform);
			return this;
		}

		public Output metadata(String metadata) {
			this.metadata = metadata;
			return this;
		}
	}

	public static final class SimpleOutput extends Output {
		private final Box box;

		public SimpleOutput(String path, Box box) {
			super(path);
			this.box = box;
		}

		@Override
		public List<Box> regions() {
			return List.of(box);
		}

		@Override
		BufferedImage render(BufferedImage image) {
			int w = image.getWidth(), h = image.getHeight();
			int x = box.scaleX(w), y = box.scaleY(h), bw = box.scaleW(w), bh = box.scaleH(h);
			if (bw <= 0 || bh <= 0 || x >= w || y >= h || Img.isEmpty(image, x, y, bw, bh)) return null;
			return Img.crop(image, x, y, bw, bh);
		}
	}

	public static final class MultipartOutput extends Output {
		private final int width;
		private final int height;
		private final List<TransformedBox> boxes;

		public MultipartOutput(String path, int width, int height, TransformedBox... boxes) {
			super(path);
			this.width = width;
			this.height = height;
			this.boxes = List.of(boxes);
		}

		@Override
		public List<Box> regions() {
			return boxes.stream().map(TransformedBox::box).toList();
		}

		@Override
		BufferedImage render(BufferedImage input) {
			Box first = boxes.getFirst().box();
			boolean any = false;
			for (TransformedBox b : boxes) {
				Box box = b.box();
				if (!Img.isEmpty(input, box.scaleX(input.getWidth()), box.scaleY(input.getHeight()), box.scaleW(input.getWidth()), box.scaleH(input.getHeight()))) {
					any = true;
					break;
				}
			}
			if (!any) return null;
			BufferedImage out = Img.create(width * input.getWidth() / first.totalW(), height * input.getHeight() / first.totalH());
			for (TransformedBox box : boxes) box.process(input, out);
			return out;
		}
	}

	public record TransformedBox(Box box, List<UnaryOperator<BufferedImage>> transformers, int offsetX, int offsetY) {
		void process(BufferedImage input, BufferedImage output) {
			int w = input.getWidth(), h = input.getHeight();
			BufferedImage sub = Img.crop(input, box.scaleX(w), box.scaleY(h), box.scaleW(w), box.scaleH(h));
			for (UnaryOperator<BufferedImage> op : transformers) sub = op.apply(sub);
			int ox = offsetX * w / box.totalW(), oy = offsetY * h / box.totalH();
			Img.blit(sub, 0, 0, sub.getWidth(), sub.getHeight(), output, ox, oy);
		}

		public static BufferedImage mirrorVertically(BufferedImage image) {
			return Img.mirrorVertically(image);
		}

		public static BufferedImage mirrorHorizontally(BufferedImage image) {
			return Img.mirrorHorizontally(image);
		}

		public static BufferedImage rotate90(BufferedImage image) {
			return Img.rotate(image, 1);
		}

		public static BufferedImage rotate180(BufferedImage image) {
			return Img.rotate(image, 2);
		}

		public static BufferedImage rotate270(BufferedImage image) {
			return Img.rotate(image, 3);
		}
	}

	/** Runs every input; returns the number of sprites written. */
	public static int run(List<Input> inputs, PackTree tree, Context ctx) {
		int written = 0;
		for (Input input : inputs) written += input.process(tree, ctx);
		return written;
	}
}
