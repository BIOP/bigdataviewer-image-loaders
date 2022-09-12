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
import ch.epfl.biop.bdv.img.bioformats.entity.ChannelName;
import ch.epfl.biop.bdv.img.bioformats.entity.SeriesNumber;
import ch.epfl.biop.bdv.img.bioformats.entity.BioFormatsUri;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.units.quantity.Length;
import ome.xml.model.enums.PixelType;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Contains all BioFormats-specific methods and fields necessary to open an image.
 */
public class BioFormatsBdvOpener implements Opener<IFormatReader> {

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsBdvOpener.class);

	// -------- How to open the dataset (reader pool, transforms)
	protected Consumer<IFormatReader> readerModifier = (e) -> {};
	private final ReaderPool pool;
	private AffineTransform3D rootTransform;


	// -------- Opener core options
	private int nTimePoints;
	private String imageName;
	private String format;
	private IMetadata omeMeta;
	private final String dataLocation;


	// -------- Pixels characteristics
	private boolean isLittleEndian;
	private boolean isRGB;
	private VoxelDimensions voxelDimensions;
	Type<? extends NumericType> t;


	// -------- Resolutions options
	private int[] cellDimensions;
	private int nMipMapLevels;


	// -------- Image dimensions
	private Dimensions[] dimensions;


	// -------- Channel options and properties
	private List<ChannelProperties> channelPropertiesList;
	private final boolean splitRGBChannels;
	private final boolean swZC;
	int nChannels;


	// -------- Series
	private int iSerie;

	/**
	 * Class constructor : sets all fields
	 *
	 * @param dataLocation
	 * @param iSerie
	 * @param positionPreTransformMatrixArray
	 * @param positionPostTransformMatrixArray
	 * @param positionIsImageCenter
	 * @param defaultSpaceUnit
	 * @param defaultVoxelUnit
	 * @param unit
	 * @param poolSize
	 * @param useDefaultXYBlockSize
	 * @param cacheBlockSize
	 * @param swZC
	 * @param splitRGBChannels
	 * @throws URISyntaxException
	 */
	public BioFormatsBdvOpener(
			// opener core option
			String dataLocation,
			int iSerie,
			// Location of the image
			double[] positionPreTransformMatrixArray,
			double[] positionPostTransformMatrixArray,
			boolean positionIsImageCenter,
			// units
			Length defaultSpaceUnit,
			Length defaultVoxelUnit,
			String unit,
			// How to stream it
			int poolSize,
			boolean useDefaultXYBlockSize,
			FinalInterval cacheBlockSize,
			// channel options
			boolean swZC,
			boolean splitRGBChannels
	) throws URISyntaxException {

		this.dataLocation = dataLocation;
		this.iSerie = iSerie;
		this.splitRGBChannels = splitRGBChannels;
		this.swZC = swZC;
		this.pool = new ReaderPool(poolSize, true, this::getNewReader);

		// open the reader and get all necessary information
		try (IFormatReader reader = getNewReader()) {
			this.omeMeta = (IMetadata) reader.getMetadataStore();
			this.nChannels = this.omeMeta.getChannelCount(iSerie);
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
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.rootTransform = BioFormatsTools.getSeriesRootTransform(
				this.omeMeta, //metadata
				iSerie, // serie
				BioFormatsTools.getUnitFromString(unit), // unit
				positionPreTransformMatrixArray, // AffineTransform3D for positionPreTransform,
				positionPostTransformMatrixArray, // AffineTransform3D for positionPostTransform,
				defaultSpaceUnit,
				positionIsImageCenter, // boolean positionIsImageCenter,
				new AffineTransform3D().getRowPackedCopy(), // voxSizePreTransform,
				new AffineTransform3D().getRowPackedCopy(), // voxSizePostTransform,
				defaultVoxelUnit,
				new boolean[]{false, false, false} // axesOfImageFlip
				);

		this.imageName = getImageName(this.omeMeta,iSerie,dataLocation);
		this.t = BioFormatsBdvOpener.getBioformatsBdvSourceType(this.omeMeta.getPixelsType(iSerie), this.isRGB, iSerie);
		this.channelPropertiesList = getChannelProperties(this.omeMeta, iSerie, this.nChannels);
	}


	/**
	 * Build a channelProperties object for each image channel.
	 * @param omeMeta = image metadata
	 * @param iSerie : serie ID
	 * @param nChannels : number of channel
	 * @return list of ChannelProperties objects
	 */
	private List<ChannelProperties> getChannelProperties(IMetadata omeMeta, int iSerie, int nChannels){
		List<ChannelProperties> channelPropertiesList = new ArrayList<>();
		for(int i = 0; i < nChannels; i++){
			channelPropertiesList.add(new ChannelProperties(i)
					.setNChannels(nChannels)
					.setChannelName(iSerie,omeMeta)
					.setEmissionWavelength(iSerie,omeMeta)
					.setExcitationWavelength(iSerie,omeMeta)
					.setChannelColor(iSerie,omeMeta)
					.setRGB(this.isRGB)
					.setPixelType(this.t)
					.setDynamicRange()
			);

		}
		return channelPropertiesList;
	}


	/**
	 *
	 * @param omeMeta = image metadata
	 * @param iSerie : serie ID
	 * @param dataLocation : path of the image
	 * @return the name of the image with the serie ID and without extension.
	 */
	private String getImageName(IMetadata omeMeta, int iSerie, String dataLocation){
		String imageName = omeMeta.getImageName(iSerie);
		String fileNameWithoutExtension = FilenameUtils.removeExtension(new File(dataLocation).getName());
		fileNameWithoutExtension = fileNameWithoutExtension.replace(".ome", ""); // above only removes .tif

		if (imageName == null || imageName.equals("")) {
			imageName = fileNameWithoutExtension;
			return imageName + "-s" + iSerie;
		}
		else {
			return imageName;
		}
	}


	/**
	 * Build a new IFormatReader to retrieve all pixels and channels information of an image opened
	 * with BioFormats.
	 *
	 * Be careful : calling this method can take some time. Need to call it as less times as possible.
	 *
	 * @return the reader
	 */
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
			logger.info("setId for reader " + dataLocation);
			StopWatch watch = new StopWatch();
			watch.start();
			memo.setId(dataLocation); // take some time
			memo.setSeries(iSerie); // because one opener per serie
			watch.stop();
			logger.info("id set in " + (int) (watch.getTime() / 1000) + " s");
		}
		catch (FormatException | IOException e) {
			e.printStackTrace();
		}
		return memo;
	}

	/**
	 *
	 * @param pt bioformats compatible pixel type
	 * @param isReaderRGB is pixel RGB
	 * @param image_index
	 * @return the bdv compatible pixel type
	 * @throws UnsupportedOperationException
	 */
	private static Type<? extends NumericType> getBioformatsBdvSourceType(PixelType pt, boolean isReaderRGB,
																		  int image_index) throws UnsupportedOperationException
	{
		if (isReaderRGB) {
			if (pt == PixelType.UINT8) {
				return new ARGBType();
			}
			else {
				throw new UnsupportedOperationException("Unhandled 16 bits RGB images");
			}
		}
		else {
			if (pt == PixelType.UINT8) {
				return new UnsignedByteType();
			}
			if (pt == PixelType.UINT16) {
				return new UnsignedShortType();
			}
			if (pt == PixelType.INT32) {
				return new IntType();
			}
			if (pt == PixelType.FLOAT) {
				return new FloatType();
			}
		}
		throw new UnsupportedOperationException("Unhandled pixel type for serie " +
				image_index + ": " + pt);
	}


	// GETTERS
	public boolean getSwitchZAndT() {
		return swZC;
	}
	public String getReaderFormat() {
		return this.format;
	}
	public Boolean getIsLittleEndian() {
		return this.isLittleEndian;
	}


	/**
	 *
	 * @param sizeX
	 * @param sizeY
	 * @param sizeZ
	 * @return image dimensions
	 */
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

	// OVERRIDDEN METHODS
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

	@Override
	public int[] getCellDimensions(int level) {
		return cellDimensions;
	}

	@Override
	public Dimensions[] getDimensions() {
		return this.dimensions;
	}

	@Override
	public int getNChannels() {
		return nChannels;
	}

	@Override
	public Type<? extends NumericType> getPixelType() {
		return this.t;
	}

	@Override
	public ChannelProperties getChannel(int iChannel) {
		if(iChannel >= this.nChannels) {
			logger.error("You are trying to get1 the channel " + iChannel + " in an image with only " + this.nChannels);
			return null;
		}
		return this.channelPropertiesList.get(iChannel);
	}

	@Override
	public List<Entity> getEntities(int iChannel) {
		ArrayList<Entity> entityList = new ArrayList<>();

		entityList.add(new SeriesNumber(iSerie, this.imageName));
		entityList.add(new BioFormatsUri(0, dataLocation));
		entityList.add(new ChannelName(0, channelPropertiesList.get(iChannel).getChannelName()));

		return entityList;
	}

	@Override
	public String getImageName() {
		return this.imageName;
	}


	@Override
	public void close() {
		getPixelReader().shutDown(reader -> {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
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
