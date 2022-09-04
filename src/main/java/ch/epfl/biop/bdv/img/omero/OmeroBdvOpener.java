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

package ch.epfl.biop.bdv.img.omero;

import bdv.cache.SharedQueue;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.units.UNITS;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static omero.gateway.model.PixelsData.FLOAT_TYPE;
import static omero.gateway.model.PixelsData.UINT16_TYPE;
import static omero.gateway.model.PixelsData.UINT32_TYPE;
import static omero.gateway.model.PixelsData.UINT8_TYPE;

/**
 * Contains parameters that explain how to open all channel sources from an
 * Omero Image TODO: make proper builder pattern (2 classes: internal builder
 * class in omerosourceopener)
 */
public class OmeroBdvOpener {

	protected static final Logger logger = LoggerFactory.getLogger(
		OmeroBdvOpener.class);

	// All serializable fields (fields needed to create the omeroSourceOpener)
	public String dataLocation = null; // URL or File
	public boolean useOmeroXYBlockSize = true; // Block size : use the one defined
																							// by Omero
	long omeroImageID;
	public String host;
	// Channels options
	boolean splitRGBChannels = false;
	// Unit used for display
	public UnitsLength u;
	// Size of the blocks
	// public FinalInterval cacheBlockSize = new FinalInterval(new long[]{0, 0,
	// 0}, new long[]{512, 512, 1}); // needs a default size for z
	// Bioformats location fix
	public double[] positionPreTransformMatrixArray;
	public double[] positionPostTransformMatrixArray;
	public ome.units.quantity.Length positionReferenceFrameLength;
	public boolean positionIgnoreBioFormatsMetaData = false;
	// Bioformats voxsize fix
	public boolean voxSizeIgnoreBioFormatsMetaData = false;
	public ome.units.quantity.Length voxSizeReferenceFrameLength;
	public int numFetcherThreads = 2;
	public int numPriorities = 4;

	// All non-serializable fields
	transient SharedQueue cc;
	transient Gateway gateway;
	transient SecurityContext securityContext;
	transient RawPixelsStorePool pool = new RawPixelsStorePool(10, true,
		this::getNewStore);
	transient int sizeT;
	transient int sizeC;
	transient int nLevels;
	transient double psizeX;
	transient double psizeY;
	transient double psizeZ;
	transient double stagePosX;
	transient double stagePosY;
	transient Map<Integer, int[]> imageSize;
	transient Map<Integer, int[]> tileSize;
	transient long pixelsID;
	transient String imageName;
	transient List<ChannelData> channelMetadata;
	transient boolean displayInSpace;
	transient RenderingDef renderingDef;
	// protected transient Consumer<IFormatReader> readerModifier = (e) -> {};

	// All get methods
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

	public int getSizeT() {
		return this.sizeT;
	}

	public int getSizeC() {
		return this.sizeC;
	}

	public int getNLevels() {
		return this.nLevels;
	}

	public long getPixelsID() {
		return this.pixelsID;
	}

	public double getPixelSizeX() {
		return this.psizeX;
	}

	public double getPixelSizeY() {
		return this.psizeY;
	}

	public double getPixelSizeZ() {
		return this.psizeZ;
	}

	public double getStagePosX() {
		return this.stagePosX;
	}

	public double getStagePosY() {
		return this.stagePosY;
	}

	public String getDataLocation() {
		return dataLocation;
	}

	public String getHost() {
		return host;
	}

	public Gateway getGateway() {
		return gateway;
	}

	public Long getOmeroId() {
		return omeroImageID;
	}

	public SecurityContext getSecurityContext() {
		return securityContext;
	}

	public List<ChannelData> getChannelMetadata() {
		return channelMetadata;
	}

	public RenderingDef getRenderingDef() {
		return renderingDef;
	}

	public int getNumFetcherThreads() {
		return numFetcherThreads;
	}

	public int getNumPriorities() {
		return numPriorities;
	}

