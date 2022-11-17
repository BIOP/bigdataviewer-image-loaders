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

import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.*;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import ch.epfl.biop.bdv.img.bioformats.entity.ChannelName;
import ch.epfl.biop.bdv.img.qupath.entity.QuPathEntryEntity;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * QuPath Image Opener. This class builds a specific opener depending on the
 * image provider class that is used to convert QuPath data into BDV compatible
 * data There are some limitations: only bioformats image server, rotated image
 * server and omero-raw image server are supported ( among probably other
 * limitations ). Also, editing files in the QuPath project after it has been
 * converted to an xml bdv dataset is not guaranteed to work.
 *
 * @author Rémy Dornier, EPFL, BIOP, 2022
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 */
public class QuPathImageOpener<T> implements Opener<T> {

	protected static Logger logger = LoggerFactory.getLogger(
			QuPathImageOpener.class);
	private Opener<T> opener;
	private MinimalQuPathProject.ImageEntry image;
	private String unit;
	private URI qpProj;
	private int iSerie;


	public MinimalQuPathProject.ImageEntry getImage() {
		return this.image;
	}


	/**
	 * Void constructor
	 */
	public QuPathImageOpener(){
	}

	/**
	 * Create an OMERO or BioFormats opener depending on the providerClassName of QuPath
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
	 * @param gateway
	 * @param ctx
	 * @param imageID
	 * @param image
	 * @param qpPathProject
	 * @return
	 */
	public Opener<?> create(// opener core option
							Context context,
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
						 	boolean splitRGBChannels,
						 	Gateway gateway,
						 	SecurityContext ctx,
						 	long imageID,
						 	MinimalQuPathProject.ImageEntry image,
						 	URI qpPathProject,
							// Optimisation : reuse existing openers
							Map<String, Object> cachedObjects)
	{

		// get the rotation angle if the image has been loaded in qupath with the
		// rotation command
		if (image.serverBuilder.builderType.equals("rotated")) {
			double angleRotationZAxis = getAngleRotationZAxis(image);
		}

		this.image = image;
		this.unit = unit;
		this.qpProj = qpPathProject;
		this.iSerie = iSerie;

		if (image.serverBuilder.builderType.equals("uri")) {
			logger.debug("URI image server");

			try {

				logger.debug("provided class name : "+image.serverBuilder.providerClassName);
				// create openers
				if (image.serverBuilder.providerClassName.equals(
					"qupath.lib.images.servers.bioformats.BioFormatsServerBuilder"))
				{
					this.opener = (Opener<T>) new BioFormatsBdvOpener(
							context,
							dataLocation,
							iSerie,
							// Location of the image
							positionPreTransformMatrixArray,
							positionPostTransformMatrixArray,
							positionIsImageCenter,
							defaultSpaceUnit,
							defaultVoxelUnit,
							unit,
							// How to stream it
							poolSize,
							useDefaultXYBlockSize,
							cacheBlockSize,
							// Channel options
							splitRGBChannels,
							cachedObjects);

					logger.debug("BioFormats Opener for image " + this.image.imageName);
				}
				else {
					if (this.image.serverBuilder.providerClassName.equals(
						"qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder"))
					{
						this.opener = (Opener<T>) new OmeroBdvOpener(
								context,
								dataLocation,
							poolSize,
							unit,
							cachedObjects);

						logger.debug("OMERO-RAW Opener for image " + this.image.imageName);
					}
					else {
						logger.error("Unsupported " +
							this.image.serverBuilder.providerClassName +
							" provider Class Name");
						System.out.println("Unsupported " +
							this.image.serverBuilder.providerClassName +
							" provider Class Name");
						return this;
					}
				}
			}
			catch (Exception e) {
				logger.error("URI Syntax error " + e.getMessage());
				System.out.println("URI Syntax error " + e.getMessage());
				e.printStackTrace();
			}
		}
		else {
			logger.error("Unsupported " + image.serverBuilder.builderType +
				" server builder");
		}
		return this;
	}


