/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2024 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package ch.epfl.biop.bdv.img.imageplus;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.CacheControlOverride;
import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.TypedBasicImgLoader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

import java.util.HashMap;
import java.util.function.Function;

/**
 * ImageLoader backed by a ImagePlus. The ImagePlus may be virtual and in
 * contrast to the imglib2 wrappers, we do not try to load all slices into
 * memory. Instead, slices are stored in {@link VolatileGlobalCellCache}. Use
 * createFloatInstance(ImagePlus), createUnsignedByteInstance(ImagePlus) or
 * createUnsignedShortInstance(ImagePlus) depending on the ImagePlus pixel type.
 * When loading images ({@link #getSetupImgLoader(int)},
 * {@link BasicSetupImgLoader#getImage(int, ImgLoaderHint...)}) the provided
 * setup id is used as the channel index of the {@link ImagePlus}, the provided
 * timepoint id is used as the frame index of the {@link ImagePlus}.
 *
 * @param <T> (non-volatile) pixel type
 * @param <V> volatile pixel type
 * @param <A> volatile array access type
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class ImagePlusImageLoader<T extends NativeType<T>, V extends Volatile<T> & NativeType<V>, A extends DataAccess & VolatileAccess>
		implements ViewerImgLoader, TypedBasicImgLoader<T>, CacheControlOverride
{

	public static
	ImagePlusImageLoader<FloatType, VolatileFloatType, VolatileFloatArray>
	createFloatInstance(final ImagePlus imp, final int offsetTime)
	{
		return new ImagePlusImageLoader<>(imp, array -> new VolatileFloatArray(
				(float[]) array, true), new FloatType(), new VolatileFloatType(),
				offsetTime);
	}

	public static
	ImagePlusImageLoader<UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray>
	createUnsignedShortInstance(final ImagePlus imp, final int offsetTime)
	{
		return new ImagePlusImageLoader<>(imp, array -> new VolatileShortArray(
				(short[]) array, true), new UnsignedShortType(),
				new VolatileUnsignedShortType(), offsetTime);
	}

	public static
	ImagePlusImageLoader<UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray>
	createUnsignedByteInstance(final ImagePlus imp, final int offsetTime)
	{
		return new ImagePlusImageLoader<>(imp, array -> new VolatileByteArray(
				(byte[]) array, true), new UnsignedByteType(),
				new VolatileUnsignedByteType(), offsetTime);
	}

	public static
	ImagePlusImageLoader<ARGBType, VolatileARGBType, VolatileIntArray>
	createARGBInstance(final ImagePlus imp, final int offsetTime)
	{
		return new ImagePlusImageLoader<>(imp, array -> new VolatileIntArray(
				(int[]) array, true), new ARGBType(), new VolatileARGBType(), offsetTime);
	}

	private static final double[][] mipmapResolutions = new double[][] { { 1, 1, 1 } };

	private static final AffineTransform3D[] mipmapTransforms =
			new AffineTransform3D[] { new AffineTransform3D() };

	private final CacheArrayLoader<A> loader;

	private VolatileGlobalCellCache cache;

	private final long[] dimensions;

	private final int[] cellDimensions;

	private final HashMap<Integer, SetupImgLoader> setupImgLoaders;

	private static int getByteCount(final PrimitiveType primitiveType) {
		// TODO: PrimitiveType.getByteCount() should be public, then we wouldn't
		// have to do this...
		switch (primitiveType) {
			case BYTE:
				return 1;
			case SHORT:
				return 2;
			case INT:
			case FLOAT:
			default:
				return 4;
		}
	}

	final ImagePlus imp;

	public ImagePlus getImagePlus() {
		return this.imp;
	}

	final int timeShift;

	public int getTimeShift() {
		return timeShift;
	}

	protected ImagePlusImageLoader(final ImagePlus imp,
								   final Function<Object, A> wrapPixels, final T type, final V volatileType,
								   final int timeOffset)
	{
		this.loader = new VirtualStackArrayLoader<>(imp, wrapPixels, getByteCount(
				type.getNativeTypeFactory().getPrimitiveType()));
		this.imp = imp;
		this.timeShift = timeOffset;
		dimensions = new long[] { imp.getWidth(), imp.getHeight(), imp
				.getNSlices() };
		cellDimensions = new int[] { imp.getWidth(), imp.getHeight(), 1 };
		final int numSetups = imp.getNChannels();
		cache = new VolatileGlobalCellCache(1, 1);
		setupImgLoaders = new HashMap<>();
		for (int setupId = 0; setupId < numSetups; ++setupId)
			setupImgLoaders.put(setupId, new SetupImgLoader(setupId, type,
					volatileType, timeOffset));
	}

	protected ImagePlusImageLoader(final ImagePlus imp,
								   final Function<Object, A> wrapPixels, final T type, final V volatileType)
	{
		this(imp, wrapPixels, type, volatileType, 0);
	}

	@Override
	public VolatileGlobalCellCache getCacheControl() {
		return cache;
	}

	@Override
	public void setCacheControl(VolatileGlobalCellCache cache)  {
		CacheControlOverride.Tools.shutdownCacheQueue(this.cache);
		this.cache.clearCache();
		this.cache = cache;
	}

	@Override
	public SetupImgLoader getSetupImgLoader(final int setupId) {
		return setupImgLoaders.get(setupId);
	}

	static class VirtualStackArrayLoader<A extends DataAccess> implements CacheArrayLoader<A> {

		private final ImagePlus imp;

		private final Function<Object, A> wrapPixels;

		private final int bytesPerElement;

		public VirtualStackArrayLoader(final ImagePlus imp,
									   final Function<Object, A> wrapPixels, final int bytesPerElement)
		{
			this.imp = imp;
			this.wrapPixels = wrapPixels;
			this.bytesPerElement = bytesPerElement;
		}

		@Override
		public A loadArray(final int timepoint, final int setup, final int level,
						   final int[] dimensions, final long[] min)
		{
			final int channel = setup + 1;
			final int slice = (int) min[2] + 1;
			final int frame = timepoint + 1;
			return wrapPixels.apply(imp.getStack().getProcessor(imp.getStackIndex(
					channel, slice, frame)).getPixels());
		}

		@Override
		public int getBytesPerElement() {
			return bytesPerElement;
		}
	}

	public class SetupImgLoader extends AbstractViewerSetupImgLoader<T, V> {

		private final int setupId;

		private final int timeOffset;

		protected SetupImgLoader(final int setupId, final T type,
								 final V volatileType, final int timeOffset)
		{
			super(type, volatileType);
			this.setupId = setupId;
			this.timeOffset = timeOffset;
		}

		@Override
		public RandomAccessibleInterval<V> getVolatileImage(final int timepointId,
															final int level, final ImgLoaderHint... hints)
		{
			return prepareCachedImage(timepointId - timeOffset, level,
					LoadingStrategy.BUDGETED, volatileType);
		}

		@Override
		public RandomAccessibleInterval<T> getImage(final int timepointId,
													final int level, final ImgLoaderHint... hints)
		{
			return prepareCachedImage(timepointId - timeOffset, level,
					LoadingStrategy.BLOCKING, type);
		}

		/**
		 * Create a {@link CachedCellImg} backed by the cache.
		 *
		 * @param timepointId timepoint
		 * @param level resolution level
		 * @param loadingStrategy loading strategy
		 * @param type type
		 * @param <T> type
		 * @return a {@link CachedCellImg} backed by the cache.
		 */
		protected <T extends NativeType<T>> AbstractCellImg<T, A, ?, ?>
		prepareCachedImage(final int timepointId, final int level,
						   final LoadingStrategy loadingStrategy, final T type)
		{
			final int priority = 0;
			final CacheHints cacheHints = new CacheHints(loadingStrategy, priority,
					false);
			final CellGrid grid = new CellGrid(dimensions, cellDimensions);
			return cache.createImg(grid, timepointId, setupId, level, cacheHints,
					loader, type);
		}

		@Override
		public double[][] getMipmapResolutions() {
			return mipmapResolutions;
		}

		@Override
		public AffineTransform3D[] getMipmapTransforms() {
			return mipmapTransforms;
		}

		@Override
		public int numMipmapLevels() {
			return 1;
		}
	}
}