	public OmeroBdvOpener positionReferenceFrameLength(
		ome.units.quantity.Length l)
	{
		this.positionReferenceFrameLength = l;
		return this;
	}

	public OmeroBdvOpener voxSizeReferenceFrameLength(
		ome.units.quantity.Length l)
	{
		this.voxSizeReferenceFrameLength = l;
		return this;
	}

	public OmeroBdvOpener useCacheBlockSizeFromOmero(boolean flag) {
		useOmeroXYBlockSize = flag;
		return this;
	}

	public OmeroBdvOpener location(String location) {
		this.dataLocation = location;
		return this;
	}

	// define image ID
	public OmeroBdvOpener imageID(long imageID) {
		this.omeroImageID = imageID;
		return this;
	}

	public OmeroBdvOpener host(String host) {
		this.host = host;
		return this;
	}

	public OmeroBdvOpener displayInSpace(boolean displayInSpace) {
		this.displayInSpace = displayInSpace;
		return this;
	}

	public OmeroBdvOpener splitRGBChannels() {
		splitRGBChannels = true;
		return this;
	}

	// define unit
	public OmeroBdvOpener unit(UnitsLength u) {
		this.u = u;
		return this;
	}

	public OmeroBdvOpener millimeter() {
		this.u = UnitsLength.MILLIMETER;
		return this;
	}

	public OmeroBdvOpener micrometer() {
		this.u = UnitsLength.MICROMETER;
		return this;
	}

	public OmeroBdvOpener nanometer() {
		this.u = UnitsLength.NANOMETER;
		return this;
	}

	// define gateway
	public OmeroBdvOpener gateway(Gateway gateway) {
		this.gateway = gateway;
		return this;
	}

	// define security context
	public OmeroBdvOpener securityContext(SecurityContext ctx) {
		this.securityContext = ctx;
		return this;
	}

	// define num fetcher threads
	public OmeroBdvOpener numFetcherThreads(int numFetcherThreads) {
		this.numFetcherThreads = numFetcherThreads;
		return this;
	}

	// define num fetcher threads
	public OmeroBdvOpener numPriorities(int numPriorities) {
		this.numPriorities = numPriorities;
		return this;
	}

	// define size fields based on omero image ID, gateway and security context