	/**
	 * get the rotation angle of the image if the image was imported in qupath
	 * with a rotation
	 *
	 * @param image
	 * @return
	 */
	private double getAngleRotationZAxis(MinimalQuPathProject.ImageEntry image) {
		double angleRotationZAxis;

		// "ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
		String angleDegreesStr = image.serverBuilder.rotation.substring(7);

		logger.debug("Rotated image server (" + angleDegreesStr + ")");
		if (angleDegreesStr.equals("NONE")) {
			angleRotationZAxis = 0;
		}
		else {
			angleRotationZAxis = (Double.parseDouble(angleDegreesStr) / 180.0) *
					Math.PI;
		}
		MinimalQuPathProject.ServerBuilderMetadata metadata =
				image.serverBuilder.metadata; // To keep the metadata (pixel size for
		// instance)
		image.serverBuilder = image.serverBuilder.builder; // Skips the rotation
		image.serverBuilder.metadata = metadata;

		return angleRotationZAxis;
	}

	/**
	 * Scale the rootTransform of the basic opener (OMERO, BioFormats) with QuPath unit and pixel dimensions
	 * @param pixelCalibrations
	 * @param outputUnit
	 * @param rootTransform
	 * @param voxSizes
	 * @return
	 */
	private AffineTransform3D getTransform(MinimalQuPathProject.PixelCalibrations pixelCalibrations, String outputUnit,
												 AffineTransform3D rootTransform, VoxelDimensions voxSizes) {

		AffineTransform3D quPathRescaling = new AffineTransform3D();

		if (pixelCalibrations != null) {
			double scaleX = 1.0;
			double scaleY = 1.0;
			double scaleZ = 1.0;

			double voxSizeX = voxSizes.dimension(0);
			double voxSizeY = voxSizes.dimension(1);
			double voxSizeZ = voxSizes.dimension(2);

			// compute scaling factor
			if (pixelCalibrations.pixelWidth != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelWidth;
				Length voxLengthX = new Length(voxSizeX, BioFormatsTools.getUnitFromString(voxSizes.unit()));

				if (voxLengthX.value(UNITS.MICROMETER) != null) {
					logger.debug("xVox size = " + pc.value + " micrometer");
					scaleX = pc.value / voxLengthX.value(UNITS.MICROMETER).doubleValue();
				} else {
					Length defaultxPix = new Length(1, BioFormatsTools.getUnitFromString(outputUnit));
					scaleX = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
					logger.debug("rescaling x");
				}
			}
			if (pixelCalibrations.pixelHeight != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelHeight;
				Length voxLengthY = new Length(voxSizeY, BioFormatsTools.getUnitFromString(voxSizes.unit()));
				// if (pc.unit.equals("um")) {
				if (voxLengthY.value(UNITS.MICROMETER) != null) {
					logger.debug("yVox size = " + pc.value + " micrometer");
					scaleY = pc.value / voxLengthY.value(UNITS.MICROMETER).doubleValue();
				} else {
					Length defaultxPix = new Length(1, BioFormatsTools.getUnitFromString(outputUnit));
					scaleY = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
					logger.debug("rescaling y");
				}
			}
			if (pixelCalibrations.zSpacing != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.zSpacing;
				Length voxLengthZ = new Length(voxSizeZ, BioFormatsTools.getUnitFromString(voxSizes.unit()));
				// if (pc.unit.equals("um")) { problem with micrometer character
				if (voxLengthZ.value(UNITS.MICROMETER) != null) {
					logger.debug("zVox size = " + pc.value + " micrometer");
					scaleZ = pc.value / voxLengthZ.value(UNITS.MICROMETER).doubleValue();
				} else {
                   /* if ((voxLengthZ != null)) {
                    }
                    else {*/
					logger.warn("Null Z voxel size");
				}
			}

			logger.debug("ScaleX: " + scaleX + " scaleY:" + scaleY + " scaleZ:" + scaleZ);

			final double finalScalex = scaleX;
			final double finalScaley = scaleY;
			final double finalScalez = scaleZ;

			if ((Math.abs(finalScalex - 1.0) > 0.0001) || (Math.abs(
					finalScaley - 1.0) > 0.0001) || (Math.abs(finalScalez -
					1.0) > 0.0001)) {
				logger.debug("Perform QuPath rescaling");
				// create a new AffineTransform3D based on pixelCalibration
				quPathRescaling.scale(finalScalex, finalScaley, finalScalez);
				// scale the root transform
				double oX = rootTransform.get(0, 3);
				double oY = rootTransform.get(1, 3);
				double oZ = rootTransform.get(2, 3);
				rootTransform.preConcatenate(quPathRescaling);
				rootTransform.set(oX, 0, 3);
				rootTransform.set(oY, 1, 3);
				rootTransform.set(oZ, 2, 3);
			}

		}

		return rootTransform;
	}

