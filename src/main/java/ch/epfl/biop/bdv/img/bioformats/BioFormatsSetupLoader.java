/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2022 Nicolas Chiaruttini, BIOP, EPFL
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the BIOP nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.epfl.biop.bdv.img.bioformats;

import bdv.AbstractViewerSetupImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.AbstractIntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

public class BioFormatsSetupLoader<T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V> & NativeType<V>, A>
	extends AbstractViewerSetupImgLoader<T, V> implements
	MultiResolutionSetupImgLoader<T>
{

	private static final Logger logger = LoggerFactory.getLogger(
		BioFormatsSetupLoader.class);

	final Function<RandomAccessibleInterval<T>, RandomAccessibleInterval<FloatType>> cvtRaiToFloatRai;

	final Converter<T, FloatType> cvt;

	final BioFormatsBdvOpener opener;

	final private ReaderPool readerPool;

	final int iSerie, iChannel;

	final Supplier<VolatileGlobalCellCache> cacheSupplier;

	final int[] cellDimensions;

	final int numberOfTimePoints;

	final boolean switchZandC;

	final Unit<Length> targetUnit;

	final Dimensions[] dimensions;

	final int numMipmapLevels;

	final VoxelDimensions voxelsDimensions;

	final double[][] mmResolutions;

	final CacheArrayLoader<A> loader;

	final int setup;

	@SuppressWarnings("unchecked")
	public BioFormatsSetupLoader(BioFormatsBdvOpener opener, int sourceIndex,
		int channelIndex, int setup, T t, V v,
		Supplier<VolatileGlobalCellCache> cacheSupplier) throws Exception
	{
		super(t, v);
		this.setup = setup;
		this.cacheSupplier = cacheSupplier;
		this.opener = opener;
		this.readerPool = opener.getReaderPool();
		iSerie = sourceIndex;
		iChannel = channelIndex;

		if (t instanceof FloatType) {
			cvt = null;
			cvtRaiToFloatRai = null; // rai -> (RandomAccessibleInterval<FloatType>)
																// ((Object) rai); // Nothing to be done
		}
		else if (t instanceof ARGBType) {
			// Average of RGB value
			cvt = (input, output) -> {
				int val = ((ARGBType) input).get();
				int r = ARGBType.red(val);
				int g = ARGBType.green(val);
				int b = ARGBType.blue(val);
				output.set(r + g + b);
			};
			cvtRaiToFloatRai = rai -> Converters.convert(rai, cvt, new FloatType());
		}
		else if (t instanceof AbstractIntegerType) {
			cvt = (input, output) -> output.set(((AbstractIntegerType) input)
				.getRealFloat());
			cvtRaiToFloatRai = rai -> Converters.convert(rai, cvt, new FloatType());
		}
		else {
			cvt = null;
			cvtRaiToFloatRai = e -> {
				logger.error("Conversion of " + t.getClass() +
					" to FloatType unsupported.");
				return null;
			};
		}

		this.targetUnit = opener.u;
		this.switchZandC = opener.swZC;

		boolean isLittleEndian;

		IFormatReader reader = null;
		try {
			reader = readerPool.acquire();
			reader.setSeries(iSerie);
			numMipmapLevels = reader.getResolutionCount();
			reader.setResolution(0);
			isLittleEndian = reader.isLittleEndian();

			// MetaData
			final IMetadata omeMeta = (IMetadata) reader.getMetadataStore();

			boolean is3D;

			if (omeMeta.getPixelsSizeZ(iSerie).getNumberValue().intValue() > 1) {
				is3D = true;
			}
			else {
				is3D = false;
			}

			numberOfTimePoints = reader.getSizeT();
			cellDimensions = new int[] { opener.useBioFormatsXYBlockSize ? reader
				.getOptimalTileWidth() : (int) opener.cacheBlockSize.dimension(0),
				opener.useBioFormatsXYBlockSize ? reader.getOptimalTileHeight()
					: (int) opener.cacheBlockSize.dimension(1), (!is3D) ? 1
						: (int) opener.cacheBlockSize.dimension(2) };

			voxelsDimensions = BioFormatsMetaDataHelper.getSeriesVoxelDimensions(
				omeMeta, iSerie, opener.u, opener.voxSizeReferenceFrameLength);

			dimensions = new Dimensions[numMipmapLevels];
			for (int level = 0; level < numMipmapLevels; level++) {
				reader.setResolution(level);
				dimensions[level] = getDimensions(reader.getSizeX(), reader.getSizeY(),
					(!is3D) ? 1 : reader.getSizeZ());
			}

			// Needs to compute mipmap resolutions... pfou
			mmResolutions = new double[numMipmapLevels][3];
			mmResolutions[0][0] = 1;
			mmResolutions[0][1] = 1;
			mmResolutions[0][2] = 1;

			if (reader.getFormat().equals("CellSens VSI")) { // Fix vsi issue see
																												// https://forum.image.sc/t/qupath-omero-weird-pyramid-levels/65484
				for (int iLevel = 1; iLevel < numMipmapLevels; iLevel++) {
					double downscalingFactor = Math.pow(2, iLevel);
					mmResolutions[iLevel][0] = downscalingFactor;
					mmResolutions[iLevel][1] = downscalingFactor;
					mmResolutions[iLevel][2] = 1;
				}
			}
			else {
				int[] srcL0dims = new int[] { (int) dimensions[0].dimension(0),
					(int) dimensions[0].dimension(1), (int) dimensions[0].dimension(2) };
				for (int iLevel = 1; iLevel < numMipmapLevels; iLevel++) {
					int[] srcLidims = new int[] { (int) dimensions[iLevel].dimension(0),
						(int) dimensions[iLevel].dimension(1), (int) dimensions[iLevel]
							.dimension(2) };
					mmResolutions[iLevel][0] = (double) srcL0dims[0] /
						(double) srcLidims[0];
					mmResolutions[iLevel][1] = (double) srcL0dims[1] /
						(double) srcLidims[1];
					mmResolutions[iLevel][2] = (double) srcL0dims[2] /
						(double) srcLidims[2];
				}
			}

		}
		finally {
			readerPool.recycle(reader);
		}
		if (t instanceof UnsignedByteType) {
			loader =
				(CacheArrayLoader<A>) new BioFormatsArrayLoaders.BioFormatsUnsignedByteArrayLoader(
					readerPool, iSerie, iChannel, switchZandC);
		}
		else if (t instanceof UnsignedShortType) {
			loader =
				(CacheArrayLoader<A>) new BioFormatsArrayLoaders.BioFormatsUnsignedShortArrayLoader(
					readerPool, iSerie, iChannel, switchZandC, isLittleEndian);
		}
		else if (t instanceof FloatType) {
			loader =
				(CacheArrayLoader<A>) new BioFormatsArrayLoaders.BioFormatsFloatArrayLoader(
					readerPool, iSerie, iChannel, switchZandC, isLittleEndian);
		}
		else if (t instanceof IntType) {
			loader =
				(CacheArrayLoader<A>) new BioFormatsArrayLoaders.BioFormatsIntArrayLoader(
					readerPool, iSerie, iChannel, switchZandC, isLittleEndian);
		}
		else if (t instanceof ARGBType) {
			loader =
				(CacheArrayLoader<A>) new BioFormatsArrayLoaders.BioFormatsRGBArrayLoader(
					readerPool, iSerie, iChannel, switchZandC);
		}
		else {
			throw new UnsupportedOperationException("Pixel type " + t.getClass()
				.getName() + " unsupported in " + BioFormatsSetupLoader.class
					.getName());
		}
	}

	static Dimensions getDimensions(long sizeX, long sizeY, long sizeZ) {
		return new Dimensions() {

			@Override
			public long dimension(int d) {
				if (d == 0) return sizeX;
				if (d == 1) return sizeY;
				return sizeZ;
			}

			@Override
			public int numDimensions() {
				return 3;
			}
		};
	}

	@Override
	public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId,
		int level, boolean normalize, ImgLoaderHint... hints)
	{
		return cvtRaiToFloatRai.apply(getImage(timepointId, level));
	}

	@Override
	public Dimensions getImageSize(int timepointId, int level) {
		return dimensions[level];
	}

	@Override
	public RandomAccessibleInterval<T> getImage(int timepointId, int level,
		ImgLoaderHint... hints)
	{
		final long[] dims = dimensions[level].dimensionsAsLongArray();
		final int[] cellDimensions = this.cellDimensions;
		final CellGrid grid = new CellGrid(dims, cellDimensions);

		final int priority = this.numMipmapLevels - level;
		final CacheHints cacheHints = new CacheHints(LoadingStrategy.BLOCKING,
			priority, false);

		return cacheSupplier.get().createImg(grid, timepointId, setup, level,
			cacheHints, loader, type);
	}

	@Override
	public RandomAccessibleInterval<V> getVolatileImage(int timepointId,
		int level, ImgLoaderHint... hints)
	{
		final long[] dims = dimensions[level].dimensionsAsLongArray();
		final int[] cellDimensions = this.cellDimensions;
		final CellGrid grid = new CellGrid(dims, cellDimensions);

		final int priority = this.numMipmapLevels - level;
		final CacheHints cacheHints = new CacheHints(LoadingStrategy.BUDGETED,
			priority, false);

		return cacheSupplier.get().createImg(grid, timepointId, setup, level,
			cacheHints, loader, volatileType);
	}

	@Override
	public double[][] getMipmapResolutions() {
		return mmResolutions;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms() {
		AffineTransform3D[] ats = new AffineTransform3D[numMipmapLevels];

		for (int iLevel = 0; iLevel < numMipmapLevels; iLevel++) {
			AffineTransform3D at = new AffineTransform3D();
			at.scale(mmResolutions[iLevel][0], mmResolutions[iLevel][1],
				mmResolutions[iLevel][2]);
			ats[iLevel] = at;
		}

		return ats;
	}

	@Override
	public int numMipmapLevels() {
		return numMipmapLevels;
	}

	@Override
	public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId,
		boolean normalize, ImgLoaderHint... hints)
	{
		return cvtRaiToFloatRai.apply(getImage(timepointId, 0));
	}

	@Override
	public Dimensions getImageSize(int timepointId) {
		return getImageSize(0, 0);
	}

	@Override
	public VoxelDimensions getVoxelSize(int timepointId) {
		return voxelsDimensions;
	}

	public ReaderPool getReaderPool() {
		return readerPool;
	}

	public BioFormatsBdvOpener getOpener() {
		return opener;
	}

}
