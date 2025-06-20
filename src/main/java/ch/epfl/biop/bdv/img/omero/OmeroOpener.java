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

package ch.epfl.biop.bdv.img.omero;

import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.OpenerSetupLoader;
import ch.epfl.biop.bdv.img.opener.ChannelProperties;
import ch.epfl.biop.bdv.img.opener.Opener;
import ch.epfl.biop.bdv.img.ResourcePool;
import ch.epfl.biop.bdv.img.omero.entity.OmeroHostId;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.omero.OMEROSession;
import net.imglib2.Dimensions;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileIntType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import ome.model.units.BigResult;
import omero.ServerError;
import omero.api.RawPixelsStorePrx;
import omero.api.ResolutionDescription;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.MetadataFacility;
import omero.gateway.model.ChannelData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.model.*;
import omero.model.enums.UnitsLength;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static ch.epfl.biop.bdv.img.opener.OpenerHelper.memoize;
import static omero.gateway.model.PixelsData.FLOAT_TYPE;
import static omero.gateway.model.PixelsData.UINT16_TYPE;
import static omero.gateway.model.PixelsData.UINT32_TYPE;
import static omero.gateway.model.PixelsData.UINT8_TYPE;

/**
 * Contains parameters that explain how to open all channel sources from an
 * Omero Image
 */
public class OmeroOpener implements Opener<RawPixelsStorePrx> {

	protected static final Logger logger = LoggerFactory.getLogger(OmeroOpener.class);

	// -------- How to open the dataset (reader pool, transforms)
	final RawPixelsStorePool pool;

	// -------- handle OMERO connection
	final Gateway gateway;
	final SecurityContext securityContext;

	// -------- Image characteristics
	final long omeroImageID;
	final String datalocation;
	//String imageName;
	final Map<Integer, int[]> imageSize;
	final Map<Integer, int[]> tileSize;


	// -------- Resolutions levels
	final int nMipmapLevels;

	// -------- TimePoints
	final int nTimePoints;

	// -------- Pixels characteristics
	final Type<? extends NumericType<?>> pixelType;
	final String unit;
	final double psizeX;
	final double psizeY;
	final double psizeZ;
	final long pixelsID;

	// -------- Channel options and characteristics
	final int nChannels;

	// -------
	final String rawPixelDataKey;

	// GETTERS
	public int getSizeX(int level) {
		return this.imageSize.get(level)[0];
	}
	public int getSizeY(int level) {
		return this.imageSize.get(level)[1];
	}
	public int getSizeZ(int level) {
		return this.imageSize.get(level)[2];
	}
	public int getTileSizeX(int level) {
		return this.tileSize.get(level)[0];
	}
	public int getTileSizeY(int level) {
		return this.tileSize.get(level)[1];
	}

	Exception exception = null;
	final String host;

	final OpenerMeta meta;

	final String format;