	/**
	 * Convert the string unit from QuPath metadata into Unit class readable by
	 * the opener
	 * 
	 * @param unitString
	 * @return
	 */
	private Unit<Length> convertStringToUnit(String unitString) {
		switch (unitString) {
			case "µm":
				return UNITS.MICROMETER;
			case "mm":
				return UNITS.MILLIMETER;
			case "cm":
				return UNITS.CENTIMETER;
			case "px":
				return UNITS.PIXEL;
			default:
				return UNITS.REFERENCEFRAME;
		}
	}



	/**
	 * Use QuPath pixelCalibration to retrieve the size of an image voxel in the specified unit.
	 * @param pixelCalibrations
	 * @param unit
	 * @return
	 */
	public VoxelDimensions getVoxelDimensions(MinimalQuPathProject.PixelCalibrations pixelCalibrations, String unit)
	{
		// Always 3 to allow for big stitcher compatibility
		int numDimensions = 3;

		// get voxel size with the original unit
		Length[] voxSize = new Length[]{
				new Length(pixelCalibrations.pixelWidth.value, convertStringToUnit(pixelCalibrations.pixelWidth.unit)),
				new Length(pixelCalibrations.pixelHeight.value, convertStringToUnit(pixelCalibrations.pixelHeight.unit)),
				new Length(pixelCalibrations.zSpacing.value, convertStringToUnit(pixelCalibrations.zSpacing.unit))
		};
		double[] d = new double[3];
		Unit<Length> u = BioFormatsTools.getUnitFromString(unit);
		Length voxSizeReferenceFrameLength = new Length(1, UNITS.MICROMETER);

		// fill the array with the dimension value converted into the specified unit u
		for (int iDimension = 0; iDimension < 3; iDimension++) { // X:0; Y:1; Z:2
			if ((voxSize[iDimension].unit() != null) && (voxSize[iDimension].unit()
					.isConvertible(u)))
			{
				d[iDimension] = voxSize[iDimension].value(u).doubleValue();
			}
			else if (voxSize[iDimension].unit().getSymbol().equals(
					"reference frame"))
			{
				Length l = new Length(voxSize[iDimension].value().doubleValue() *
						voxSizeReferenceFrameLength.value().doubleValue(),
						voxSizeReferenceFrameLength.unit());
				d[iDimension] = l.value(u).doubleValue();
			}
			else {
				d[iDimension] = 1;
			}
		}

		// create the Voxel dimension object
		VoxelDimensions voxelDimensions;

		{
			assert numDimensions == 3;
			voxelDimensions = new VoxelDimensions() {

				final Unit<Length> targetUnit = u;

				final double[] dims = { d[0], d[1], d[2] };

				@Override
				public String unit() {
					return targetUnit.getSymbol();
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

	// OVERRIDDEN METHODS
	@Override
	public int[] getCellDimensions(int level) {
		return this.opener.getCellDimensions(level);
	}

	@Override
	public ChannelProperties getChannel(int iChannel) {
		if (this.image.serverBuilder != null &&
				this.image.serverBuilder.metadata != null &&
				this.image.serverBuilder.metadata.channels != null){

			if (this.image.serverBuilder.metadata.isRGB) {
				if (opener.getPixelType() instanceof ARGBType) {
					// No split RGB
					return this.opener.getChannel(iChannel).setChannelName("RGB");
				} else {
					MinimalQuPathProject.ChannelInfo channel = this.image.serverBuilder.metadata.channels.get(iChannel);
					return this.opener.getChannel(iChannel).setChannelName(channel.name).setChannelColor(channel.color);
				}
			}

			MinimalQuPathProject.ChannelInfo channel = this.image.serverBuilder.metadata.channels.get(iChannel);
			return this.opener.getChannel(iChannel).setChannelName(channel.name).setChannelColor(channel.color);
		} else return this.opener.getChannel(iChannel);
	}

	@Override
	public Dimensions[] getDimensions() {
		return this.opener.getDimensions();
	}

	@Override
	public List<Entity> getEntities(int iChannel) {
		List<Entity> oldEntities = this.opener.getEntities(iChannel);
		List<Entity> newEntities = oldEntities.stream().filter(e->!(e instanceof ChannelName)).collect(Collectors.toList());
		newEntities.add(new ChannelName(0, getChannel(iChannel).getChannelName()));

		// create a QuPath Entry
		QuPathEntryEntity qpentry = new QuPathEntryEntity(this.image.entryID);
		qpentry.setName(QuPathEntryEntity.getNameFromURIAndSerie(this.image.serverBuilder.uri, this.iSerie));
		qpentry.setQuPathProjectionLocation(Paths.get(this.qpProj).toString());

		newEntities.add(qpentry);
		newEntities.forEach(e->System.out.println(e));
		return newEntities;

	}

	@Override
	public String getImageName() {
		return this.image.imageName;
	}

	@Override
	public int getNChannels() {
		/*if(this.image.serverBuilder != null &&
				this.image.serverBuilder.metadata != null &&
				this.image.serverBuilder.metadata.channels != null){
			return this.image.serverBuilder.metadata.channels.size();
		}
		else */
		{
			return this.opener.getNChannels();
		}
	}

	@Override
	public int getNTimePoints() {
		return this.opener.getNTimePoints();
	}

	@Override
	public int getNumMipmapLevels() {
		return this.opener.getNumMipmapLevels();
	}

	@Override
	public ResourcePool<T> getPixelReader() {
		return opener.getPixelReader();
	}

	@Override
	public Type<? extends NumericType> getPixelType() {
		return this.opener.getPixelType();
	}

	@Override
	public AffineTransform3D getTransform() {
		if(this.image.serverBuilder != null &&
				this.image.serverBuilder.metadata != null &&
				this.image.serverBuilder.metadata.pixelCalibration != null){
			return getTransform(this.image.serverBuilder.metadata.pixelCalibration,
					this.unit, opener.getTransform(), getVoxelDimensions());
		}else
			return this.opener.getTransform();
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {
		if(this.image.serverBuilder != null &&
		this.image.serverBuilder.metadata != null &&
		this.image.serverBuilder.metadata.pixelCalibration != null){
			return getVoxelDimensions(this.image.serverBuilder.metadata.pixelCalibration,this.unit);
		}
		else return this.opener.getVoxelDimensions();
	}

	@Override
	public boolean isLittleEndian() {
		return this.opener.isLittleEndian();
	}

	@Override
	public BiopSetupLoader<?, ?, ?> getSetupLoader(int channelIdx, int setupIdx, Supplier<VolatileGlobalCellCache> cacheSupplier) {
		return this.opener.getSetupLoader(channelIdx, setupIdx, cacheSupplier);
	}

	@Override
	public void close() throws IOException {
		this.opener.close();
	}
}
