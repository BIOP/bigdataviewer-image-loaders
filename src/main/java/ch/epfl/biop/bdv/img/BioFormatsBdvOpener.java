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

package ch.epfl.biop.bdv.img;

import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import ome.model.units.Unit;
import ome.units.quantity.Length;
import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BioFormatsBdvOpener implements Opener<IFormatReader> {

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsBdvOpener.class);

	transient protected Consumer<IFormatReader> readerModifier = (e) -> {};

	//public OpenerSettings settings;
	private final ReaderPool pool;
	private int nTimePoints;
	private IMetadata omeMeta;
	private boolean isLittleEndian;
	private boolean isRGB;
	private int[] cellDimensions;
	private Dimensions[] dimensions;
	private String format;
	private int nMipMapLevels;
	private VoxelDimensions voxelDimensions;
	private int serieCount;

	private int iSerie;

	private final String dataLocation;

	private final boolean splitRGBChannels;
	private final boolean swZC;
	private AffineTransform3D rootTransform;

	public BioFormatsBdvOpener(
			String dataLocation,
			int iSerie,
			// Location of the image
			double[] positionPreTransformMatrixArray,
			double[] positionPostTransformMatrixArray,
			boolean positionIsImageCenter,
			Length defaultSpaceUnit,
			Length defaultVoxelUnit,
			String unit,
			// How to stream it
			int poolSize,
			boolean useDefaultXYBlockSize,
			FinalInterval cacheBlockSize,
			boolean swZC,
			boolean splitRGBChannels

	//		OpenerSettings settings
	) {
		this.dataLocation = dataLocation;
		this.iSerie = iSerie;
		this.splitRGBChannels = splitRGBChannels;
		this.swZC = swZC;
		this.pool = new ReaderPool(poolSize, true, this::getNewReader);

		try (IFormatReader reader = getNewReader()) {
			this.serieCount = reader.getSeriesCount();
			this.omeMeta = (IMetadata) reader.getMetadataStore();

			this.iSerie = reader.getSeries();
			System.out.println("Try to catch the serie "+this.iSerie+" from the reader but I dont know if it is possible");
			reader.setSeries(this.iSerie);
			this.nMipMapLevels = reader.getResolutionCount();
			this.nTimePoints = reader.getSizeT();
			this.voxelDimensions = BioFormatsTools.getSeriesVoxelDimensions(this.omeMeta,
					this.iSerie, BioFormatsTools.getUnitFromString(unit), defaultVoxelUnit);
			this.isLittleEndian = reader.isLittleEndian();
			this.isRGB = reader.isRGB();
			this.format = reader.getFormat();

			this.cellDimensions = new int[] {
					useDefaultXYBlockSize ? reader.getOptimalTileWidth() : (int) cacheBlockSize.dimension(0),
					useDefaultXYBlockSize ? reader.getOptimalTileHeight() : (int) cacheBlockSize.dimension(1),
					useDefaultXYBlockSize ? 1 : (int) cacheBlockSize.dimension(2) };

			this.dimensions = new Dimensions[this.nMipMapLevels];
			for (int level = 0; level < this.nMipMapLevels; level++) {
				reader.setResolution(level);
				this.dimensions[level] = getDimension(reader.getSizeX(), reader.getSizeY(), reader.getSizeZ());
			}

			rootTransform = BioFormatsTools
					.getSeriesRootTransform(this.omeMeta, this.iSerie, BioFormatsTools.getUnitFromString(unit),
							positionPreTransformMatrixArray, // AffineTransform3D
							// positionPreTransform,
							positionPostTransformMatrixArray, // AffineTransform3D
							// positionPostTransform,
							defaultSpaceUnit,
							positionIsImageCenter, // boolean positionIsImageCenter,
							new AffineTransform3D().getRowPackedCopy(), // voxSizePreTransform,
							new AffineTransform3D().getRowPackedCopy(), // AffineTransform3D
							// voxSizePostTransform,
							defaultVoxelUnit,
							//, // null, //Length
							// voxSizeReferenceFrameLength,
							new boolean[]{false, false, false} // axesOfImageFlip
					);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public IFormatReader getNewReader() {
		logger.debug("Getting new reader for " + dataLocation);
		IFormatReader reader = new ImageReader();
		reader.setFlattenedResolutions(false);
		if (splitRGBChannels) {
			reader = new ChannelSeparator(reader);
		}
		Memoizer memo = new Memoizer(reader);

		final IMetadata omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
		memo.setMetadataStore(omeMetaIdxOmeXml);
		readerModifier.accept(memo); // Specific modifications of the genrated
																	// readers

		try {
			logger.debug("setId for reader " + dataLocation);
			StopWatch watch = new StopWatch();
			watch.start();
			memo.setId(dataLocation);
			memo.setSeries(iSerie);
			watch.stop();
			logger.debug("id set in " + (int) (watch.getTime() / 1000) + " s");

		}
		catch (FormatException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return memo;
	}

	static Dimensions getDimension(long sizeX, long sizeY, long sizeZ) {
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
	public int getNumMipmapLevels() {
		return this.nMipMapLevels;
	}

	@Override
	public int getNTimePoints() {
		return this.nTimePoints;
	}

	@Override
	public AffineTransform3D getTransform() {
		return rootTransform;
	}

	@Override
	public ResourcePool<IFormatReader> getPixelReader() {
		return this.pool;
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {
		return this.voxelDimensions;
	}

	public boolean getSwitchZAndT() {
		return swZC;
	}

	@Override
	public int[] getCellDimensions(int level) {
		return cellDimensions;
	}

	@Override
	public Dimensions[] getDimensions() {
		return this.dimensions;
	}

	public String getReaderFormat() {
		return this.format;
	}

	public Boolean getEndianness() {
		return this.isLittleEndian;
	}

	public Boolean getRGB() {
		return this.isRGB;
	}

	public IMetadata getMetadata() {
			return this.omeMeta;
	}

	static class ReaderPool extends ResourcePool<IFormatReader> {

		final Supplier<IFormatReader> readerSupplier;

		public ReaderPool(int size, Boolean dynamicCreation,
						  Supplier<IFormatReader> readerSupplier)
		{
			super(size, dynamicCreation);
			createPool();
			this.readerSupplier = readerSupplier;
		}

		@Override
		public IFormatReader createObject() {
			return readerSupplier.get();
		}

	}

}
