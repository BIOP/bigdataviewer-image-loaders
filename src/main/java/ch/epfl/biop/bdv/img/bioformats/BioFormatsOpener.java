/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
import ch.epfl.biop.bdv.img.entity.Field;
import ch.epfl.biop.bdv.img.entity.Plate;
import ch.epfl.biop.bdv.img.opener.ChannelProperties;
import ch.epfl.biop.bdv.img.opener.Opener;
import ch.epfl.biop.bdv.img.ResourcePool;
import ch.epfl.biop.bdv.img.bioformats.entity.FileName;
import ch.epfl.biop.bdv.img.bioformats.entity.SeriesIndex;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
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
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private final boolean memoize;

	// -------- Pixels characteristics
	private final boolean isLittleEndian;
	private final boolean isRGB;
	private final boolean hasAlphaChannel;
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
			boolean to16Bits,
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

		// Reads potential disabling of memoization
		if (readerOptions.containsKey(OpenerSettings.BF_MEMO_KEY)) {
			memoize = Boolean.getBoolean(readerOptions.get(OpenerSettings.BF_MEMO_KEY));
		} else {
			memoize = true;
		}

		logger.debug("Unique key for bio-formats opener: "+rawPixelDataKey);
		logger.debug("Using memoization for bio-formats opener: "+memoize);

		this.filename = new File(dataLocation).getName();
		Integer currentIndexFilename = memoize("opener.bioformats.currentfileindex", cachedObjects, () -> 0);
		this.idxFilename = memoize("opener.bioformats.fileindex."+dataLocation+"."+options, cachedObjects, () -> {
			cachedObjects.put("opener.bioformats.currentfileindex", currentIndexFilename + 1 );
			return currentIndexFilename;
		});

		this.pool = memoize("opener.bioformats."+splitRGBChannels+"."+dataLocation+"."+options,
				cachedObjects,
				() -> {
					logger.debug("Creating pool for "+"opener.bioformats."+splitRGBChannels+"."+dataLocation+"."+options);
                    try {
                        return new ReaderPool(poolSize, true,
                                this::getNewReader, dataLocation.toUpperCase().trim().endsWith(".CZI") ); // Create base reader only for czi files
                    } catch (Exception e) {
						e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                });
		int pixelType;
		IFormatReader reader = null;
		try { // Indentation just for the pool / recycle operation -> force limiting the scope of reader
			reader = pool.acquire();
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
			this.hasAlphaChannel = reader.getSizeC() == 4;

			// Collect class of reader - helps with the special handling of ZeissQuickStartCZIReader
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
		} finally {
			if (reader != null) {
				pool.recycle(reader);
			}
		}

		this.to16Bits = to16Bits;
		this.t = to16Bits? new UnsignedShortType(): BioFormatsOpener.getBioformatsBdvSourceType(pixelType, this.isRGB, iSerie);

		if (!skipMeta) {

			AffineTransform3D rootTransform = BioFormatsHelper.getSeriesRootTransform(
					this.omeMeta, //metadata
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

			if (format.equals("Zeiss CZI (Quick Start)") && ZeissCZIQuickStartHelper.isLatticeLightSheet(this)) {
				// Adds an extra transformation - corresponding to skew of Zeiss LLS7

				AffineTransform3D latticeTransform = new AffineTransform3D();

				double angle = -60.0/180*Math.PI;

				latticeTransform.set(
						1,0,0,0,
						0,Math.cos(angle),0,0,
						0,+Math.sin(angle),-1,0
				);

				AffineTransform3D rotateX = new AffineTransform3D();
				rotateX.rotate(0, Math.PI/2.0);

				latticeTransform.preConcatenate(rotateX);
				AffineTransform3D addOffset = rootTransform.copy();

				double ox = addOffset.get(0,3);
				double oy = addOffset.get(1,3);
				double oz = addOffset.get(2,3);

				addOffset.identity();
				addOffset.set(ox,0,3);
				addOffset.set(oy,1,3);
				addOffset.set(oz,2,3);

				AffineTransform3D rmOffset = addOffset.inverse();

				latticeTransform.preConcatenate(addOffset);
				latticeTransform.concatenate(rmOffset);

				rootTransform.preConcatenate(latticeTransform);
			}

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
					if (BioFormatsOpener.this.format.equals("Zeiss CZI (Quick Start)")) {
						ZeissCZIQuickStartHelper.addCZIAdditionalEntities(entityList, BioFormatsOpener.this, iSerie, iChannel);
					}
					addPlateInfo(entityList, options, iSerie, cachedObjects);
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

	private void addPlateInfo(ArrayList<Entity> entityList,
									 String options,
									 int iSerie,
									 Map<String, Object> cachedObjects) {
		if (omeMeta.getPlateCount() == 0 ) return; // No plate information

		// Check that we can read information
		if (omeMeta.getPlateCount() > 1) {
			logger.warn("Plate information ignored: only bio-formats containing single wells are supported");
			return;
		}

		if (!(omeMeta.getRoot() instanceof OMEXMLMetadataRoot)) {
			logger.warn("Can't detect plate information since ome meta root is not of class OMEXMLMetadataRoot");
			return;
		}

		OMEXMLMetadataRoot r = (OMEXMLMetadataRoot) omeMeta.getRoot();
		ome.xml.model.Plate plate = r.getPlate(0);

		// Gets a unique identifier for the plate

		Integer currentPlateIndex = memoize("opener.bioformats.currentplateindex", cachedObjects, () -> 0);
		int idxPlate = memoize("opener.bioformats.plateIndex."+dataLocation+"."+options, cachedObjects, () -> {
			cachedObjects.put("opener.bioformats.currentplateindex", currentPlateIndex + 1 );
			return currentPlateIndex;
		});

		entityList.add(new Plate(idxPlate, plate.getName()));

		Map<Integer, WellSample> idToWellSample = memoize("opener.bioformats.idtowell."+dataLocation+"."+options, cachedObjects, () -> {
			Map<Integer, WellSample> idToWS = new HashMap<>();
			plate.copyWellList().forEach(well -> well.copyWellSampleList().forEach(ws -> {
				if (ws.getLinkedImage()!=null) {
					if (ws.getLinkedImage().getID()!=null) {
						// "Image:0"
						idToWS.put(Integer.parseInt(ws.getLinkedImage().getID().split(":")[1]), ws);
					}
				}
			}));
			return idToWS;
		});

		if (idToWellSample.containsKey(iSerie)) {
			WellSample ws = idToWellSample.get(iSerie);
			System.out.println("ws="+ws.getID());
			// WellSample:0:0:2
			int id = Integer.parseInt(ws.getID().split(":")[3]);
			entityList.add(new Field(id));
			if (ws.getWell()!=null) {
				Well w = ws.getWell();
				entityList.add(
						new ch.epfl.biop.bdv.img.entity.Well(
								Integer.parseInt(w.getID().split(":")[2]),
								(char)(w.getRow().getValue()+'A')+Integer.toString(w.getColumn().getValue()+1),
						w.getRow().getValue(), w.getColumn().getValue()));
			}
		}

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

		if (imageName == null || imageName.isEmpty()) {
			imageName = fileNameWithoutExtension;
			return imageName + "-s" + iSerie;
		}
		else {
			return imageName;
		}
	}

	// create OME-XML metadata store
	static final ServiceFactory factory;
	static final OMEXMLService service;

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
				if (opts[i].trim().equals("--bfOptions")) {
					i++;
					String[] kv = opts[i].split("=");
					if (kv.length!=2) {
						kv = opts[i+1].split("\\u003d");
					}
					if (kv.length==2) {
						if (kv[0].trim().startsWith("-")) {
							kv[0] = kv[0].substring(1).trim();
						}
						readerOptions.put(kv[0], kv[1].trim());
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

	private static final Pattern ZARR_FILE_PATTERN = Pattern.compile("\\.zarr/?(\\d+/?)?$");
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
		IFormatReader reader;
		// Copied from QuPath logic: https://github.com/qupath/qupath/blob/f9c7622c899653b52ebd6f586b038a8fcf193372/qupath-extension-bioformats/src/main/java/qupath/lib/images/servers/bioformats/BioFormatsImageServer.java#L1298C4-L1305C5
		Matcher zarrMatcher = ZARR_FILE_PATTERN.matcher(dataLocation.toLowerCase());
		if (new File(dataLocation).isDirectory() || zarrMatcher.find()) {
			try {
				Class<?> zarrReaderClass = Class.forName("loci.formats.in.ZarrReader"); // Avoid import in case the dependency is not there
				reader = (IFormatReader) zarrReaderClass.getDeclaredConstructor().newInstance();

				if (dataLocation.startsWith("https")) {
					Object metadataOptions = reader.getMetadataOptions();
					if (metadataOptions instanceof DynamicMetadataOptions) {
						DynamicMetadataOptions zarrOptions = (DynamicMetadataOptions) metadataOptions;
						zarrOptions.set("omezarr.alt_store", dataLocation);
					}
				}
			} catch (ClassNotFoundException e) {
				logger.error("Attempt to open OME ZARR Dataset but the package is not installed. Please check https://github.com/ome/ZarrReader to get it installed");
				return null;
			} catch (Exception e) {
				logger.error("Failed to initialize ZarrReader: " + e.getMessage());
				return null; // Fallback
			}
		} else {
			reader = new ImageReader();
		}

		reader.setFlattenedResolutions(false);

		if (!readerOptions.isEmpty()) {
			MetadataOptions metadataOptions = reader.getMetadataOptions();
			if (metadataOptions instanceof DynamicMetadataOptions) {
				// We need to set a xml metadata backend or else a Dummy metadata store is created and
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

		if (memoize) {
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
			if (pt == FormatTools.UINT32) {
				return new UnsignedIntType();
			}
			if (pt == FormatTools.INT16) {
				return new ShortType();
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
	}

	/**
	 * Checks whether this opener converts bytes to shorts - that's for bvv compatibility
	 * @return whether the data is converted to 16 bits
	 */
	public boolean to16bit() {
		return to16Bits;
	}

	boolean to16Bits = false;

	public boolean hasAlphaChannel() {
		return hasAlphaChannel;
	}

	private static class ReaderPool extends ResourcePool<IFormatReader> {

		final Supplier<IFormatReader> readerSupplier;
		final IFormatReader model;

		public ReaderPool(int size, Boolean dynamicCreation,
						  Supplier<IFormatReader> readerSupplier, boolean createBase) throws Exception {
			super(size, dynamicCreation);
			this.readerSupplier = readerSupplier;
			if (createBase) {
				model = this.acquire();
			} else {
				model = null;
			}
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

		volatile boolean modelHasBeenRecycled = false;

		@Override
		public synchronized void shutDown(Consumer<IFormatReader> closer) {
			if (!modelHasBeenRecycled) {
				modelHasBeenRecycled = true;
				if (model != null) {
					try {
						recycle(model);
					} catch (Exception e) {
						e.printStackTrace();
					}
					closer.accept(model);
				}
			}
			super.shutDown(closer);
		}

	}
}