	/**
	 * Builder pattern: fills all the omerosourceopener fields that relates to the
	 * image to open (i.e. image size for all resolution levels..)
	 *
	 */
	public OmeroOpener(
			Context context,
			String datalocation,
			int poolSize,
			String unit,
			boolean positionIsImageCenter,
			// Optimisation : reuse from existing openers
			Map<String, Object> cachedObjects,
			int defaultNumberOfChannels,
			boolean skipMeta
	) throws Exception {
		//System.out.println(datalocation);
		URL url = new URL(datalocation);
		host = url.getHost();

		// We don't want to ask again and again and again credentials if it failed once. Thus we memoize the potential error
		if (cachedObjects.containsKey("opener.omero.connect."+host+".error")) throw new RuntimeException("Connection to OMERO failed");

		OMEROSession session = null;
		try {
			session = OmeroHelper.getGatewayAndSecurityContext(context, host);
		} catch (Exception e) {
			cachedObjects.put("opener.omero.connect."+host+".error", e);
			exception = e;
		}

		assert session!=null;

		if (exception != null) {
			// avoid asking again and again connection credentials
			throw exception;
		}

		this.gateway = session.getGateway();
		this.securityContext = session.getSecurityContext();

		List<Long> imageIDs = OmeroHelper.getImageIDs(datalocation, gateway, securityContext);

		if (imageIDs.isEmpty()) throw new IllegalStateException("Could not found an image ID in url "+datalocation);
		if (imageIDs.size()>1) throw new UnsupportedOperationException("Could not open multiple Omero IDs in a single URL, split them.");

		long imageID = imageIDs.get(0);

		// get pixels
		PixelsData pixels = memoize("opener.omero.pixels."+host+"."+imageID, cachedObjects, () -> {
			try {
				return getPixelsDataFromOmeroID(imageID, gateway, securityContext);
			} catch (Exception e) {
				exception = e;
				throw new RuntimeException(e);
			}
		});
		if (exception != null) throw exception;

		this.pixelsID = pixels.getId();
		logger.debug("Opener :" +this+" pixel.getID : "+this.pixelsID);
		this.omeroImageID = imageID;
		this.unit = unit;
		this.datalocation = datalocation;

		rawPixelDataKey = "opener.omero."+host+"."+omeroImageID;

		// create a new reader pool
		this.pool = memoize("opener.omero.pool."+host+"."+imageID, cachedObjects, () -> {
			logger.debug("Creating pool for "+"opener.omero.pool."+host+"."+imageID);
			return new RawPixelsStorePool(poolSize, true, this::getNewStore);
		});

		// get the current pixel store
		{
			RawPixelsStorePrx rawPixStore = gateway.getPixelsStore(securityContext);

			rawPixStore.setPixelsId(this.pixelsID, false);

			// populate information on image dimensions and resolution levels
			this.nMipmapLevels = rawPixStore.getResolutionLevels();
			this.imageSize = new HashMap<>();

			// Optimize time if there is only one resolution level because
			// getResolutionDescriptions() is time-consuming // TODO : WHAT ??
			if (this.nMipmapLevels == 1) {
				imageSize.put(0, new int[] { pixels.getSizeX(), pixels.getSizeY(), pixels.getSizeZ() });
				tileSize = imageSize;
			} else {
				tileSize = new HashMap<>();
				logger.debug("Get image size and tile sizes...");
				int tileSizeX = rawPixStore.getTileSize()[0];
				int tileSizeY = rawPixStore.getTileSize()[1];
				int bytesWidth = rawPixStore.getByteWidth();

				for (int level = 0; level < this.nMipmapLevels; level++) {
					int[] sizes = new int[3];
                    rawPixStore.setResolutionLevel(this.nMipmapLevels - level - 1);
					sizes[0] = rawPixStore.getRowSize() / bytesWidth;
					sizes[1] = (int) ( (rawPixStore.getPlaneSize() / sizes[0]) / bytesWidth );
					sizes[2] = pixels.getSizeZ();
					int[] tileSizes = new int[2];
					tileSizes[0] = Math.min(tileSizeX, sizes[0]);
					tileSizes[1] = Math.min(tileSizeY, sizes[1]);
					imageSize.put(level, sizes);
					tileSize.put(level, tileSizes);
				}
			}
			// close the to free up resources
			rawPixStore.close();
		}

		this.nTimePoints = pixels.getSizeT();
		this.nChannels = pixels.getSizeC();

		logger.debug("SQL request completed!");
		// psizes are expressed in the unit given in the builder

		if (pixels.getPixelSizeX(OmeroHelper.getUnitsLengthFromString(unit)) == null || pixels.getPixelSizeY(
				OmeroHelper.getUnitsLengthFromString(unit)) == null)
		{
			this.psizeX = 1;
			this.psizeY = 1;
			logger.warn("The physical pixel size is not set for image " +
				datalocation + " ; a default value of 1 " + unit + " has been set");
		}
		else {
			this.psizeX = pixels.getPixelSizeX(OmeroHelper.getUnitsLengthFromString(unit)).getValue();
			this.psizeY = pixels.getPixelSizeY(OmeroHelper.getUnitsLengthFromString(unit)).getValue();
		}
		// to handle 2D images

		Length length = pixels.getPixelSizeZ(OmeroHelper.getUnitsLengthFromString(unit));
		if (length != null) {
			this.psizeZ = length.getValue();
		} else {
			this.psizeZ = 1;
		}

		this.pixelType = getNumericType(pixels);

		ImageData imageData = getImageData(imageID, gateway, securityContext);
		this.format = imageData.asImage().getFormat().getValue().getValue();
		String imageName = imageData.getName();

		if (!skipMeta) {


			List<ChannelData> channelMetadata = gateway.getFacility(MetadataFacility.class).getChannelData(securityContext, imageID);
			RenderingDef renderingDef = null;

			try {
				renderingDef = gateway.getRenderingSettingsService(securityContext).getRenderingSettings(pixelsID);
			} catch (Exception e) {
				logger.error("Couldn't get rendering definition.");
			}

			boolean isRGB = this.nChannels == 3 && this.pixelType instanceof UnsignedByteType; // Humhum bof! TODO

			// Miscroscope stage
			double stagePosX;
			double stagePosY;

			// --X and Y stage positions--
			logger.debug("Begin SQL request for OMERO image with ID : " + imageID);
			List<IObject> objectinfos = gateway.getQueryService(securityContext)
					.findAllByQuery("select info from PlaneInfo as info " +
							"join fetch info.deltaT as dt " +
							"join fetch info.exposureTime as et " + "where info.pixels.id=" + pixels
							.getId(), null);

			if (!objectinfos.isEmpty()) {

				// one plane per (c,z,t) combination: we assume that X and Y stage
				// positions are the same in all planes and therefore take the 1st plane
				PlaneInfo planeinfo = (PlaneInfo) (objectinfos.get(0));
				// Convert the offsets in the unit given in the builder

				if (planeinfo == null) {
					logger.warn("The planeinfo is not set for the image " +
							imageName + " ; plane position set at origin ");
					stagePosX = 0;
					stagePosY = 0;
				} else if (planeinfo.getPositionX() == null) {
					logger.warn("The planeinfo position is not set for the image " +
							imageName + " ; plane position set at origin ");
					stagePosX = 0;
					stagePosY = 0;
				} else  if (planeinfo.getPositionX().getUnit() == null) {
					logger.warn("The planeinfo position unit is not set for the image " +
							imageName + " ; plane position set at origin ");
					stagePosX = 0;
					stagePosY = 0;
				} else if (!planeinfo.getPositionX().getUnit().equals(UnitsLength.REFERENCEFRAME)) {
					stagePosX = new LengthI(planeinfo.getPositionX(), unit).getValue();
					stagePosY = new LengthI(planeinfo.getPositionY(), unit).getValue();
				} else {
					Length lengthPosX;
					Length lengthPosY;
					logger.warn("The pixel unit is not set for the image " +
							imageName + " ; a default unit " + unit + " has been set");
					Length l1 = planeinfo.getPositionX();
					Length l2 = planeinfo.getPositionY();
					l1.setUnit(OmeroHelper.getUnitsLengthFromString(unit));
					l2.setUnit(OmeroHelper.getUnitsLengthFromString(unit));
					lengthPosX = new LengthI(l1, unit);
					lengthPosY = new LengthI(l2, unit);
					stagePosX = lengthPosX.getValue();
					stagePosY = lengthPosY.getValue();
				}
			} else {
				stagePosX = 0;
				stagePosY = 0;
			}

			List<ChannelProperties> channelPropertiesList = getChannelProperties(channelMetadata, renderingDef, this.nChannels, this.pixelType, isRGB);

			this.meta = new OpenerMeta() {

				@Override
				public String getImageName() {
					return (imageName); // + "--OMERO ID:" + this.omeroImageID
				}

				@Override
				public AffineTransform3D getTransform() {
					AffineTransform3D transform = new AffineTransform3D();
					transform.identity();
					transform.scale(psizeX, psizeY, psizeZ);
					transform.translate(stagePosX, stagePosY, 0);// TODO : find Z ? getStagePosX(), getStagePosY(), 0});
					return transform;
				}

				@Override
				public List<Entity> getEntities(int iChannel) {
					ArrayList<Entity> entityList = new ArrayList<>();
					if (omeroImageID < Integer.MAX_VALUE) {
						entityList.add(new OmeroHostId((int) omeroImageID, host + "/" + omeroImageID));
					} else {
						logger.error("Can't index the omeroid with an int, taking the modulo, and hoping for no overlap");
						entityList.add(new OmeroHostId((int) (omeroImageID % Integer.MAX_VALUE), host + "/" + omeroImageID));
					}
					return entityList;
				}

				@Override
				public ChannelProperties getChannel(int iChannel) {
					return channelPropertiesList.get(iChannel);
				}
			};
		} else meta = null;
	}