	/**
	 * Builder pattern: fills all the omerosourceopener fields that relates to the
	 * image to open (i.e image size for all resolution levels..)
	 * 
	 * @return
	 * @throws Exception
	 */
	public OmeroBdvOpener create() throws Exception {
		// TODO move it to omerosourceopener
		System.out.println("Load PixelsData...");
		System.out.println("omeroID : " + omeroImageID);
		PixelsData pixels = OmeroTools.getPixelsDataFromOmeroID(omeroImageID,
			gateway, securityContext);
		System.out.println("PixelsData loaded!");
		System.out.println("Load RawPixelsStore...");
		RawPixelsStorePrx rawPixStore = gateway.getPixelsStore(securityContext);
		System.out.println("RawPixelsStore loaded!");
		this.cc = new SharedQueue(numFetcherThreads, numPriorities);
		this.pixelsID = pixels.getId();
		rawPixStore.setPixelsId(this.pixelsID, false);
		this.nLevels = rawPixStore.getResolutionLevels();
		this.imageSize = new HashMap<>();
		this.tileSize = new HashMap<>();
		this.imageName = getImageData(omeroImageID, gateway, securityContext)
			.getName();
		this.channelMetadata = gateway.getFacility(MetadataFacility.class)
			.getChannelData(securityContext, omeroImageID);
		this.renderingDef = gateway.getRenderingSettingsService(securityContext)
			.getRenderingSettings(pixelsID);

		// Optimize time if there is only one resolution level because
		// getResolutionDescriptions() is time-consuming // TODO : WHAT ??
		if (rawPixStore.getResolutionLevels() == 1) {
			imageSize.put(0, new int[] { pixels.getSizeX(), pixels.getSizeY(), pixels
				.getSizeZ() });
			tileSize = imageSize;
		}
		else {
			System.out.println("Get image size and tile sizes...");
			Instant start = Instant.now();
			ResolutionDescription[] resDesc = rawPixStore.getResolutionDescriptions();
			Instant finish = Instant.now();
			System.out.println("Done! Time elapsed : " + Duration.between(start,
				finish));
			int tileSizeX = rawPixStore.getTileSize()[0];
			int tileSizeY = rawPixStore.getTileSize()[1];

			for (int level = 0; level < this.nLevels; level++) {
				int[] sizes = new int[3];
				sizes[0] = resDesc[level].sizeX;
				sizes[1] = resDesc[level].sizeY;
				sizes[2] = pixels.getSizeZ();
				int[] tileSizes = new int[2];
				tileSizes[0] = Math.min(tileSizeX, resDesc[rawPixStore
					.getResolutionLevels() - 1].sizeX);
				tileSizes[1] = Math.min(tileSizeY, resDesc[rawPixStore
					.getResolutionLevels() - 1].sizeY);
				imageSize.put(level, sizes);
				tileSize.put(level, tileSizes);
			}
		}

		this.sizeT = pixels.getSizeT();
		this.sizeC = pixels.getSizeC();

		// --X and Y stage positions--
		System.out.println("Begin SQL request for OMERO image with ID : " +
			this.omeroImageID);
		List<IObject> objectinfos = gateway.getQueryService(securityContext)
			.findAllByQuery("select info from PlaneInfo as info " +
				"join fetch info.deltaT as dt " +
				"join fetch info.exposureTime as et " + "where info.pixels.id=" + pixels
					.getId(), null);
		// System.out.println("objectinfos.size() " + objectinfos.size());
		if (objectinfos.size() != 0) {
			// one plane per (c,z,t) combination: we assume that X and Y stage
			// positions are the same in all planes and therefore take the 1st plane
			PlaneInfo planeinfo = (PlaneInfo) (objectinfos.get(0));
			// Convert the offsets in the unit given in the builder
			// System.out.println("Unit u " + this.u);
			// System.out.println("planeinfo.getPositionX() :
			// "+planeinfo.getPositionX());
			Length lengthPosX;
			Length lengthPosY;
			if ((planeinfo == null)||(planeinfo.getPositionX()==null)||planeinfo.getPositionX().getUnit()==null) {
				logger.warn("The pixel unit is not set for the image " +
						this.imageName + " ; a default unit " + this.u + " has been set");
				lengthPosX = new LengthI(0, UNITS.MICROMETER);
				lengthPosY = new LengthI(0, UNITS.MICROMETER);
			} else {
				if (!planeinfo.getPositionX().getUnit().equals(
						UnitsLength.REFERENCEFRAME)) {
					lengthPosX = new LengthI(planeinfo.getPositionX(), this.u);
					lengthPosY = new LengthI(planeinfo.getPositionY(), this.u);
				} else {
					logger.warn("The pixel unit is not set for the image " +
							this.imageName + " ; a default unit " + this.u + " has been set");
					Length l1 = planeinfo.getPositionX();
					Length l2 = planeinfo.getPositionY();
					l1.setUnit(this.u);
					l2.setUnit(this.u);
					lengthPosX = new LengthI(l1, this.u);
					lengthPosY = new LengthI(l2, this.u);
				}
			}

			this.stagePosX = lengthPosX.getValue();
			this.stagePosY = lengthPosY.getValue();
		}
		else {
			this.stagePosX = 0;
			this.stagePosY = 0;
		}
		System.out.println("SQL request completed!");
		// psizes are expressed in the unit given in the builder
		this.psizeX = 1;
		this.psizeY = 1;
		if (pixels.getPixelSizeX(this.u) == null || pixels.getPixelSizeY(
			this.u) == null)
		{
			logger.warn("The physical pixel size is not set for image " +
				this.imageName + " ; a default value of 1 " + this.u + " has been set");
		}
		else {
			this.psizeX = pixels.getPixelSizeX(this.u).getValue();
			this.psizeY = pixels.getPixelSizeY(this.u).getValue();
		}
		// to handle 2D images
		this.psizeZ = 1;
		Length length = pixels.getPixelSizeZ(this.u);
		if (length != null) {
			this.psizeZ = length.getValue();
		}

		// must close the rawPixStore to free up resources
		rawPixStore.close();
		return this;
	}

