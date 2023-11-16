/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2023 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.bioformats;

import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.OpenerSetupLoader;
import ch.epfl.biop.bdv.img.opener.ChannelProperties;
import ch.epfl.biop.bdv.img.opener.Opener;
import ch.epfl.biop.bdv.img.ResourcePool;
import ch.epfl.biop.bdv.img.bioformats.entity.FileName;
import ch.epfl.biop.bdv.img.bioformats.entity.SeriesIndex;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static ch.epfl.biop.bdv.img.opener.OpenerHelper.memoize;

/**
 * {@link Opener} implementation for Bio-Formats backed dataset
 */

public class BioFormatsOpener implements Opener<IFormatReader> {

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsOpener.class);

	// -------- How to open the dataset (reader pool, transforms)
	//protected Consumer<IFormatReader> readerModifier = (e) -> {};
	private final ReaderPool pool;
	// private AffineTransform3D rootTransform;

	// -------- Opener core options
	private final int nTimePoints;
	// private String imageName;
	private final String format;
	private final IMetadata omeMeta;
	private final String dataLocation;

	// -------- Pixels characteristics
	private final boolean isLittleEndian;
	private final boolean isRGB;
	private final VoxelDimensions voxelDimensions;
	private final Type<? extends NumericType<?>> t;

	// -------- Resolutions options
	private final int[] cellDimensions;
	private final int nMipMapLevels;

	// -------- Image dimensions
	private final Dimensions[] dimensions;

	// -------- Channel options and properties
	//private List<ChannelProperties> channelPropertiesList;
	private final boolean splitRGBChannels;
	private final int nChannels;

	// -------- Series
	private final int iSerie;

	// --------
	private final String rawPixelDataKey;
	private final String filename;
	private final int idxFilename;
	private final OpenerMeta meta;

	private final Map<String, String> readerOptions;

	IFormatReader model;

	/**
	 *
	 * @param context
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
	 * @param splitRGBChannels
	 * @param cachedObjects
	 * @param defaultNumberOfChannels
	 * @param skipMeta
	 * @throws Exception
	 */
	public BioFormatsOpener(
			Context context, // not used
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
			int[] cacheBlockSize,
			// channel options
			boolean splitRGBChannels,
			// Optimisation : reuse from existing openers
			Map<String, Object> cachedObjects,
			int defaultNumberOfChannels,
			boolean skipMeta,
			String options
	) throws Exception {

		if (iSerie<0) throw new IllegalStateException("Invalid series number for file "+dataLocation+" iSerie = "+iSerie+" requested");

		// ------------

		this.readerOptions = bfOptionsToMap(options);

		this.dataLocation = dataLocation;
		this.iSerie = iSerie;
		this.splitRGBChannels = splitRGBChannels;

		// Should be unique to raw pixel data, we don't care if the units are different
		String buildRawPixelDataKey = "opener.bioformats"
						+"."+splitRGBChannels
						+"."+dataLocation
						+"."+iSerie
						+"."+options;

		if (!useDefaultXYBlockSize) {
			buildRawPixelDataKey += "."+ Arrays.toString(cacheBlockSize);
		}

		this.rawPixelDataKey = buildRawPixelDataKey;

		this.filename = new File(dataLocation).getName();
		Integer currentIndexFilename = memoize("opener.bioformats.currentfileindex", cachedObjects, () -> 0);
		this.idxFilename = memoize("opener.bioformats.fileindex."+dataLocation+"."+options, cachedObjects, () -> {
			cachedObjects.put("opener.bioformats.currentfileindex", currentIndexFilename + 1 );
			return currentIndexFilename;
		});

		this.model = this.getNewReader();
		this.pool = memoize("opener.bioformats."+splitRGBChannels+"."+dataLocation+"."+options,
				cachedObjects,
				() -> new ReaderPool(poolSize, true,
						this::getNewReader, model));
		int pixelType;
		{ // Indentation just for the pool / recycle operation -> force limiting the scope of reader
			IFormatReader reader = pool.takeOrCreate();
			reader.setSeries(iSerie);
			this.omeMeta = (IMetadata) reader.getMetadataStore();
			nChannels = this.omeMeta.getChannelCount(iSerie);//reader.getSizeC();
			this.nMipMapLevels = reader.getResolutionCount();
			this.nTimePoints = reader.getSizeT();

			Unit<Length> u = BioFormatsHelper.getUnitFromString(unit);
			if (u == null) {
				logger.error("Could not find matching length unit from String: "+unit);
				u = UNITS.REFERENCEFRAME;
			}

			this.voxelDimensions = BioFormatsHelper.getSeriesVoxelDimensions(this.omeMeta,
					this.iSerie, u, defaultVoxelUnit);
			this.isLittleEndian = reader.isLittleEndian();
			this.isRGB = reader.isRGB();
			this.format = reader.getFormat();

			this.cellDimensions = new int[] {
					useDefaultXYBlockSize ? reader.getOptimalTileWidth() : cacheBlockSize[0],
					useDefaultXYBlockSize ? reader.getOptimalTileHeight() : cacheBlockSize[1],
					useDefaultXYBlockSize ? 1 : cacheBlockSize[2] };

			this.dimensions = new Dimensions[this.nMipMapLevels];
			for (int level = 0; level < this.nMipMapLevels; level++) {
				reader.setResolution(level);
				this.dimensions[level] = getDimension(reader.getSizeX(), reader.getSizeY(), reader.getSizeZ());
			}
			pixelType = reader.getPixelType();
			pool.recycle(reader);
		}

		this.t = BioFormatsOpener.getBioformatsBdvSourceType(pixelType, this.isRGB, iSerie);

		if (!skipMeta) {

			AffineTransform3D rootTransform = BioFormatsHelper.getSeriesRootTransform(
					this.omeMeta, //metadata
					model,
					iSerie, // serie
					BioFormatsHelper.getUnitFromString(unit), // unit
					positionPreTransformMatrixArray, // AffineTransform3D for positionPreTransform,
					positionPostTransformMatrixArray, // AffineTransform3D for positionPostTransform,
					defaultSpaceUnit,
					positionIsImageCenter, // boolean positionIsImageCenter,
					new AffineTransform3D().getRowPackedCopy(), // voxSizePreTransform,
					new AffineTransform3D().getRowPackedCopy(), // voxSizePostTransform,
					defaultVoxelUnit,
					new boolean[]{false, false, false} // axesOfImageFlip
			);

			String imageName = getImageName(this.omeMeta,iSerie,dataLocation);
			List<ChannelProperties> channelPropertiesList = getChannelProperties(this.omeMeta, iSerie, this.nChannels);

			meta = new OpenerMeta() {

				@Override
				public ChannelProperties getChannel(int iChannel) {
					if(iChannel >= nChannels) {
						logger.error("You are trying to get the channel " + iChannel + " in an image with only " + nChannels);
						return null;
					}
					return channelPropertiesList.get(iChannel);
				}

				@Override
				public List<Entity> getEntities(int iChannel) {
					ArrayList<Entity> entityList = new ArrayList<>();
					entityList.add(new FileName(idxFilename, filename));
					entityList.add(new SeriesIndex(iSerie));
					return entityList;
				}

				@Override
				public String getImageName() {
					return imageName;
				}

				@Override
				public AffineTransform3D getTransform() {
					return rootTransform;
				}

			};
		} else meta = null;
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
					.setDisplayRange(0,255)
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
	private static String getImageName(IMetadata omeMeta, int iSerie, String dataLocation){
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

	// create OME-XML metadata store
	static ServiceFactory factory;
	static OMEXMLService service;

	static {
		try {
			factory = new ServiceFactory();
			service = factory.getInstance(OMEXMLService.class);
		} catch (DependencyException e) {
			throw new RuntimeException(e);
		}
	}

	public static Map<String, String> bfOptionsToMap(String options) {

		Map<String, String> readerOptions = new LinkedHashMap<>();
		// Parse options into metadata options
		try {
			String[] opts = options.split(" ");
			int i = 0;
			while (i<opts.length) {
				if (opts[i].equals("--bfOptions")) {
					i++;
					String[] kv = opts[i].split("=");
					if (kv.length!=2) {
						kv = opts[i+1].split("\\u003d");
					}
					if (kv.length==2) {
						if (kv[0].startsWith("-")) {
							kv[0] = kv[0].substring(1);
						}
						readerOptions.put(kv[0], kv[1]);
					}
				}
				i++;
			}
		} catch (Exception e) {
			System.err.println("Could not parse bio formats args: "+options);
			e.printStackTrace();
		}
		return readerOptions;
	}

	/**
	 * Build a new IFormatReader to retrieve all pixels and channels information of an image opened
	 * with BioFormats.
	 * <p>
	 * Be careful : calling this method can take some time.
	 *
	 * @return the reader
	 */
	public IFormatReader getNewReader() {
		logger.debug("Getting new reader for " + dataLocation);
		IFormatReader reader = new ImageReader();
		reader.setFlattenedResolutions(false);

		if (!readerOptions.isEmpty()) {
			MetadataOptions metadataOptions = reader.getMetadataOptions();
			if (metadataOptions instanceof DynamicMetadataOptions) {
				// We need to set an xml metadata backend or else a Dummy metadata store is created and
				// all metadata are discarded
				try {
					reader.setMetadataStore(service.createOMEXMLMetadata());
				} catch (Exception e) {
					e.printStackTrace();
				}
				for (Map.Entry<String,String> option : readerOptions.entrySet()) {
					logger.debug("setting reader option:"+option.getKey()+":"+option.getValue());
					((DynamicMetadataOptions)metadataOptions).set(option.getKey(), option.getValue());
				}
			}
		}

		if (splitRGBChannels) {
			reader = new ChannelSeparator(reader);
		}

		if (readerOptions.isEmpty()) { // Can't memoize with bf Options
			Memoizer memo = new Memoizer(reader);
			try {
				memo.setId(dataLocation);
			} catch (FormatException | IOException e) {
				e.printStackTrace();
			}
			return memo;
		} else {
			try {
				reader.setId(dataLocation);
			}
			catch (FormatException | IOException e) {
				e.printStackTrace();
			}
			return reader;
		}

	}

	/**
	 *
	 * @param pt bioformats compatible pixel type
	 * @param isReaderRGB is pixel RGB
	 * @param image_index
	 * @return the bdv compatible pixel type
	 * @throws UnsupportedOperationException
	 */
	private static Type<? extends NumericType<?>> getBioformatsBdvSourceType(int pt, boolean isReaderRGB,
																		  int image_index) throws UnsupportedOperationException
	{
		if (isReaderRGB) {
			if (pt == FormatTools.UINT8) {
				return new ARGBType();
			}
			else {
				throw new UnsupportedOperationException("Unhandled 16 bits RGB images");
			}
		}
		else {
			if (pt == FormatTools.UINT8) {
				return new UnsignedByteType();
			}
			if (pt == FormatTools.UINT16) {
				return new UnsignedShortType();
			}
			if (pt == FormatTools.INT32) {
				return new IntType();
			}
			if (pt == FormatTools.FLOAT) {
				return new FloatType();
			}
		}
		throw new UnsupportedOperationException("Unhandled pixel type for serie " +
				image_index + ": " + pt);
	}

	// GETTERS
	@Override
	public String getImageFormat() {return this.format;}

	/**
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
	public ResourcePool<IFormatReader> getPixelReader() {
		return this.pool;
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {
		return this.voxelDimensions;
	}

	@Override
	public boolean isLittleEndian() {
		return this.isLittleEndian;
	}

	@Override
	public OpenerSetupLoader<?, ?, ?> getSetupLoader(int channelIdx, int setupIdx, Supplier<VolatileGlobalCellCache> cacheSupplier) {
		return new BioFormatsSetupLoader(this,
				channelIdx, this.iSerie, setupIdx, (NumericType) this.getPixelType(), OpenerHelper.getVolatileOf((NumericType) this.getPixelType()), cacheSupplier);
	}

	@Override
	public String getRawPixelDataKey() {
		return rawPixelDataKey;
	}

	@Override
	public OpenerMeta getMeta() {
		return meta;
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
	public Type<? extends NumericType<?>> getPixelType() {
		return this.t;
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
		if (model!=null) {
			try {
				model.close();
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}

	private static class ReaderPool extends ResourcePool<IFormatReader> {

		final Supplier<IFormatReader> readerSupplier;
		final IFormatReader model;

		public ReaderPool(int size, Boolean dynamicCreation,
						  Supplier<IFormatReader> readerSupplier)
		{
			super(size, dynamicCreation);
			createPool();
			this.readerSupplier = readerSupplier;
			model = null;
		}

		public ReaderPool(int size, Boolean dynamicCreation,
						  Supplier<IFormatReader> readerSupplier,
						  IFormatReader model)
		{
			super(size, dynamicCreation);
			this.model = model;
			createPool();
			this.readerSupplier = readerSupplier;
		}

		@Override
		public IFormatReader createObject() {
			// Line below: optimisation for CZI reader and Lattice Light Sheet dataset
			// It is complicated because it needs to work for the standard bio-formats version
			// and for the modified bio-formats version with the lattice light sheet reader
			if ((model!=null)&&(BioFormatsHelper.hasCopyMethod(model))) {
				return BioFormatsHelper.copy(model);
			}
			return readerSupplier.get();
		}
	}
}