	/**
	 * Build a channelProperties object for each image channel.
	 * @param channelMetadata
	 * @param rd
	 * @param nChannels
	 * @param pixType
	 * @param isRGB
	 * @return
	 * @throws BigResult
	 */
	static List<ChannelProperties> getChannelProperties(List<ChannelData> channelMetadata, RenderingDef rd, int nChannels, Type<? extends  NumericType<?>> pixType, Boolean isRGB) throws BigResult {
		List<ChannelProperties> channelPropertiesList = new ArrayList<>();
		for(int i = 0; i < nChannels; i++){
			channelPropertiesList.add(new ChannelProperties(i)
					.setNChannels(nChannels)
					.setChannelColor(rd)
					.setEmissionWavelength(channelMetadata.get(i))
					.setExcitationWavelength(channelMetadata.get(i))
					.setChannelName(channelMetadata.get(i))
					.setPixelType(pixType)
					.setRGB(isRGB)
					.setDynamicRange(rd)
			);

		}
		return channelPropertiesList;
	}

	/**
	 * @param imageID ID of the OMERO image to access
	 * @param gateway OMERO gateway
	 * @param ctx OMERO Security context
	 * @return OMERO raw pixel data
	 * @throws Exception
	 */
	public static PixelsData getPixelsDataFromOmeroID(long imageID,
		Gateway gateway, SecurityContext ctx) throws Exception
	{
		ImageData image = getImageData(imageID, gateway, ctx);
		return image.getDefaultPixels();
	}