	// All space transformation methods
	public OmeroBdvOpener flipPositionXYZ() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(-1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public OmeroBdvOpener flipPositionX() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(-1, 1, 1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public OmeroBdvOpener flipPositionY() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(1, -1, 1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public OmeroBdvOpener flipPositionZ() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(1, 1, -1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public static PixelsData getPixelsDataFromOmeroID(long imageID,
		Gateway gateway, SecurityContext ctx) throws Exception
	{
		ImageData image = getImageData(imageID, gateway, ctx);
		return image.getDefaultPixels();

	}

	public static ImageData getImageData(long imageID, Gateway gateway,
		SecurityContext ctx) throws Exception
	{
		BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
		ImageData image = browse.getImage(ctx, imageID);
		return image;
	}

	public ARGBType getChannelColor(int c) throws Exception {

		ChannelBinding cb = renderingDef.getChannelBinding(c);

		return new ARGBType(ARGBType.rgba(cb.getRed().getValue(), cb.getGreen()
			.getValue(), cb.getBlue().getValue(), cb.getAlpha().getValue()));
	}

	/**
	 * RawPixelStore supplier method for the RawPixelsStorePool.
	 */
	public RawPixelsStorePrx getNewStore() {
		try {
			RawPixelsStorePrx rawPixStore = gateway.getPixelsStore(securityContext);
			rawPixStore.setPixelsId(getPixelsID(), false);
			return rawPixStore;
		}
		catch (ServerError | DSOutOfServiceException serverError) {
			serverError.printStackTrace();
		}
		return null;
	}

	public AffineTransform3D getSourceTransform(int level) {
		AffineTransform3D transform = new AffineTransform3D();
		transform.identity();
		transform.scale(getPixelSizeX() * (double) imageSize.get(0)[0] /
			(double) imageSize.get(level)[0], getPixelSizeY() * (double) imageSize
				.get(0)[1] / (double) imageSize.get(level)[1], getPixelSizeZ() *
					(double) imageSize.get(0)[2] / (double) imageSize.get(level)[2]);
		transform.translate(new double[] { getStagePosX(), getStagePosY(), 0 });
		return transform;
	}

	public NumericType getNumericType(int channel) throws Exception {
		PixelsData pixels = getPixelsDataFromOmeroID(omeroImageID, gateway,
			securityContext);
		// TODO : get pixel type as a field in omerosourceopener
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

	public String getImageName() {
		return (this.imageName + "--OMERO ID:" + this.omeroImageID);
	}

	public static OmeroBdvOpener getOpener() {
		OmeroBdvOpener opener = new OmeroBdvOpener().positionReferenceFrameLength(
			new ome.units.quantity.Length(1, UNITS.MICROMETER)) // Compulsory
			.voxSizeReferenceFrameLength(new ome.units.quantity.Length(1,
				UNITS.MICROMETER)).millimeter().useCacheBlockSizeFromOmero(true);
		return opener;
	}

	public Dimensions getDimensions() {
		// Always set 3d to allow for Big Stitcher compatibility
		int numDimensions = 3;

		int sX = imageSize.get(0)[0];
		int sY = imageSize.get(0)[1];
		int sZ = imageSize.get(0)[2];

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

	public VoxelDimensions getVoxelDimensions() {
		// Always 3 to allow for big stitcher compatibility
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
					return u.toString();
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

	public OmeroBdvOpener ignoreMetadata() {
		this.positionIgnoreBioFormatsMetaData = true;
		this.voxSizeIgnoreBioFormatsMetaData = true;
		return this;
	}

	public OmeroBdvOpener setCache(SharedQueue cc) {
		this.cc = cc;
		return this;
	}

}
