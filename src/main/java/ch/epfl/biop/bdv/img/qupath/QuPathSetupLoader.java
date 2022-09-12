/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.qupath;

import bdv.AbstractViewerSetupImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsSetupLoader;
import ch.epfl.biop.bdv.img.OmeroBdvOpener;
import ch.epfl.biop.bdv.img.omero.OmeroSetupLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.FloatType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * See documentation in {@link QuPathImageLoader} This class builds the
 * setupLoader corresponding to the opener type (Bioformat or Omero) and get all
 * the necessary info from this loader.
 *
 * @author Rémy Dornier, EPFL, BIOP, 2022
 */

public class QuPathSetupLoader<T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V> & NativeType<V>, A>
	extends AbstractViewerSetupImgLoader<T, V> implements
	MultiResolutionSetupImgLoader<T>
{
	private Object imageSetupLoader;
	final public int iSerie, iChannel;
	private static final Logger logger = LoggerFactory.getLogger(
		QuPathSetupLoader.class);

	public QuPathSetupLoader(QuPathImageOpener qpOpener, int serieIndex,
		int channelIndex, int setupId, T type, V volatileType,
		Supplier<VolatileGlobalCellCache> cacheSupplier) throws Exception
	{
		super(type, volatileType);

		QuPathImageOpener opener = qpOpener;
		this.iChannel = channelIndex;
		this.iSerie = serieIndex;

		// get the setup loader corresponding to BioFormat opener
		if (qpOpener.getOpener() instanceof BioFormatsBdvOpener) {
			BioFormatsSetupLoader bfSetupLoader = new BioFormatsSetupLoader(
				(BioFormatsBdvOpener) opener.getOpener(), serieIndex, channelIndex,
				setupId, type, volatileType, cacheSupplier);
			this.imageSetupLoader = bfSetupLoader;

		}
		else {
			// get the setup loader corresponding to Omero opener
			if (qpOpener.getOpener() instanceof OmeroBdvOpener) {
				try {
					OmeroSetupLoader omeSetupLoader = new OmeroSetupLoader(
						(OmeroBdvOpener) opener.getOpener(), channelIndex, setupId,
						type, volatileType, cacheSupplier);
					this.imageSetupLoader = omeSetupLoader;
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			else {
				logger.warn("Opener " + qpOpener.getOpener() + " is not valid");
			}
		}

	}

	@Override
	public RandomAccessibleInterval<V> getVolatileImage(int timepointId,
		int level, ImgLoaderHint... hints)
	{
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return (RandomAccessibleInterval<V>) (((BioFormatsSetupLoader) this.imageSetupLoader)
				.getVolatileImage(timepointId, level, hints));
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((RandomAccessibleInterval<V>) ((OmeroSetupLoader) this.imageSetupLoader)
					.getVolatileImage(timepointId, level, hints));
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId,
		int level, boolean normalize, ImgLoaderHint... hints)
	{
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader).getFloatImage(
				timepointId, level, normalize, hints);
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader).getFloatImage(
					timepointId, level, normalize, hints);
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public Dimensions getImageSize(int timepointId, int level) {
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader).getImageSize(
				timepointId, level);
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader).getImageSize(
					timepointId, level);
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public RandomAccessibleInterval<T> getImage(int timepointId, int level,
		ImgLoaderHint... hints)
	{
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return (RandomAccessibleInterval<T>) (((BioFormatsSetupLoader) this.imageSetupLoader)
				.getImage(timepointId, level, hints));
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return (RandomAccessibleInterval<T>) (((OmeroSetupLoader) this.imageSetupLoader)
					.getImage(timepointId, level, hints));
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public double[][] getMipmapResolutions() {
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader)
				.getMipmapResolutions();
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader)
					.getMipmapResolutions();
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms() {
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader)
				.getMipmapTransforms();
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader).getMipmapTransforms();
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public int numMipmapLevels() {
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader).numMipmapLevels();
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader).numMipmapLevels();
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return 0;
	}

	@Override
	public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId,
		boolean normalize, ImgLoaderHint... hints)
	{
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader).getFloatImage(
				timepointId, normalize, hints);
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader).getFloatImage(
					timepointId, normalize, hints);
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public Dimensions getImageSize(int timepointId) {
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader).getImageSize(
				timepointId);
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader).getImageSize(
					timepointId);
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}

	@Override
	public VoxelDimensions getVoxelSize(int timepointId) {
		if (this.imageSetupLoader instanceof BioFormatsSetupLoader)
			return ((BioFormatsSetupLoader) this.imageSetupLoader).getVoxelSize(
				timepointId);
		else {
			if (this.imageSetupLoader instanceof OmeroSetupLoader)
				return ((OmeroSetupLoader) this.imageSetupLoader).getVoxelSize(
					timepointId);
		}
		logger.warn("The loader " + this.imageSetupLoader.getClass().getName() +
			" is not recognized : ");
		return null;
	}
}