	/**
	 *
	 * @param imageID ID of the OMERO image to access
	 * @param gateway OMERO gateway
	 * @param ctx OMERO Security context
	 * @return OMERIO raw image data
	 * @throws Exception
	 */
	public static ImageData getImageData(long imageID, Gateway gateway,
		SecurityContext ctx) throws Exception
	{
		BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
		return browse.getImage(ctx, imageID);
	}


	/**
	 * RawPixelStore supplier method for the RawPixelsStorePool.
	 */
	public RawPixelsStorePrx getNewStore() {
		try {
			RawPixelsStorePrx rawPixStore = this.gateway.getPixelsStore(this.securityContext);
			rawPixStore.setPixelsId(pixelsID, false);
			return rawPixStore;
		}
		catch (ServerError | DSOutOfServiceException serverError) {
			serverError.printStackTrace();
		}
		return null;
	}


	/**
	 *
	 * @param pixels : OMERO compatible pixel type
	 * @return BDV compatible pixel type
	 */
	private static Type<? extends  NumericType<?>> getNumericType(PixelsData pixels)  {
		switch (pixels.getPixelType()) {
			case FLOAT_TYPE:
				return new FloatType();
			case UINT16_TYPE:
				return new UnsignedShortType();
			case UINT8_TYPE:
				return new UnsignedByteType();
			case UINT32_TYPE:
				return new UnsignedIntType();
			default:
				throw new IllegalStateException("Unsupported pixel type : " + pixels
					.getPixelType());
		}
	}

	/**
	 *
	 * @return image dimensions
	 */
	public Dimensions getDimension(){
		long sX = imageSize.get(0)[0];
		long sY = imageSize.get(0)[1];
		long sZ = imageSize.get(0)[2];

		return getDimension(sX, sY, sZ);
	}

	/**
	 *
	 * @param sX
	 * @param sY
	 * @param sZ
	 * @return image dimensions
	 */
	public Dimensions getDimension(long sX, long sY, long sZ){
		// Always set 3d to allow for Big Stitcher compatibility
		int numDimensions = 3;

		long[] dims = new long[3];

		dims[0] = sX;
		dims[1] = sY;
		dims[2] = sZ;

		Dimensions dimensions = new Dimensions() {

			@Override
			public void dimensions(long[] dimensions) {
				dimensions[0] = dims[0];
				dimensions[1] = dims[1];
				dimensions[2] = dims[2];
			}

			@Override
			public long dimension(int d) {
				return dims[d];
			}

			@Override
			public int numDimensions() {
				return numDimensions;
			}
		};

		return dimensions;
	}

	/**
	 *
	 * @param t an instance of the pixel type
	 * @return volatile pixel type from t
	 */
	private static Volatile<?> getVolatileOf(NumericType<?> t) {
		if (t instanceof UnsignedShortType) return new VolatileUnsignedShortType();

		if (t instanceof IntType) return new VolatileIntType();

		if (t instanceof UnsignedByteType) return new VolatileUnsignedByteType();

		if (t instanceof FloatType) return new VolatileFloatType();

		if (t instanceof ARGBType) return new VolatileARGBType();

		System.err.println("Volatile type of pixel type "+t.getClass().getName()+" not found!");

		return null;
	}

	@Override
	public int getNumMipmapLevels() {
		return this.nMipmapLevels;
	}

	@Override
	public int getNTimePoints() {
		return this.nTimePoints;
	}

	@Override
	public ResourcePool<RawPixelsStorePrx> getPixelReader() {
		return pool;
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {
		int numDimensions = 3;

		double[] d = new double[3];
		d[0] = psizeX;
		d[1] = psizeY;
		d[2] = psizeZ;

		VoxelDimensions voxelDimensions;

		{
			assert numDimensions == 3;
			voxelDimensions = new VoxelDimensions() {

				final double[] dims = { d[0], d[1], d[2] };

				@Override
				public String unit() {
					return unit;
				}

				@Override
				public void dimensions(double[] doubles) {
					doubles[0] = dims[0];
					doubles[1] = dims[1];
					doubles[2] = dims[2];
				}

				@Override
				public double dimension(int i) {
					return dims[i];
				}

				@Override
				public int numDimensions() {
					return numDimensions;
				}
			};
		}
		return voxelDimensions;
	}

	@Override
	// https://forum.image.sc/t/omero-py-how-to-get-tiles-at-different-zoom-level-pyramidal-image/45643/11
	// OMERO always produce big-endian pixels
	public boolean isLittleEndian() {
		return false;
	}

	@Override
	public String getImageFormat() {
		return this.format;
	}

	@Override
	public OpenerSetupLoader<?, ?, ?> getSetupLoader(int channelIdx, int setupIdx, Supplier<VolatileGlobalCellCache> cacheSupplier) {
		return new OmeroSetupLoader(this,
				channelIdx, setupIdx, (NumericType) this.getPixelType(), getVolatileOf((NumericType) this.getPixelType()), cacheSupplier);
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
		return new int[] { this.getTileSizeX(
				this.nMipmapLevels - 1 - level), this.getTileSizeY(this.nMipmapLevels - 1 -
				level), 1 };
	}

	@Override
	public Dimensions[] getDimensions() {
		Dimensions[] dimensions = new Dimensions[this.nMipmapLevels];
		for (int level = 0; level < this.nMipmapLevels; level++) {
			dimensions[level] = getDimension(this.getSizeX(level), this
					.getSizeY(level), this.getSizeZ(level));
		}
		return dimensions;
	}

	@Override
	public int getNChannels() {
		return this.nChannels;
	}

	@Override
	public Type<? extends NumericType<?>> getPixelType() {
		return this.pixelType;
	}

	@Override
	public void close() {
		pool.shutDown(store -> {
			try {
				if (gateway.isConnected()) {
					store.close();
				}
			} catch (ServerError e) {
				e.printStackTrace();
			}
		});
	}

	public static class RawPixelsStorePool extends ResourcePool<RawPixelsStorePrx> {

		final Supplier<RawPixelsStorePrx> rpsSupplier;

		public RawPixelsStorePool(int size, Boolean dynamicCreation,
								  Supplier<RawPixelsStorePrx> rawPixelStoreSupplier)
		{
			super(size, dynamicCreation);
			this.rpsSupplier = rawPixelStoreSupplier;
		}

		@Override
		protected RawPixelsStorePrx createObject() {
			return rpsSupplier.get();
		}
	}

}
